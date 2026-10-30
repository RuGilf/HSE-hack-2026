package ru.lifety.core.repository

/** One shared set of repository instances, supplied by the composition root. */
data class Repositories(
    val tasks: TaskRepository,
    val expenses: ExpenseRepository,
    val health: HealthRepository,
    val expenseCategories: ExpenseCategoryRepository? = null,
    val people: PersonRepository? = null,
    val goals: GoalRepository? = null,
    val planning: PlanningRepository? = null,
    val chatGptExchange: ChatGptExchangeRepository? = null,
    val healthMetrics: HealthMetricsRepository? = null,
    val navigation: NavigationPreferencesRepository? = null,
    val schedule: ScheduleRepository? = null,
)
