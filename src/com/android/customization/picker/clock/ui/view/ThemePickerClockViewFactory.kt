/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.customization.picker.clock.ui.view

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.lifecycle.LifecycleOwner
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.plugins.clocks.ClockController
import com.android.systemui.plugins.clocks.WeatherData
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.util.ScreenSizeCalculator
import com.android.wallpaper.util.TimeUtils.TimeTicker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Provide reusable clock view and related util functions.
 *
 * @property screenSize The Activity or Fragment's window size.
 */
class ThemePickerClockViewFactory
@Inject
constructor(
    activity: Activity,
    private val wallpaperManager: WallpaperManager,
    private val registry: ClockRegistry,
) : ClockViewFactory {
    private val appContext = activity.applicationContext
    private val resources = appContext.resources
    private val screenSize =
        ScreenSizeCalculator.getInstance().getScreenSize(activity.windowManager.defaultDisplay)
    private val timeTickListeners: ConcurrentHashMap<Int, TimeTicker> = ConcurrentHashMap()
    private val clockControllers: ConcurrentHashMap<String, ClockController> = ConcurrentHashMap()
    private val smallClockFrames: HashMap<String, FrameLayout> = HashMap()

    override fun getController(clockId: String): ClockController {
        return clockControllers[clockId]
            ?: initClockController(clockId).also { clockControllers[clockId] = it }
    }

    /**
     * Reset the large view to its initial state when getting the view. This is because some view
     * configs, e.g. animation state, might change during the reuse of the clock view in the app.
     */
    override fun getLargeView(clockId: String): View {
        return getController(clockId).largeClock.let {
            it.animations.onPickerCarouselSwiping(1F)
            it.view
        }
    }

    /**
     * Reset the small view to its initial state when getting the view. This is because some view
     * configs, e.g. translation X, might change during the reuse of the clock view in the app.
     */
    override fun getSmallView(clockId: String): View {
        val smallClockFrame =
            smallClockFrames[clockId]
                ?: createSmallClockFrame().also {
                    it.addView(getController(clockId).smallClock.view)
                    smallClockFrames[clockId] = it
                }
        smallClockFrame.translationX = 0F
        smallClockFrame.translationY = 0F
        return smallClockFrame
    }

    /** Enables or disables the reactive swipe interaction */
    override fun setReactiveTouchInteractionEnabled(clockId: String, enable: Boolean) {
        check(BaseFlags.get().isClockReactiveVariantsEnabled()) {
            "isClockReactiveVariantsEnabled is disabled"
        }
        getController(clockId).events.isReactiveTouchInteractionEnabled = enable
    }

    private fun createSmallClockFrame(): FrameLayout {
        val smallClockFrame = FrameLayout(appContext)
        val layoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(
                    com.android.systemui.customization.R.dimen.small_clock_height
                ),
            )
        layoutParams.topMargin = getSmallClockTopMargin()
        layoutParams.marginStart = getSmallClockStartPadding()
        smallClockFrame.layoutParams = layoutParams
        smallClockFrame.clipChildren = false
        return smallClockFrame
    }

    private fun getSmallClockTopMargin() =
        getStatusBarHeight(appContext) +
            appContext.resources.getDimensionPixelSize(
                com.android.systemui.customization.R.dimen.small_clock_padding_top
            )

    private fun getSmallClockStartPadding() =
        appContext.resources.getDimensionPixelSize(
            com.android.systemui.customization.R.dimen.clock_padding_start
        )

    override fun updateColorForAllClocks(@ColorInt seedColor: Int?) {
        clockControllers.values.forEach { it.events.onSeedColorChanged(seedColor = seedColor) }
    }

    override fun updateColor(clockId: String, @ColorInt seedColor: Int?) {
        getController(clockId).events.onSeedColorChanged(seedColor)
    }

    override fun updateRegionDarkness() {
        val isRegionDark = isLockscreenWallpaperDark()
        clockControllers.values.forEach {
            it.largeClock.events.onRegionDarknessChanged(isRegionDark)
            it.smallClock.events.onRegionDarknessChanged(isRegionDark)
        }
    }

    private fun isLockscreenWallpaperDark(): Boolean {
        val colors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_LOCK)
        return (colors?.colorHints?.and(WallpaperColors.HINT_SUPPORTS_DARK_TEXT)) == 0
    }

    override fun updateTimeFormat(clockId: String) {
        getController(clockId)
            .events
            .onTimeFormatChanged(android.text.format.DateFormat.is24HourFormat(appContext))
    }

    override fun registerTimeTicker(owner: LifecycleOwner) {
        val hashCode = owner.hashCode()
        if (timeTickListeners.keys.contains(hashCode)) {
            return
        }

        timeTickListeners[hashCode] = TimeTicker.registerNewReceiver(appContext) { onTimeTick() }
    }

    override fun onDestroy() {
        timeTickListeners.forEach { (_, timeTicker) -> appContext.unregisterReceiver(timeTicker) }
        timeTickListeners.clear()
        clockControllers.clear()
        smallClockFrames.clear()
    }

    private fun onTimeTick() {
        clockControllers.values.forEach {
            it.largeClock.events.onTimeTick()
            it.smallClock.events.onTimeTick()
        }
    }

    override fun unregisterTimeTicker(owner: LifecycleOwner) {
        val hashCode = owner.hashCode()
        timeTickListeners[hashCode]?.let {
            appContext.unregisterReceiver(it)
            timeTickListeners.remove(hashCode)
        }
    }

    private fun initClockController(clockId: String): ClockController {
        val controller =
            registry.createExampleClock(clockId).also { it?.initialize(resources, 0f, 0f) }
        checkNotNull(controller)

        val isWallpaperDark = isLockscreenWallpaperDark()
        // Initialize large clock
        controller.largeClock.events.onRegionDarknessChanged(isWallpaperDark)
        controller.largeClock.events.onFontSettingChanged(
            resources
                .getDimensionPixelSize(
                    com.android.systemui.customization.R.dimen.large_clock_text_size
                )
                .toFloat()
        )
        controller.largeClock.events.onTargetRegionChanged(getLargeClockRegion())

        // Initialize small clock
        controller.smallClock.events.onRegionDarknessChanged(isWallpaperDark)
        controller.smallClock.events.onFontSettingChanged(
            resources
                .getDimensionPixelSize(
                    com.android.systemui.customization.R.dimen.small_clock_text_size
                )
                .toFloat()
        )
        controller.smallClock.events.onTargetRegionChanged(getSmallClockRegion())
        controller.events.onWeatherDataChanged(WeatherData.getPlaceholderWeatherData())
        return controller
    }

    /**
     * Simulate the function of getLargeClockRegion in KeyguardClockSwitch so that we can get a
     * proper region corresponding to lock screen in picker and for onTargetRegionChanged to scale
     * and position the clock view
     */
    private fun getLargeClockRegion(): Rect {
        val largeClockTopMargin =
            resources.getDimensionPixelSize(
                com.android.systemui.customization.R.dimen.keyguard_large_clock_top_margin
            )
        val targetHeight =
            resources.getDimensionPixelSize(
                com.android.systemui.customization.R.dimen.large_clock_text_size
            ) * 2
        val top = (screenSize.y / 2 - targetHeight / 2 + largeClockTopMargin / 2)
        return Rect(0, top, screenSize.x, (top + targetHeight))
    }

    /**
     * Simulate the function of getSmallClockRegion in KeyguardClockSwitch so that we can get a
     * proper region corresponding to lock screen in picker and for onTargetRegionChanged to scale
     * and position the clock view
     */
    private fun getSmallClockRegion(): Rect {
        val topMargin = getSmallClockTopMargin()
        val targetHeight =
            resources.getDimensionPixelSize(
                com.android.systemui.customization.R.dimen.small_clock_height
            )
        return Rect(getSmallClockStartPadding(), topMargin, screenSize.x, topMargin + targetHeight)
    }

    companion object {
        private fun getStatusBarHeight(context: Context): Int {
            val display = context.displayNoVerify
            if (display != null) {
                return SystemBarUtils.getStatusBarHeight(context.resources, display.cutout)
            }

            var result = 0
            val resourceId: Int =
                context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = context.resources.getDimensionPixelSize(resourceId)
            }
            return result
        }
    }
}
