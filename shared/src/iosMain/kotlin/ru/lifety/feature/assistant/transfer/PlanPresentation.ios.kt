@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.feature.assistant.transfer

import platform.UIKit.*

/** Transfer runs outside Compose dialogs. Present from the app window, never the keyboard overlay. */
internal fun planPresenter(fallback: UIViewController): UIViewController {
    val hostWindow = fallback.view.window
    val scene = hostWindow?.windowScene
        ?: UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
    val window = hostWindow?.takeIf { it.windowLevel == UIWindowLevelNormal }
        ?: scene?.windows?.filterIsInstance<UIWindow>()
            ?.firstOrNull { !it.hidden && it.alpha > 0.0 && it.windowLevel == UIWindowLevelNormal && it.rootViewController != null }
    var presenter = window?.rootViewController ?: fallback
    while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
    return presenter
}
