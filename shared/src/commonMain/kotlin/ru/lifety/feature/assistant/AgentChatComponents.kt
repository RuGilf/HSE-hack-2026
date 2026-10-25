package ru.lifety.feature.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import ru.lifety.feature.assistant.agent.*
import ru.lifety.feature.assistant.ai.assistantLocalTime

@Composable internal fun AgentActivity(messages: List<AgentMessage>, busy: Boolean) {
    val calls = messages.flatMap { it.calls }.takeLast(8)
    if (calls.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val receipts = messages.filter { it.role == "tool" }.associateBy { it.callId }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (busy) actionLabel(calls.last().name) else "Последние действия · ${calls.size}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            if (expanded) calls.forEach { call ->
                val receipt = receipts[call.id]
                val status = receipt?.let { runCatching { agentJson.parseToJsonElement(it.text).jsonObject["status"]?.jsonPrimitive?.content }.getOrNull() }
                Text((if (receipt == null) "◌ " else if (status in setOf("ok", "archived", "offloaded", "awaiting_approval")) "✓ " else "• ") + actionLabel(call.name),
                    style = MaterialTheme.typography.bodySmall)
                Text(actionDetail(call), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("Нажмите, чтобы посмотреть действия", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun actionDetail(call: ToolCall): String {
    val args = runCatching { agentJson.parseToJsonElement(call.arguments).jsonObject }.getOrNull()
    val path = args?.get("path")?.jsonPrimitive?.content.orEmpty()
    return when {
        path.startsWith("data/expenses") -> "Расходы"
        path.startsWith("data/tasks") -> "Задачи"
        path.startsWith("data/health") -> "Показатели здоровья"
        path.startsWith("memory/") -> "Подтверждённая память"
        path.startsWith("skills/") || path.startsWith("schemas/") -> "Выбираю способ решения"
        call.name == "script_run" -> "Локальный расчёт и сохранение результата"
        path.isNotBlank() -> path.substringAfterLast('/')
        else -> "В рабочей среде Lifety"
    }
}

private fun actionLabel(name: String) = when (name) {
    "files_read", "files_list", "files_search" -> "Смотрю доступные данные"
    "script_run" -> "Проверяю расчёты"
    "files_write" -> "Сохраняю материалы"
    "files_delete" -> "Убираю рабочий файл"
    "source_refresh" -> "Обновляю сведения"
    "app_propose" -> "Готовлю действие на подтверждение"
    else -> "Работаю с данными"
}

@Composable internal fun AgentProposalCard(proposal: AgentProposal, busy: Boolean, approve: () -> Unit, reject: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when (proposal.kind) {
                "task_upsert" -> "Сохранить задачу"; "task_delete" -> "Удалить задачу"
                "expense_upsert" -> "Сохранить расход"; "expense_delete" -> "Удалить расход"
                else -> "Запомнить о вас"
            }, style = MaterialTheme.typography.titleMedium)
            val before = proposal.expected as? JsonObject
            if (before != null) Text("Сейчас: " + describeAgentAction(proposal.kind, before), style = MaterialTheme.typography.bodySmall)
            if (!proposal.kind.endsWith("_delete")) Text(describeAgentAction(proposal.kind, proposal.body))
            if (proposal.status == "applied") Text("✓ Применено", color = MaterialTheme.colorScheme.primary)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = approve, enabled = !busy) { Text(if (proposal.status == "applying") "Повторить" else "Подтвердить") }
                TextButton(onClick = reject, enabled = !busy) { Text("Отклонить") }
            }
        }
    }
}

internal fun describeAgentAction(kind: String, body: JsonObject): String {
    fun text(key: String) = body[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty()
    fun date(key: String) = text(key).toLongOrNull()?.let { assistantLocalTime(it).take(16).replace('T', ' ') } ?: "не задано"
    return when {
        kind.startsWith("task_") -> "${text("title")}\nПлан: ${date("scheduledAt")} · Срок: ${date("deadlineAt")}\n" +
            if (text("isCompleted") == "true") "Выполнена" else "Не выполнена"
        kind.startsWith("expense_") -> {
            val amount = text("amountMinor").toLongOrNull()
            val money = if (amount == null) text("amountMinor") else "${amount / 100},${(amount % 100).toString().padStart(2, '0')}"
            "${text("merchant")} · $money ${text("currency")}\nКатегория: ${text("category")}\nДата: ${date("timestamp")}"
        }
        else -> "${text("value")}\nИз ваших слов: «${text("quote") }»"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SavedAgentSheet(state: AgentChatState, controller: AgentChatController, dismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var forget by remember { mutableStateOf<UserFact?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.view.document.facts) {
        try { files = controller.workspace.files("reports") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Не удалось прочитать сохранённые материалы" }
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Сохранённое", style = MaterialTheme.typography.titleLarge)
            if (content != null) {
                TextButton(onClick = { content = null }) { Text("← К списку") }
                SelectionContainer { Text(content!!, style = MaterialTheme.typography.bodyMedium) }
            } else {
                Text("Память о вас", style = MaterialTheme.typography.titleMedium)
                val facts = state.view.document.facts.filter { state.view.document.scopes.memory && AgentRunner.factAllowed(it, state.view.document.scopes) }
                if (facts.isEmpty()) Text("Подтверждённых сведений пока нет. Попросите агента что-нибудь запомнить.", style = MaterialTheme.typography.bodySmall)
                facts.forEach { fact ->
                    ListItem(headlineContent = { Text(fact.value) }, supportingContent = { Text("«${fact.sourceQuote}»") },
                        trailingContent = { TextButton(onClick = { forget = fact }) { Text("Забыть") } })
                }
                HorizontalDivider()
                Text("Разборы", style = MaterialTheme.typography.titleMedium)
                if (files.isEmpty()) Text("Здесь появятся разборы, которые агент сохранил для следующих разговоров.", style = MaterialTheme.typography.bodySmall)
                files.forEach { file ->
                    TextButton(onClick = { scope.launch {
                        try { val text = controller.workspace.read(file.path); content = text.take(30000) + if (text.length > 30000) "\n…Показана первая часть." else "" }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = "Материал недоступен" }
                    } }) { Text(file.path.substringAfterLast('/')) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
    forget?.let { fact -> AlertDialog(onDismissRequest = { forget = null }, title = { Text("Забыть это сведение?") },
        text = { Text("Очистятся переписка и рабочие материалы, чтобы сведение не вернулось из старого разбора. Остальная подтверждённая память сохранится.") },
        confirmButton = { TextButton(onClick = { controller.forget(fact.id); forget = null }) { Text("Забыть") } },
        dismissButton = { TextButton(onClick = { forget = null }) { Text("Отмена") } }) }
}
