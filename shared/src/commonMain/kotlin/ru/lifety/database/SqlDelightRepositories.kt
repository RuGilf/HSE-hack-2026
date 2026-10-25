package ru.lifety.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.lifety.core.model.Expense
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.model.Task
import ru.lifety.core.repository.ExpenseRepository
import ru.lifety.core.repository.HealthRepository
import ru.lifety.core.repository.TaskRepository
import ru.lifety.database.generated.LifetyDatabase

class SqlDelightTaskRepository(
    database: LifetyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TaskRepository {
    private val queries = database.lifetyQueries

    override fun observeAll(): Flow<List<Task>> = queries.selectTasks(::Task).asFlow().mapToList(dispatcher)

    override suspend fun upsert(task: Task) = withContext(dispatcher) {
        queries.upsertTask(task.id, task.title, task.scheduledAt, task.deadlineAt, task.isCompleted)
        Unit
    }

    override suspend fun delete(id: String) = withContext(dispatcher) { queries.deleteTask(id); Unit }
}

class SqlDelightExpenseRepository(
    database: LifetyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExpenseRepository {
    private val queries = database.lifetyQueries

    override fun observeAll(): Flow<List<Expense>> = queries.selectExpenses(::Expense).asFlow().mapToList(dispatcher)

    override suspend fun upsert(expense: Expense) = withContext(dispatcher) {
        queries.upsertExpense(expense.id, expense.amountMinor, expense.currency, expense.merchant,
            expense.category, expense.timestamp, expense.source)
        Unit
    }

    override suspend fun delete(id: String) = withContext(dispatcher) { queries.deleteExpense(id); Unit }
}

class SqlDelightHealthRepository(
    database: LifetyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HealthRepository {
    private val queries = database.lifetyQueries

    override fun observeLatest(): Flow<HealthSnapshot?> = queries.selectLatestHealth(::HealthSnapshot)
        .asFlow().mapToOneOrNull(dispatcher)

    override fun observeAll(): Flow<List<HealthSnapshot>> = queries.selectHealthHistory(::HealthSnapshot)
        .asFlow().mapToList(dispatcher)

    override suspend fun update(snapshot: HealthSnapshot) = withContext(dispatcher) {
        queries.upsertHealth(snapshot.timestamp, snapshot.sleepMinutes, snapshot.steps)
        Unit
    }

    override suspend fun updateAll(snapshots: List<HealthSnapshot>) = withContext(dispatcher) {
        queries.transaction {
            snapshots.forEach { queries.upsertHealth(it.timestamp, it.sleepMinutes, it.steps) }
        }
    }
}
