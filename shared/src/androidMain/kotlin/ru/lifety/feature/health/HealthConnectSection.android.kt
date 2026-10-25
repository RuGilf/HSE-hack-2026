package ru.lifety.feature.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.lifety.core.repository.HealthRepository
import ru.lifety.core.ui.LifetySpacing

@Composable
internal actual fun HealthConnectSection(repository: HealthRepository?, enabled: Boolean, onBusyChanged: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < 28) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(LifetySpacing.Content), verticalArrangement = Arrangement.spacedBy(LifetySpacing.Small)) {
                Text("Health Connect", style = MaterialTheme.typography.titleLarge)
                Text("На этом устройстве импорт недоступен. Для подключения требуется Android 9 или новее. Показатели можно добавить вручную.")
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(repository, context, scope) {
        HealthConnectController(AndroidHealthConnectGateway(context), repository, scope)
    }
    val state by controller.state.collectAsState()
    var requesting by remember { mutableStateOf(false) }
    val onBusy by rememberUpdatedState(onBusyChanged)
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        requesting = false
        controller.refresh() // Check the real permission state, including partial grants and revocation.
    }
    val lifecycle = context.componentActivity()?.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.refresh() }
        lifecycle?.addObserver(observer)
        controller.refresh()
        onDispose { lifecycle?.removeObserver(observer); onBusy(false) }
    }
    LaunchedEffect(state.busy, requesting) { onBusy(state.busy || requesting) }

    val active = enabled && !state.busy && !requesting
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(LifetySpacing.Content), verticalArrangement = Arrangement.spacedBy(LifetySpacing.Item)) {
            Text("Связь с Health Connect", style = MaterialTheme.typography.titleLarge)
            Text("Получайте сон и шаги из приложений, которые делятся ими через Health Connect. Чтение начинается только по кнопке, сохранение — после предпросмотра.")
            when (state.availability) {
                null -> Text("Проверяем доступность…")
                HealthConnectAvailability.UNAVAILABLE -> {
                    Text("Health Connect недоступен на этом устройстве или в текущем профиле. Ручной ввод работает без него.")
                    TextButton(onClick = controller::refresh, enabled = active) { Text("Проверить снова") }
                }
                HealthConnectAvailability.INSTALL_REQUIRED -> {
                    Text("Установите или обновите Health Connect, затем вернитесь в Lifety.")
                    Button(onClick = { openProviderStore(context, controller) }, enabled = active) { Text("Установить / обновить") }
                    TextButton(onClick = controller::refresh, enabled = active) { Text("Проверить снова") }
                }
                HealthConnectAvailability.AVAILABLE -> {
                    HealthMetric.entries.forEach { metric ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LifetySpacing.Small)) {
                            Column(Modifier.weight(1f)) {
                                Text(metric.label, style = MaterialTheme.typography.titleMedium)
                                Text(if (metric in state.granted) "Чтение разрешено" else "Доступ не выдан", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = metric in state.selected, onCheckedChange = { controller.select(metric, it) }, enabled = active,
                                modifier = Modifier.semantics { contentDescription = "Импорт: ${metric.label}" })
                        }
                    }
                    FilledTonalButton(onClick = {
                        try { requesting = true; launcher.launch(healthPermissions(state.selected)) }
                        catch (_: Exception) { requesting = false; controller.showError("Не удалось открыть запрос разрешений. Откройте настройки Health Connect.") }
                    }, enabled = active && state.selected.isNotEmpty()) { Text("Настроить разрешения") }
                    Button(onClick = { controller.readHistoryPreview() },
                        enabled = active && state.selected.intersect(state.granted).isNotEmpty()) { Text("Прочитать за 30 дней") }
                    Text("Сон и шаги — по календарным дням за последние 30 дней. Читаются только выбранные и разрешённые показатели. Доступная глубина зависит от разрешений Health Connect; более старые локальные записи сохраняются.", style = MaterialTheme.typography.bodySmall)
                    Text("Если записей нет, включите передачу сна и шагов в Health Connect в совместимом приложении часов или фитнеса. Синхронизируйте устройство и повторите чтение.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        openHealthIntent(context, Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS), controller)
                    }, enabled = active) { Text("Открыть Health Connect") }
                }
            }
            if (state.busy || requesting) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = controller::refresh, enabled = active) { Text("Обновить подключение") }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            state.preview?.let { preview ->
                HorizontalDivider()
                Text(if (preview.periodDays != null) "Импорт: ${preview.snapshots.size} дней" else "Проверить перед сохранением", style = MaterialTheme.typography.titleMedium)
                if (preview.periodDays != null) {
                    Text("${healthRecordedAt(preview.snapshots.first().timestamp)} — ${healthRecordedAt(preview.snapshots.last().timestamp)}", style = MaterialTheme.typography.bodySmall)
                    Text("Сон: ${preview.snapshots.count { it.sleepMinutes != null }} дней · Шаги: ${preview.snapshots.count { it.steps != null }} дней")
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        preview.snapshots.asReversed().forEach { row ->
                            Text("${healthRecordedAt(row.timestamp)}\nСон: ${sleepLabel(row.sleepMinutes)} · Шаги: ${row.steps?.toString() ?: "Нет данных"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (preview.periodDays == null) {
                    Text("Сон: ${preview.snapshot.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "нет данных / нет доступа"}")
                    Text("Шаги: ${preview.snapshot.steps?.toString() ?: "нет данных / нет доступа"}")
                    Text("Получено: ${healthRecordedAt(preview.snapshot.timestamp)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (preview.periodDays != null) "Записи дополнят локальную историю. Повторный импорт завершённых дней обновит те же дни. Пустые значения не удаляют сохранённые показатели." else "Эта запись заменит последние отображаемые показатели. Пустое значение станет «неизвестно»; старые значения не подмешиваются.")
                Button(onClick = controller::savePreview, enabled = active) { Text(if (preview.periodDays != null) "Сохранить историю" else "Сохранить этот снимок") }
                TextButton(onClick = controller::discardPreview, enabled = active) { Text("Отменить импорт") }
            }
            Text(HEALTH_CONNECT_PRIVACY, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.componentActivity() else null
    else -> null
}

private fun openProviderStore(context: Context, controller: HealthConnectController) {
    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) {
        openHealthIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")), controller)
    }
    catch (_: Exception) { controller.showError("Не удалось открыть магазин. Установите Health Connect через Google Play.") }
}

private fun openHealthIntent(context: Context, intent: Intent, controller: HealthConnectController) {
    try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { controller.showError("Не удалось открыть Health Connect. Проверьте его установку в настройках Android.") }
}

internal const val HEALTH_CONNECT_PRIVACY = "Lifety запрашивает только чтение сна и шагов. Записей в Health Connect и фонового импорта нет. Сохранённый снимок остаётся в локальной базе Lifety. Отправка в ИИ отдельно выключена по умолчанию и возможна только после вашего разрешения и ручного анализа."
