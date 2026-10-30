@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.core.appearance

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.*
import platform.Foundation.NSUserDefaults
import platform.UIKit.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class IosAppearance : AppearancePlatform {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun readTheme(): String? = defaults.stringForKey("lifety.appearance.theme")
    override suspend fun saveTheme(id: String) { defaults.setObject(id, forKey = "lifety.appearance.theme") }
    override val supportsIcons: Boolean get() = UIApplication.sharedApplication.supportsAlternateIcons
    override fun currentIcon(): LifetyPalette = LifetyPalette.fromIconName(UIApplication.sharedApplication.alternateIconName)
    override suspend fun changeIcon(theme: LifetyPalette): Unit = suspendCancellableCoroutine { continuation ->
        UIApplication.sharedApplication.setAlternateIconName(theme.alternateIconName) { error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            }
        }
    }
}

private val controller by lazy { AppearanceController(IosAppearance(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
@Composable internal actual fun rememberAppearanceController(): AppearanceController = controller

internal fun Color.toUiColor() = UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
@Composable internal actual fun ApplyPlatformAppearance(palette: ThemePalette) {
    val host = LocalUIViewController.current
    SideEffect {
        val style = if (palette.isDark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        host.overrideUserInterfaceStyle = style
        host.view.window?.overrideUserInterfaceStyle = style
        host.view.backgroundColor = palette.background.toUiColor()
        host.view.window?.backgroundColor = palette.background.toUiColor()
        host.view.tintColor = palette.accent.toUiColor()
        // Compose dialogs use additional controllers. Their explicit old style must not
        // survive a palette switch while an editor or picker is already open.
        fun updateStyle(controller: UIViewController) {
            controller.overrideUserInterfaceStyle = style
            controller.childViewControllers.filterIsInstance<UIViewController>().forEach(::updateStyle)
            controller.presentedViewController?.let(::updateStyle)
        }
        host.view.window?.rootViewController?.let(::updateStyle)
    }
}
