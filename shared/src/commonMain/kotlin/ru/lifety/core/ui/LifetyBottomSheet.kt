package ru.lifety.core.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Keeps iOS content scrolling independent of sheet dismissal, including at scroll boundaries. */
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
    var dismissing by remember(sheetState) { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!dismissing) scope.launch {
            dismissing = true
            try {
                sheetState.hide()
                // Respect the editor's confirmValueChange guard while saving.
                if (!sheetState.isVisible) onDismiss()
            } finally { dismissing = false }
        }
    }
    // iOS rubber-band overscroll and M3's nested sheet drag both consume edge deltas.
    // Keep inertia, but avoid a second moving surface inside the modal on iOS.
    CompositionLocalProvider(LocalOverscrollFactory provides if (isIos) null else LocalOverscrollFactory.current) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = !isIos,
            containerColor = containerColor,
            contentColor = contentColor,
            dragHandle = {
                if (isIos) SheetDismissHandle(dismiss)
                else BottomSheetDefaults.DragHandle()
            },
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDismissHandle(onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Box(Modifier
        .clickable(role = Role.Button, onClickLabel = "Закрыть окно", onClick = { dismiss() })
        .pointerInput(threshold) {
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onDragCancel = { distance = 0f },
                onDragEnd = { if (distance >= threshold) dismiss() },
                onVerticalDrag = { change, delta -> change.consume(); distance = (distance + delta).coerceAtLeast(0f) },
            )
        }) {
        BottomSheetDefaults.DragHandle()
    }
}
