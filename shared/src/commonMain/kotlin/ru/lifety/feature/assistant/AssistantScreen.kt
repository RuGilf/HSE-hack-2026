package ru.lifety.feature.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.*
import ru.lifety.feature.assistant.agent.*
import com.mikepenz.markdown.m3.Markdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    val runtime = rememberAgentRuntime()
    val repositories = LocalRepositories.current
    val scope = rememberCoroutineScope()
    val controller = remember(runtime, repositories, scope) { AgentChatController(runtime, repositories, scope) }
    val state by controller.state.collectAsState()
    var settings by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var clearChat by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(controller) { controller.initialize().join() }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val messages = remember(state.view.document.messages) {
        state.view.document.messages.filter { (it.role == "user" && !it.text.startsWith("APP_RECEIPT:")) || (it.role == "assistant" && it.text.isNotBlank()) }
    }
    val proposals = state.view.document.proposals.filter { it.status != "rejected" }
    val latestAction = state.view.document.messages.lastOrNull { it.calls.isNotEmpty() }?.calls?.lastOrNull()?.id
    LaunchedEffect(messages.size, proposals.size, latestAction) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.size + proposals.size)
    }
    fun send() { if (controller.send(input.trim()) != null) { input = ""; focus.clearFocus() } }
    Column(modifier.fillMaxSize().imePadding().padding(bottom = ru.lifety.core.ui.LocalBottomNavigationInset.current)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.connected) state.credentials.settings.model else "Подключите свою модель", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            TextButton(onClick = { saved = true }, enabled = state.connected && !state.busy) { Text("Сохранённое") }
            IconButton(onClick = { settings = true }, enabled = !state.busy,
                modifier = Modifier.semantics { contentDescription = "Настройки ассистента" }) { LifetyIcon(LifetySymbol.Settings) }
        }
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LifetyMascot(Modifier.size(100.dp), emotion = LifetyEmotion.Calm)
                Spacer(Modifier.height(24.dp))
                Text("С чего начнём?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Разберём расходы, освободим день или вернёмся к тому, что уже обсуждали.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                if (!state.connected) Button(onClick = { settings = true }, enabled = !state.busy) { Text("Подключить ИИ") }
                else {
                    OutlinedButton(onClick = { input = "Разбери мои расходы и предложи, на чём можно сэкономить." }, modifier = Modifier.fillMaxWidth()) { Text("Куда уходят деньги?") }
                    OutlinedButton(onClick = { input = "Посмотри мои задачи и помоги реалистично спланировать день." }, modifier = Modifier.fillMaxWidth()) { Text("Помоги спланировать день") }
                }
            }
        } else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(messages, key = { it.id }, contentType = { it.role }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
                    if (message.role == "user") Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp), modifier = Modifier.widthIn(max = 340.dp)) {
                        SelectionContainer { Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
                    } else SelectionContainer { Markdown(message.text, modifier = Modifier.fillMaxWidth()) }
                }
            }
            items(proposals, key = { it.id }, contentType = { "approval" }) { proposal ->
                AgentProposalCard(proposal, state.busy, { controller.approve(proposal.id) }, { controller.reject(proposal.id) })
            }
            item(key = "activity") { AgentActivity(state.view.document.messages, state.busy) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        (state.error ?: state.view.error)?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it.take(8000) }, Modifier.weight(1f), enabled = state.connected,
                placeholder = { Text("Спросите что-нибудь…") }, maxLines = 5, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (!state.busy && input.isNotBlank()) send() }))
            if (state.busy) FilledTonalButton(onClick = controller::cancel, modifier = Modifier.heightIn(min = 56.dp)) { Text("Стоп") }
            else Button(onClick = ::send, enabled = state.connected && input.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) { Text("Отправить") }
        }
        if (messages.isNotEmpty()) TextButton(onClick = { clearChat = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Новый разговор") }
    }
    if (settings) AgentSettingsSheet(state, controller) { settings = false }
    if (saved) SavedAgentSheet(state, controller) { saved = false }
    if (clearChat) AlertDialog(onDismissRequest = { clearChat = false }, title = { Text("Начать новый разговор?") },
        text = { Text("Переписка очистится. Подтверждённая память и сохранённые разборы останутся.") },
        confirmButton = { TextButton(onClick = { controller.newChat(); clearChat = false }) { Text("Начать") } },
        dismissButton = { TextButton(onClick = { clearChat = false }) { Text("Отмена") } })
}
