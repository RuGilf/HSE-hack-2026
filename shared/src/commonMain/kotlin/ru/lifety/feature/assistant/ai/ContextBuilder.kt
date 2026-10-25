package ru.lifety.feature.assistant.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.lifety.core.repository.Repositories

@Serializable
data class ContextPermissions(
    val tasks: Boolean = false,
    val finances: Boolean = false,
    val health: Boolean = false,
) {
    val any: Boolean get() = tasks || finances || health
}

@Serializable
data class TaskContext(
    val ref: String,
    val title: String,
    val completed: Boolean,
    val scheduledAt: Long?,
    val deadlineAt: Long?,
)

@Serializable
data class TasksContext(val total: Int, val completed: Int, val omitted: Int, val items: List<TaskContext>)

@Serializable
data class SpendingContext(val currency: String, val amountMinor: Long, val count: Int)

@Serializable
data class FinanceContext(val since: Long, val until: Long, val spending: List<SpendingContext>)

@Serializable
data class HealthContext(val recordedAt: Long, val sleepMinutes: Int?, val steps: Int?)

@Serializable
data class AssistantContext(
    val capturedAt: Long,
    val timeUnit: String = "UTC epoch milliseconds",
    val tasks: TasksContext? = null,
    val finances: FinanceContext? = null,
    val health: HealthContext? = null,
    val localTime: String? = null,
)

internal expect fun assistantLocalTime(now: Long): String

/** No subscription at all to a denied repository. Nothing here performs network I/O. */
class ContextBuilder(private val repositories: Repositories) {
    suspend fun build(permissions: ContextPermissions, now: Long): AssistantContext = withTimeout(10_000) {
        val tasks = if (permissions.tasks) repositories.tasks.observeAll().first() else null
        val expenses = if (permissions.finances) repositories.expenses.observeAll().first() else null
        val health = if (permissions.health) repositories.health.observeLatest().first() else null
        val since = now - 30L * 24 * 60 * 60 * 1_000
        AssistantContext(
            capturedAt = now,
            localTime = assistantLocalTime(now),
            tasks = tasks?.let { all ->
                val selected = all.sortedWith(
                    compareBy<ru.lifety.core.model.Task> { it.isCompleted }
                        .thenBy { it.deadlineAt ?: it.scheduledAt ?: Long.MAX_VALUE }
                        .thenBy { it.id },
                ).take(40)
                TasksContext(all.size, all.count { it.isCompleted }, all.size - selected.size,
                    selected.mapIndexed { index, task ->
                        TaskContext("T${index + 1}", task.title.take(100), task.isCompleted, task.scheduledAt, task.deadlineAt)
                    })
            },
            finances = expenses?.let { all ->
                val groups = all.filter { it.timestamp in since..now }.groupBy { it.currency }
                require(groups.size <= 20) { "Too many currencies" }
                FinanceContext(since, now, groups.entries.sortedBy { it.key }.map { (currency, items) ->
                    SpendingContext(currency.take(8), checkedTotal(items.map { it.amountMinor }), items.size)
                })
            },
            health = health?.let { HealthContext(it.timestamp, it.sleepMinutes, it.steps) },
        )
    }
}

internal fun checkedTotal(values: List<Long>): Long = values.fold(0L) { sum, value ->
    require(!((value > 0 && sum > Long.MAX_VALUE - value) || (value < 0 && sum < Long.MIN_VALUE - value)))
    sum + value
}

/** Null sections are omitted: a disabled category is absent from the actual payload. */
val contextJson = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = true }
fun AssistantContext.previewJson(): String = contextJson.encodeToString(this)
