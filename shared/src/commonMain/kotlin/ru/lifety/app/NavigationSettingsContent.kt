package ru.lifety.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.*
import ru.lifety.core.ui.*

@Composable internal fun NavigationSettingsContent(onBack: () -> Unit) {
    val state = LocalNavigationState.current
    val controller = LocalNavigationController.current
    var routes by remember { mutableStateOf(state.routes) }
    var presentation by remember { mutableStateOf(state.presentation) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !state.saving, modifier = Modifier.semantics { contentDescription = "Вернуться в настройки" }) { LifetyIcon(LifetySymbol.Back) }
            Text("Нижняя панель", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("До 5 вкладок. Остальные разделы — в меню «Все разделы».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isIos) {
                Text("Вид навигации", style = MaterialTheme.typography.titleSmall)
                LifetySegmentedControl(listOf("Панель", "Дуга"), if (presentation == "arc") 1 else 0,
                    { presentation = if (it == 1) "arc" else "tabs" }, Modifier.fillMaxWidth(), enabled = !state.saving)
                Text(if (presentation == "arc") "Удерживайте центральный овал — разделы появятся над ним по дуге. Нажмите раздел для перехода. Касание овала также открывает меню."
                    else "Все выбранные разделы видны в нижней панели.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("В панели", style = MaterialTheme.typography.titleSmall)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    routes.forEachIndexed { index, route ->
                        val destination = AppDestination.fromRoute(route)
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(destination.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { routes = routes.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = !state.saving && index > 0,
                                modifier = Modifier.semantics { contentDescription = "Выше: ${destination.title}" }) { Text("↑") }
                            IconButton(onClick = { routes = routes.toMutableList().apply { add(index + 1, removeAt(index)) } }, enabled = !state.saving && index < routes.lastIndex,
                                modifier = Modifier.semantics { contentDescription = "Ниже: ${destination.title}" }) { Text("↓") }
                            IconButton(onClick = { routes = routes - route }, enabled = !state.saving && routes.size > 1,
                                modifier = Modifier.semantics { contentDescription = "Убрать: ${destination.title}" }) { LifetyIcon(LifetySymbol.Close, Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            Text("Другие разделы", style = MaterialTheme.typography.titleSmall)
            availableNavigation.filter { it !in routes }.forEach { route ->
                val destination = AppDestination.fromRoute(route)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LifetyIcon(destination.symbol, Modifier.padding(end = 12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(destination.title, Modifier.weight(1f))
                    TextButton(onClick = { routes = routes + route }, enabled = !state.saving && routes.size < 5) { Text("Добавить") }
                }
            }
            if (routes.size == 5) Text("Чтобы добавить вкладку, сначала уберите одну из панели.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { routes = defaultNavigation }, enabled = !state.saving) { Text("Восстановить стандартные") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { controller?.save(routes, presentation, onBack) }, enabled = !state.saving && routes.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text(if (state.saving) "Сохраняем…" else "Сохранить") }
    }
}
