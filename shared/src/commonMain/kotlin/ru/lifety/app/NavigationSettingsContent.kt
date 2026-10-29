package ru.lifety.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.*

@Composable internal fun NavigationSettingsContent(onBack: () -> Unit) {
    val state = LocalNavigationState.current
    val controller = LocalNavigationController.current
    var routes by remember { mutableStateOf(state.routes) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack, enabled = !state.saving) { Text("‹ Настройки") }
        Text("Нижняя панель", style = MaterialTheme.typography.headlineSmall)
        Text("От 1 до 5 пунктов. Убирайте, добавляйте и меняйте порядок. Все разделы останутся доступны через «Все разделы» в верхней панели.")
        routes.forEachIndexed { index, route ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(AppDestination.fromRoute(route).title, Modifier.weight(1f))
                TextButton(onClick = { routes = routes.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = !state.saving && index > 0) { Text("↑") }
                TextButton(onClick = { routes = routes.toMutableList().apply { add(index + 1, removeAt(index)) } }, enabled = !state.saving && index < routes.lastIndex) { Text("↓") }
                TextButton(onClick = { routes = routes - route }, enabled = !state.saving && routes.size > 1) { Text("Убрать") }
            }
        }
        HorizontalDivider()
        Text("Добавить", style = MaterialTheme.typography.titleMedium)
        availableNavigation.filter { it !in routes }.forEach { route ->
            FilledTonalButton(onClick = { routes = routes + route }, enabled = !state.saving && routes.size < 5) { Text("+ ${AppDestination.fromRoute(route).title}") }
        }
        if (routes.size == 5) Text("Пять пунктов — максимум. Сначала уберите один, чтобы добавить другой.", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { controller?.save(routes, onBack) }, enabled = !state.saving && routes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (state.saving) "Сохраняем…" else "Сохранить панель") }
        TextButton(onClick = { routes = defaultNavigation }, enabled = !state.saving) { Text("Стандартная панель") }
    }
}
