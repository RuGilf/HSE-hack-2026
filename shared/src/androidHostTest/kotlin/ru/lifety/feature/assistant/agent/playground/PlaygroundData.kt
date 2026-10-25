package ru.lifety.feature.assistant.agent.playground

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lifety.core.model.*
import ru.lifety.core.repository.*

/** Isolated demo repositories; never connected to AppContainer or lifety.db. */
class PlaygroundData(val now: Long = System.currentTimeMillis()) {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private fun day(offset: Int, hour: Int = 12) = today.plusDays(offset.toLong()).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    val expenses = MutableStateFlow<List<Expense>>(emptyList())
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    val health = MutableStateFlow<HealthSnapshot?>(null)
    init { reset() }

    fun reset() {
        val stores = listOf(
            Triple("Пятёрочка", "food", 143550L), Triple("Кофейня", "food", 39000L),
            Triple("Метро", "transport", 6700L), Triple("Ozon", "shopping", 249000L),
            Triple("Аптека", "health", 78600L), Triple("Самокат", "food", 128000L),
            Triple("Кино", "entertainment", 65000L), Triple("Такси", "transport", 59000L),
        )
        expenses.value = (0..27).map { i ->
            val store = stores[i % stores.size]
            Expense("demo-expense-$i", store.third + (i % 3) * 1200L, "RUB", "[ДЕМО] ${store.first}", store.second,
                day(-i, 9 + i % 10), "synthetic-playground")
        } + listOf(Expense("demo-usd", 1299L, "USD", "[ДЕМО] Облачный сервис", "subscriptions", day(-3), "synthetic-playground"))
        tasks.value = listOf(
            Task("demo-task-1", "[ДЕМО] Подготовить презентацию", day(0, 16), day(1, 18), false),
            Task("demo-task-2", "[ДЕМО] Разобрать конспект по математике", day(0, 19), day(2, 20), false),
            Task("demo-task-3", "[ДЕМО] Записаться к стоматологу", null, day(3), false),
            Task("demo-task-4", "[ДЕМО] Прогулка 30 минут", day(1, 9), null, false),
            Task("demo-task-5", "[ДЕМО] Оплатить интернет", null, day(5), false),
            Task("demo-task-6", "[ДЕМО] Купить продукты", day(-1, 18), null, true),
        )
        health.value = HealthSnapshot(day(0, 8), 410, 7850)
    }

    val repositories = Repositories(
        object : TaskRepository {
            override fun observeAll() = tasks
            override suspend fun upsert(task: Task) { tasks.value = tasks.value.filterNot { it.id == task.id } + task }
            override suspend fun delete(id: String) { tasks.value = tasks.value.filterNot { it.id == id } }
        },
        object : ExpenseRepository {
            override fun observeAll() = expenses
            override suspend fun upsert(expense: Expense) { expenses.value = expenses.value.filterNot { it.id == expense.id } + expense.copy(source = "synthetic-playground") }
            override suspend fun delete(id: String) { expenses.value = expenses.value.filterNot { it.id == id } }
        },
        object : HealthRepository {
            override fun observeLatest() = health
            override suspend fun update(snapshot: HealthSnapshot) { health.value = snapshot }
        },
    )

    val guide = """# Синтетический профиль песочницы
Все стартовые записи вымышлены. Это изолированный тестовый набор, не реальные сведения о пользователе.
Дата набора: $today. Часовой пояс: Europe/Moscow. Timestamp: Unix milliseconds.
Демо-профиль: Алекс, студент, 22 года. Хочет понимать траты и лучше планировать учебную неделю.
Существующие расходы и задачи находятся в data/, обновляются через обычные репозитории.
Общий HealthRepository даёт только последний снимок; историю он не предоставляет.
Дополнительно вручную импортирован синтетический CSV: scratch/demo-health-history.csv (14 дней).
Для исследования сна/шагов можно читать этот файл. Он не является синхронизацией Google Health.
Долгосрочная память сначала пуста: новые предпочтения записываются после подтверждения предложения.
"""
    val healthCsv: String get() = "date,sleepMinutes,steps,source\n" + (13 downTo 0).joinToString("\n") { i ->
        "${today.minusDays(i.toLong())},${listOf(390, 430, 460, 370, 480, 440, 410)[i % 7]},${listOf(4200, 8200, 11400, 3700, 9400, 6800, 7850)[i % 7]},synthetic-playground"
    }
}
