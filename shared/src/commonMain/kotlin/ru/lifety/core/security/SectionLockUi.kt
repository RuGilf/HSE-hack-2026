package ru.lifety.core.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.*

internal val LocalSectionLockController = staticCompositionLocalOf<SectionLockController?> { null }
internal val LocalSectionLockState = staticCompositionLocalOf { SectionLockState() }

@Composable
internal fun SectionLockProvider(content: @Composable () -> Unit) {
    val controller = rememberSectionLockController()
    val state by controller.state.collectAsState()
    ObserveSectionLockBackground(controller::lock)
    CompositionLocalProvider(LocalSectionLockController provides controller, LocalSectionLockState provides state, content = content)
}

@Composable
internal fun SectionGuard(route: String, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val state = LocalSectionLockState.current
    val controller = LocalSectionLockController.current
    when {
        !state.ready -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (state.busy) CircularProgressIndicator()
            state.error?.let { Text(it); Button(onClick = { controller?.reload() }, enabled = !state.busy) { Text("Повторить") } }
        }
        state.needsUnlock(route) -> {
            var pin by remember(state.epoch, route) { mutableStateOf("") }
            LaunchedEffect(state.error) { if (state.error != null) pin = "" }
            PinPad(lockSections[route] ?: "План", "Введите код, чтобы открыть раздел", pin, { pin = it }, !state.busy,
                "Открыть", { entered -> pin = ""; controller?.unlock(entered) }, state.error, onBack, autoSubmit = true)
        }
        else -> content()
    }
}

/** PIN digits are transient memory only: no saved state, text field, clipboard or accessibility value. */
@Composable
private fun PinPad(title: String, subtitle: String, pin: String, onPin: (String) -> Unit, enabled: Boolean,
    action: String, submit: (String) -> Unit, error: String? = null, onBack: (() -> Unit)? = null,
    autoSubmit: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp + if (onBack == null) LocalBottomNavigationInset.current else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) TextButton(onClick = onBack, enabled = enabled) { Text("‹ Назад") }
            LifetyMascot(Modifier.size(40.dp), animated = false, emotion = LifetyEmotion.Focused)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((0..5).joinToString("  ") { if (it < pin.length) "●" else "○" }, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Введено ${pin.length} из 6 цифр" })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { digit ->
                        if (digit.isEmpty()) Spacer(Modifier.size(64.dp)) else OutlinedButton(
                            onClick = {
                                val next = if (digit == "⌫") pin.dropLast(1) else (pin + digit).take(6)
                                onPin(next)
                                if (autoSubmit && digit != "⌫" && validPin(next)) submit(next)
                            }, enabled = enabled,
                            modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp), shape = MaterialTheme.shapes.extraLarge) {
                            Text(digit, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            if (autoSubmit) {
                if (!enabled) CircularProgressIndicator(Modifier.size(24.dp))
                else Text("Раздел откроется после шестой цифры", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Button(onClick = { submit(pin) }, enabled = enabled && validPin(pin), modifier = Modifier.fillMaxWidth()) { Text(if (enabled) action else "Проверяем…") }
        }
    }
}

@Composable
internal fun SectionLockSettingsContent(onBack: () -> Unit) {
    val controller = LocalSectionLockController.current ?: return
    val state = LocalSectionLockState.current
    val existing = state.configuration.enabled
    var sections by remember(state.ready) { mutableStateOf(state.configuration.sections) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var step by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) { if (state.error != null) currentPin = "" }
    if (step.isNotEmpty()) {
        PinPad(when (step) { "current" -> "Текущий код"; "new" -> "Новый код"; else -> "Повторите код" },
            "6 цифр для выбранных разделов", input, { input = it; inputError = null }, !state.busy, "Продолжить", { _ ->
                when (step) {
                    "current" -> { currentPin = input; input = ""; step = "" }
                    "new" -> { firstPin = input; input = ""; step = "confirm" }
                    else -> if (input == firstPin) { newPin = input; firstPin = ""; input = ""; step = "" }
                        else { input = ""; inputError = "Коды не совпадают. Введите код ещё раз." }
                }
            }, inputError, onBack = { input = ""; firstPin = ""; step = "" })
        return
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !state.busy) { Text("‹ Настройки") }
        Text("Код-пароль", style = MaterialTheme.typography.headlineSmall)
        Text("Один код для выбранных разделов. После сворачивания приложения код понадобится снова.", style = MaterialTheme.typography.bodyMedium)
        Text("Главная и ассистент также запрашивают код, если защищены План, Финансы или Здоровье: там есть общие данные.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.ready) {
            Text(state.error ?: "Загружаем защиту…")
            TextButton(onClick = { controller.reload() }, enabled = !state.busy) { Text("Повторить") }
            return@Column
        }
        lockSections.forEach { (id, title) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f)); Checkbox(id in sections, { checked -> sections = if (checked) sections + id else sections - id; saved = false }, enabled = !state.busy,
                    modifier = Modifier.semantics { contentDescription = "Защитить раздел $title" })
            }
        }
        if (existing) FilledTonalButton(onClick = { input = ""; step = "current" }, enabled = !state.busy) {
            Text(if (validPin(currentPin)) "Текущий код введён" else "Ввести текущий код")
        }
        FilledTonalButton(onClick = { input = ""; step = "new" }, enabled = !state.busy) {
            Text(if (validPin(newPin)) "Новый код задан" else if (existing) "Изменить код · необязательно" else "Задать код")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saved) Text("Настройки защиты сохранены", color = MaterialTheme.colorScheme.primary)
        Button(onClick = { controller.configure(currentPin, newPin.takeIf(::validPin), sections) {
            currentPin = ""; newPin = ""; firstPin = ""; saved = true
        } }, enabled = !state.busy && (if (existing) validPin(currentPin) else validPin(newPin) && sections.isNotEmpty()), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Сохраняем…" else if (existing && sections.isEmpty()) "Убрать защиту" else "Сохранить защиту")
        }
        if (existing && state.unlocked) TextButton(onClick = { currentPin = ""; newPin = ""; controller.lock(); onBack() }) { Text("Заблокировать сейчас") }
        Text("Код не отправляется в ИИ и не входит в экспорт. Это блокировка экранов, а не шифрование базы данных. Восстановления забытого кода нет.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
