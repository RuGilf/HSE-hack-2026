package ru.lifety.feature.dashboard

import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.lifety.core.model.*
import ru.lifety.core.repository.Repositories

data class DashboardPeriod(val dayStart: Long, val dayEnd: Long, val monthStart: Long, val monthEnd: Long, val label: String)
internal expect fun dashboardPeriod(now: Long): DashboardPeriod
internal expect fun dashboardDate(timestamp: Long): String

data class CurrencySpending(val currency: String, val amountMinor: Long?)
data class DashboardSummary(
    val today: List<Task> = emptyList(),
    val completed: Int = 0,
    val remaining: Int = 0,
    val overdue: Int = 0,
    val deadlines: List<Task> = emptyList(),
    val spending: List<CurrencySpending> = emptyList(),
    val expenseCount: Int = 0,
    val health: HealthSnapshot? = null,
)

internal fun summarizeDashboard(tasks: List<Task>, expenses: List<Expense>, health: HealthSnapshot?, now: Long, period: DashboardPeriod): DashboardSummary {
    val today = tasks.filter { task ->
        task.scheduledAt?.let { it >= period.dayStart && it < period.dayEnd } == true ||
            task.deadlineAt?.let { it >= period.dayStart && it < period.dayEnd } == true
    }.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.scheduledAt ?: it.deadlineAt ?: Long.MAX_VALUE }.thenBy { it.id })
    val currentExpenses = expenses.filter { it.timestamp >= period.monthStart && it.timestamp < period.monthEnd && it.timestamp <= now }
    return DashboardSummary(
        today = today,
        completed = today.count { it.isCompleted },
        remaining = today.count { !it.isCompleted },
        overdue = tasks.count { !it.isCompleted && it.deadlineAt?.let { deadline -> deadline < now } == true },
        deadlines = tasks.filter { !it.isCompleted && it.deadlineAt?.let { deadline -> deadline >= now } == true }
            .sortedWith(compareBy<Task> { it.deadlineAt }.thenBy { it.id }).take(5),
        spending = currentExpenses.groupBy { it.currency }.entries.sortedBy { it.key }.map { (currency, entries) ->
            val total = runCatching {
                entries.fold(0L) { sum, expense ->
                    val value = expense.amountMinor
                    require(!((value > 0 && sum > Long.MAX_VALUE - value) || (value < 0 && sum < Long.MIN_VALUE - value)))
                    sum + value
                }
            }.getOrNull()
            CurrencySpending(currency, total)
        },
        expenseCount = currentExpenses.size,
        health = health,
    )
}

/** Integer formatting preserves money above 2^53 and even Long.MIN_VALUE. */
internal fun formatMinor(amount: Long, currency: String): String {
    val scale = when (currency) {
        "JPY", "KRW", "VND", "CLP", "ISK", "BIF", "DJF", "GNF", "KMF", "PYG", "RWF", "UGX", "VUV", "XAF", "XOF", "XPF" -> 0
        "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
        "CLF", "UYW" -> 4
        else -> 2
    }
    val digits = amount.toString().removePrefix("-").padStart(scale + 1, '0')
    val number = if (scale == 0) digits else digits.dropLast(scale) + "," + digits.takeLast(scale)
    return (if (amount < 0) "−" else "") + number + " " + currency
}

data class DashboardUiState(val loading: Boolean = true, val summary: DashboardSummary = DashboardSummary(), val date: String = "", val error: String? = null, val allTasks: List<Task> = emptyList())

class DashboardController(repositories: Repositories?, scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(DashboardUiState(loading = repositories != null))
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()
    init {
        if (repositories != null) scope.launch {
            try {
                val clock = flow { while (true) { emit(Clock.System.now().toEpochMilliseconds()); delay(30_000) } }
                combine(repositories.tasks.observeAll(), repositories.expenses.observeAll(), repositories.health.observeLatest(), clock) { tasks, expenses, health, now ->
                    val period = dashboardPeriod(now)
                    DashboardUiState(false, summarizeDashboard(tasks, expenses, health, now, period), period.label, allTasks = tasks)
                }.flowOn(Dispatchers.Default).collect { mutableState.value = it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, error = "Не удалось загрузить обзор. Повторите попытку.") }
        }
    }
}
