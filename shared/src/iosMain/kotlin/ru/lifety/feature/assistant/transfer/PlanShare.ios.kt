@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.feature.assistant.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*

@Composable
internal actual fun rememberPlanShare(): (String) -> Unit {
    val host = LocalUIViewController.current
    return remember(host) { { text ->
        val presenter = planPresenter(host)
        val activity = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        activity.popoverPresentationController?.let { popover ->
            popover.sourceView = presenter.view
            popover.sourceRect = CGRectMake(16.0, 16.0, 1.0, 1.0)
        }
        presenter.presentViewController(activity, animated = true, completion = null)
    } }
}
