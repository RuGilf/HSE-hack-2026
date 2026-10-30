package ru.lifety.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** On iOS only the header drags the sheet; scrolling the form never moves its container. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LifetyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onDismiss by rememberUpdatedState(onDismissRequest)
    val density = LocalDensity.current.density
    var dismissing by remember(sheetState) { mutableStateOf(false) }
    var drag by remember(sheetState) { mutableFloatStateOf(0f) }
    var height by remember(sheetState) { mutableFloatStateOf(1f) }
    var settling by remember(sheetState) { mutableStateOf<Job?>(null) }
    fun settle() {
        settling?.cancel()
        settling = scope.launch {
            animate(drag, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 600f)) { value, _ -> drag = value }
        }
    }
    val dismiss: () -> Unit = {
        if (!dismissing) scope.launch {
            dismissing = true
            try {
                sheetState.hide()
                // Editors may refuse dismissal while saving; put the dragged panel back.
                if (!sheetState.isVisible) onDismiss() else settle()
            } finally { dismissing = false }
        }
    }
    CompositionLocalProvider(LocalOverscrollFactory provides if (isIos) null else LocalOverscrollFactory.current) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = if (isIos) modifier.onSizeChanged { height = it.height.toFloat() }
                .offset { IntOffset(0, drag.roundToInt()) } else modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = !isIos,
            containerColor = containerColor,
            contentColor = contentColor,
            // M3 wraps the dragHandle slot in a clickable even when gestures are disabled.
            // Render our iOS handle in the content instead, so a tap cannot dismiss the form.
            dragHandle = if (isIos) null else ({ BottomSheetDefaults.DragHandle() }),
        ) {
            CompositionLocalProvider(LocalNativeSurfaceColor provides containerColor) {
                if (isIos) SheetSwipeHandle(
                    onStart = { settling?.cancel() },
                    onDrag = { delta -> if (!dismissing) drag = (drag + delta).coerceIn(0f, height) },
                    onCancel = { settle() },
                    onRelease = { velocity ->
                        if (shouldDismissSheet(drag / density, velocity / density, height / density)) dismiss() else settle()
                    }, onDismiss = dismiss,
                )
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSwipeHandle(onStart: () -> Unit, onDrag: (Float) -> Unit,
    onCancel: () -> Unit, onRelease: (Float) -> Unit, onDismiss: () -> Unit) {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val cancel by rememberUpdatedState(onCancel)
    val release by rememberUpdatedState(onRelease)
    val dismiss by rememberUpdatedState(onDismiss)
    val tracker = remember { VelocityTracker() }
    Box(Modifier.fillMaxWidth()
        .semantics(mergeDescendants = true) {
            customActions = listOf(CustomAccessibilityAction("Закрыть окно") { dismiss(); true })
        }
        .pointerInput(Unit) {
            var travel = 0f
            detectVerticalDragGestures(
                onDragStart = { start(); travel = 0f; tracker.resetTracking() },
                onDragCancel = { cancel() },
                onDragEnd = { release(tracker.calculateVelocity().y) },
                onVerticalDrag = { change, delta ->
                    change.consume()
                    travel += delta
                    tracker.addPosition(change.uptimeMillis, Offset(0f, travel))
                    drag(delta)
                },
            )
        }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        BottomSheetDefaults.DragHandle()
    }
}
