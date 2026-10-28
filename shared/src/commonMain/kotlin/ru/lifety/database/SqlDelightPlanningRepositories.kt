package ru.lifety.database

import app.cash.sqldelight.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.Goal
import ru.lifety.core.model.Task
import ru.lifety.core.model.TaskPlan
import ru.lifety.core.model.PlanReport
import ru.lifety.core.repository.*
import ru.lifety.core.planning.*
import ru.lifety.database.generated.LifetyDatabase
import kotlin.time.Instant

class SqlDelightGoalRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : GoalRepository {
    private val queries = database.lifetyQueries
    override fun observeAll(): Flow<List<Goal>> = queries.selectGoals(::Goal).asFlow().mapToList(dispatcher)
    override suspend fun upsert(goal: Goal) = withContext(dispatcher) {
        require(goal.id.isNotBlank() && goal.title.isNotBlank() && goal.title.length <= 200 && goal.description.length <= 6000)
        queries.upsertGoal(goal.id, goal.title.trim(), goal.description.trim(), goal.targetAt, goal.isCompleted)
        Unit
    }
    override suspend fun delete(id: String) = withContext(dispatcher) {
        queries.transaction { queries.clearGoalLinks(id); queries.deleteGoal(id) }
    }
}

class SqlDelightPlanningRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : PlanningRepository {
    private val queries = database.lifetyQueries
    override fun observeAll(): Flow<List<TaskPlan>> = queries.selectTaskPlans(::TaskPlan).asFlow().mapToList(dispatcher)
    override fun observeReport(): Flow<PlanReport?> = queries.selectPlanReport(::PlanReport).asFlow().mapToOneOrNull(dispatcher)
    private fun readSnapshot() = PlanSnapshot(queries.selectTasks(::Task).executeAsList(), queries.selectGoals(::Goal).executeAsList(), queries.selectTaskPlans(::TaskPlan).executeAsList())
    override suspend fun snapshot(): PlanSnapshot = withContext(dispatcher) { queries.transactionWithResult { readSnapshot() } }
    override suspend fun apply(plan: ImportedPlan, now: Long) = withContext(dispatcher) {
        queries.transaction {
            val snapshot = readSnapshot()
            validateImportedPlan(plan, snapshot, now)
            val tasks = snapshot.tasks.associateBy { it.id }
            plan.tasks.forEach { item ->
                val task = tasks.getValue(item.taskId)
                queries.upsertTask(task.id, task.title, Instant.parse(item.scheduledAt).toEpochMilliseconds(), task.deadlineAt, task.isCompleted)
                queries.upsertTaskPlan(task.id, item.durationMinutes, item.approach.trim(), item.goalId)
            }
            queries.upsertPlanReport(plan.summary.trim(), now)
        }
    }
}
