/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.module

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.customization.model.color.ThemedWallpaperColorResources
import com.android.customization.model.color.WallpaperColorResources
import com.android.customization.model.grid.GridOptionsManager
import com.android.customization.model.mode.DarkModeSnapshotRestorer
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.customization.model.themedicon.ThemedIconSwitchProvider
import com.android.customization.model.themedicon.data.repository.ThemeIconRepository
import com.android.customization.model.themedicon.domain.interactor.ThemedIconInteractor
import com.android.customization.model.themedicon.domain.interactor.ThemedIconSnapshotRestorer
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.domain.interactor.ClockPickerSnapshotRestorer
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.view.ThemePickerClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerSnapshotRestorer
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.grid.data.repository.GridRepositoryImpl
import com.android.customization.picker.grid.domain.interactor.GridInteractor
import com.android.customization.picker.grid.domain.interactor.GridSnapshotRestorer
import com.android.customization.picker.grid.ui.viewmodel.GridScreenViewModel
import com.android.customization.picker.notifications.domain.interactor.NotificationsSnapshotRestorer
import com.android.customization.picker.notifications.ui.viewmodel.NotificationSectionViewModel
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordanceSnapshotRestorer
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.customization.picker.settings.ui.viewmodel.ColorContrastSectionViewModel
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.FragmentFactory
import com.android.wallpaper.module.WallpaperPicker2Injector
import com.android.wallpaper.picker.CustomizationPickerActivity
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Singleton
open class ThemePickerInjector
@Inject
constructor(
    @MainDispatcher private val mainScope: CoroutineScope,
    @BackgroundDispatcher private val bgScope: CoroutineScope,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) : WallpaperPicker2Injector(mainScope), CustomizationInjector {
    private var customizationSections: CustomizationSections? = null
    private var keyguardQuickAffordancePickerViewModelFactory:
        KeyguardQuickAffordancePickerViewModel.Factory? =
        null
    private var fragmentFactory: FragmentFactory? = null
    private var notificationsSnapshotRestorer: NotificationsSnapshotRestorer? = null
    private var clockCarouselViewModelFactory: ClockCarouselViewModel.Factory? = null
    private var clockViewFactory: ClockViewFactory? = null
    private var notificationSettingsInteractor: NotificationSettingsInteractor? = null
    private var notificationSectionViewModelFactory: NotificationSectionViewModel.Factory? = null
    private var colorPickerViewModelFactory: ColorPickerViewModel.Factory? = null
    private var colorCustomizationManager: ColorCustomizationManager? = null
    private var darkModeSnapshotRestorer: DarkModeSnapshotRestorer? = null
    private var themedIconSnapshotRestorer: ThemedIconSnapshotRestorer? = null
    private var themedIconInteractor: ThemedIconInteractor? = null
    private var clockSettingsViewModelFactory: ClockSettingsViewModel.Factory? = null
    private var gridInteractor: GridInteractor? = null
    private var gridSnapshotRestorer: GridSnapshotRestorer? = null
    private var gridScreenViewModelFactory: GridScreenViewModel.Factory? = null

    // Injected objects, sorted by type
    @Inject
    lateinit var colorContrastSectionViewModelFactory: Lazy<ColorContrastSectionViewModel.Factory>
    @Inject
    lateinit var keyguardQuickAffordancePickerInteractor:
        Lazy<KeyguardQuickAffordancePickerInteractor>
    @Inject
    lateinit var keyguardQuickAffordanceSnapshotRestorer:
        Lazy<KeyguardQuickAffordanceSnapshotRestorer>
    @Inject lateinit var themesUserEventLogger: Lazy<ThemesUserEventLogger>
    @Inject lateinit var colorPickerInteractor: Lazy<ColorPickerInteractor>
    @Inject lateinit var colorPickerSnapshotRestorer: Lazy<ColorPickerSnapshotRestorer>
    @Inject lateinit var clockRegistry: Lazy<ClockRegistry>
    @Inject lateinit var secureSettingsRepository: Lazy<SecureSettingsRepository>
    @Inject lateinit var systemSettingsRepository: Lazy<SystemSettingsRepository>
    @Inject lateinit var clockPickerInteractor: Lazy<ClockPickerInteractor>
    @Inject lateinit var clockPickerSnapshotRestorer: Lazy<ClockPickerSnapshotRestorer>

    override fun getCustomizationSections(activity: ComponentActivity): CustomizationSections {
        val appContext = activity.applicationContext
        val clockViewFactory = getClockViewFactory(activity)
        val resources = activity.resources
        return customizationSections
            ?: DefaultCustomizationSections(
                    getColorPickerViewModelFactory(appContext),
                    getKeyguardQuickAffordancePickerViewModelFactory(appContext),
                    colorContrastSectionViewModelFactory.get(),
                    getNotificationSectionViewModelFactory(appContext),
                    getFlags(),
                    getClockCarouselViewModelFactory(
                        interactor = clockPickerInteractor.get(),
                        clockViewFactory = clockViewFactory,
                        resources = resources,
                    ),
                    clockViewFactory,
                    getThemedIconSnapshotRestorer(appContext),
                    getThemedIconInteractor(),
                    getGridInteractor(appContext),
                    colorPickerInteractor.get(),
                    getUserEventLogger(),
                )
                .also { customizationSections = it }
    }

    override fun getDeepLinkRedirectIntent(context: Context, uri: Uri): Intent {
        val intent = Intent()
        intent.setClass(context, CustomizationPickerActivity::class.java)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return intent
    }

    override fun getDownloadableIntentAction(): String? {
        return null
    }

    @Synchronized
    override fun getUserEventLogger(): ThemesUserEventLogger {
        return themesUserEventLogger.get()
    }

    override fun getFragmentFactory(): FragmentFactory? {
        return fragmentFactory ?: ThemePickerFragmentFactory().also { fragmentFactory }
    }

    override fun getSnapshotRestorers(context: Context): Map<Int, SnapshotRestorer> {
        return super<WallpaperPicker2Injector>.getSnapshotRestorers(context).toMutableMap().apply {
            this[KEY_QUICK_AFFORDANCE_SNAPSHOT_RESTORER] =
                keyguardQuickAffordanceSnapshotRestorer.get()
            // TODO(b/285047815): Enable after adding wallpaper id for default static wallpaper
            if (getFlags().isWallpaperRestorerEnabled()) {
                this[KEY_WALLPAPER_SNAPSHOT_RESTORER] = getWallpaperSnapshotRestorer(context)
            }
            this[KEY_NOTIFICATIONS_SNAPSHOT_RESTORER] = getNotificationsSnapshotRestorer(context)
            this[KEY_DARK_MODE_SNAPSHOT_RESTORER] = getDarkModeSnapshotRestorer(context)
            this[KEY_THEMED_ICON_SNAPSHOT_RESTORER] = getThemedIconSnapshotRestorer(context)
            this[KEY_APP_GRID_SNAPSHOT_RESTORER] = getGridSnapshotRestorer(context)
            this[KEY_COLOR_PICKER_SNAPSHOT_RESTORER] = colorPickerSnapshotRestorer.get()
            this[KEY_CLOCKS_SNAPSHOT_RESTORER] = clockPickerSnapshotRestorer.get()
        }
    }

    override fun getCustomizationPreferences(context: Context): CustomizationPreferences {
        return getPreferences(context) as CustomizationPreferences
    }

    override fun getWallpaperInteractor(context: Context): WallpaperInteractor {
        return injectedWallpaperInteractor.get()
    }

    override fun getKeyguardQuickAffordancePickerInteractor(
        context: Context
    ): KeyguardQuickAffordancePickerInteractor {
        return keyguardQuickAffordancePickerInteractor.get()
    }

    fun getKeyguardQuickAffordancePickerViewModelFactory(
        context: Context
    ): KeyguardQuickAffordancePickerViewModel.Factory {
        return keyguardQuickAffordancePickerViewModelFactory
            ?: KeyguardQuickAffordancePickerViewModel.Factory(
                    context.applicationContext,
                    getKeyguardQuickAffordancePickerInteractor(context),
                    getWallpaperInteractor(context),
                    getCurrentWallpaperInfoFactory(context),
                    getUserEventLogger(),
                )
                .also { keyguardQuickAffordancePickerViewModelFactory = it }
    }

    fun getNotificationSectionViewModelFactory(
        context: Context
    ): NotificationSectionViewModel.Factory {
        return notificationSectionViewModelFactory
            ?: NotificationSectionViewModel.Factory(
                    interactor = getNotificationsInteractor(context),
                    logger = getUserEventLogger(),
                )
                .also { notificationSectionViewModelFactory = it }
    }

    private fun getNotificationsInteractor(context: Context): NotificationSettingsInteractor {
        return notificationSettingsInteractor
            ?: NotificationSettingsInteractor(
                    repository =
                        NotificationSettingsRepository(
                            backgroundScope = bgScope,
                            backgroundDispatcher = bgDispatcher,
                            secureSettingsRepository = secureSettingsRepository.get(),
                            systemSettingsRepository = systemSettingsRepository.get(),
                        )
                )
                .also { notificationSettingsInteractor = it }
    }

    private fun getNotificationsSnapshotRestorer(context: Context): NotificationsSnapshotRestorer {
        return notificationsSnapshotRestorer
            ?: NotificationsSnapshotRestorer(
                    interactor = getNotificationsInteractor(context = context),
                    backgroundScope = bgScope,
                )
                .also { notificationsSnapshotRestorer = it }
    }

    override fun getClockCarouselViewModelFactory(
        interactor: ClockPickerInteractor,
        clockViewFactory: ClockViewFactory,
        resources: Resources,
    ): ClockCarouselViewModel.Factory {
        return clockCarouselViewModelFactory
            ?: ClockCarouselViewModel.Factory(
                    interactor,
                    bgDispatcher,
                    clockViewFactory,
                    resources,
                    getUserEventLogger(),
                )
                .also { clockCarouselViewModelFactory = it }
    }

    override fun getClockViewFactory(activity: ComponentActivity): ClockViewFactory {
        return clockViewFactory
            ?: ThemePickerClockViewFactory(
                    activity,
                    WallpaperManager.getInstance(activity.applicationContext),
                    clockRegistry.get(),
                )
                .also {
                    clockViewFactory = it
                    activity.lifecycle.addObserver(
                        object : DefaultLifecycleObserver {
                            override fun onDestroy(owner: LifecycleOwner) {
                                super.onDestroy(owner)
                                if ((owner as Activity).isChangingConfigurations()) return
                                clockViewFactory?.onDestroy()
                            }
                        }
                    )
                }
    }

    override fun getWallpaperColorResources(
        wallpaperColors: WallpaperColors,
        context: Context,
    ): WallpaperColorResources {
        return ThemedWallpaperColorResources(wallpaperColors, secureSettingsRepository.get())
    }

    override fun getColorPickerViewModelFactory(context: Context): ColorPickerViewModel.Factory {
        return colorPickerViewModelFactory
            ?: ColorPickerViewModel.Factory(
                    context.applicationContext,
                    colorPickerInteractor.get(),
                    getUserEventLogger(),
                )
                .also { colorPickerViewModelFactory = it }
    }

    private fun getColorCustomizationManager(context: Context): ColorCustomizationManager {
        return colorCustomizationManager
            ?: ColorCustomizationManager.getInstance(context, OverlayManagerCompat(context)).also {
                colorCustomizationManager = it
            }
    }

    fun getDarkModeSnapshotRestorer(context: Context): DarkModeSnapshotRestorer {
        val appContext = context.applicationContext
        return darkModeSnapshotRestorer
            ?: DarkModeSnapshotRestorer(
                    context = appContext,
                    manager = uiModeManager.get(),
                    backgroundDispatcher = bgDispatcher,
                )
                .also { darkModeSnapshotRestorer = it }
    }

    protected fun getThemedIconSnapshotRestorer(context: Context): ThemedIconSnapshotRestorer {
        val optionProvider = ThemedIconSwitchProvider.getInstance(context)
        return themedIconSnapshotRestorer
            ?: ThemedIconSnapshotRestorer(
                    isActivated = { optionProvider.isThemedIconEnabled },
                    setActivated = { isActivated ->
                        optionProvider.isThemedIconEnabled = isActivated
                    },
                    interactor = getThemedIconInteractor(),
                )
                .also { themedIconSnapshotRestorer = it }
    }

    protected fun getThemedIconInteractor(): ThemedIconInteractor {
        return themedIconInteractor
            ?: ThemedIconInteractor(repository = ThemeIconRepository()).also {
                themedIconInteractor = it
            }
    }

    override fun getClockSettingsViewModelFactory(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
        clockViewFactory: ClockViewFactory,
    ): ClockSettingsViewModel.Factory {
        return clockSettingsViewModelFactory
            ?: ClockSettingsViewModel.Factory(
                    context.applicationContext,
                    clockPickerInteractor.get(),
                    colorPickerInteractor.get(),
                    getUserEventLogger(),
                ) { clockId ->
                    clockId?.let { clockViewFactory.getController(clockId).config.isReactiveToTone }
                        ?: false
                }
                .also { clockSettingsViewModelFactory = it }
    }

    fun getGridScreenViewModelFactory(context: Context): ViewModelProvider.Factory {
        return gridScreenViewModelFactory
            ?: GridScreenViewModel.Factory(
                    context = context,
                    interactor = getGridInteractor(context),
                )
                .also { gridScreenViewModelFactory = it }
    }

    fun getGridInteractor(context: Context): GridInteractor {
        val appContext = context.applicationContext
        return gridInteractor
            ?: GridInteractor(
                    applicationScope = getApplicationCoroutineScope(),
                    repository =
                        GridRepositoryImpl(
                            applicationScope = getApplicationCoroutineScope(),
                            manager = GridOptionsManager.getInstance(context),
                            backgroundDispatcher = bgDispatcher,
                            isGridApplyButtonEnabled =
                                BaseFlags.get().isGridApplyButtonEnabled(appContext),
                        ),
                    snapshotRestorer = { getGridSnapshotRestorer(appContext) },
                )
                .also { gridInteractor = it }
    }

    private fun getGridSnapshotRestorer(context: Context): GridSnapshotRestorer {
        return gridSnapshotRestorer
            ?: GridSnapshotRestorer(interactor = getGridInteractor(context)).also {
                gridSnapshotRestorer = it
            }
    }

    override fun isCurrentSelectedColorPreset(context: Context): Boolean {
        val colorManager =
            ColorCustomizationManager.getInstance(context, OverlayManagerCompat(context))
        return COLOR_SOURCE_PRESET == colorManager.currentColorSource
    }

    companion object {
        @JvmStatic
        private val KEY_QUICK_AFFORDANCE_SNAPSHOT_RESTORER =
            WallpaperPicker2Injector.MIN_SNAPSHOT_RESTORER_KEY
        @JvmStatic
        private val KEY_WALLPAPER_SNAPSHOT_RESTORER = KEY_QUICK_AFFORDANCE_SNAPSHOT_RESTORER + 1
        @JvmStatic
        private val KEY_NOTIFICATIONS_SNAPSHOT_RESTORER = KEY_WALLPAPER_SNAPSHOT_RESTORER + 1
        @JvmStatic
        private val KEY_DARK_MODE_SNAPSHOT_RESTORER = KEY_NOTIFICATIONS_SNAPSHOT_RESTORER + 1
        @JvmStatic
        private val KEY_THEMED_ICON_SNAPSHOT_RESTORER = KEY_DARK_MODE_SNAPSHOT_RESTORER + 1
        @JvmStatic
        private val KEY_APP_GRID_SNAPSHOT_RESTORER = KEY_THEMED_ICON_SNAPSHOT_RESTORER + 1
        @JvmStatic
        private val KEY_COLOR_PICKER_SNAPSHOT_RESTORER = KEY_APP_GRID_SNAPSHOT_RESTORER + 1
        @JvmStatic private val KEY_CLOCKS_SNAPSHOT_RESTORER = KEY_COLOR_PICKER_SNAPSHOT_RESTORER + 1

        /**
         * When this injector is overridden, this is the minimal value that should be used by
         * restorers returns in [getSnapshotRestorers].
         *
         * It should always be greater than the biggest restorer key.
         */
        @JvmStatic protected val MIN_SNAPSHOT_RESTORER_KEY = KEY_CLOCKS_SNAPSHOT_RESTORER + 1
    }
}
