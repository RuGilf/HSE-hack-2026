package ru.lifety.core.di

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import ru.lifety.core.repository.Repositories
import ru.lifety.database.SqlDelightExpenseRepository
import ru.lifety.database.SqlDelightHealthRepository
import ru.lifety.database.SqlDelightTaskRepository
import ru.lifety.database.SqlDelightExpenseCategoryRepository
import ru.lifety.database.SqlDelightPersonRepository
import ru.lifety.database.SqlDelightGoalRepository
import ru.lifety.database.SqlDelightPlanningRepository
import ru.lifety.database.SqlDelightChatGptExchangeRepository
import ru.lifety.database.createDatabase

/** One owner for the database connection and all repositories. Construct off the UI thread. */
class AppContainer(
    private val driver: SqlDriver,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val database = createDatabase(driver)
    val repositories = Repositories(
        tasks = SqlDelightTaskRepository(database, dispatcher),
        expenses = SqlDelightExpenseRepository(database, dispatcher),
        health = SqlDelightHealthRepository(database, dispatcher),
        expenseCategories = SqlDelightExpenseCategoryRepository(database, dispatcher),
        people = SqlDelightPersonRepository(database, dispatcher),
        goals = SqlDelightGoalRepository(database, dispatcher),
        planning = SqlDelightPlanningRepository(database, dispatcher),
        chatGptExchange = SqlDelightChatGptExchangeRepository(database, dispatcher),
    )

    override fun close() { driver.close() }
}
