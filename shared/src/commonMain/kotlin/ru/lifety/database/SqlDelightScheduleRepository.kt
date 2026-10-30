package ru.lifety.database

import app.cash.sqldelight.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.ScheduleBlock
import ru.lifety.core.planner.scheduleError
import ru.lifety.core.repository.ScheduleRepository
import ru.lifety.database.generated.LifetyDatabase

internal fun scheduleBlock(id: String, title: String, startMinute: Long, endMinute: Long, weekdays: Long, date: String?) =
    ScheduleBlock(id, title, startMinute.toInt(), endMinute.toInt(), (1..7).filter { weekdays and (1L shl (it - 1)) != 0L }.toSet(), date)

class SqlDelightScheduleRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ScheduleRepository {
    private val queries = database.lifetyQueries
    override fun observeAll(): Flow<List<ScheduleBlock>> = queries.selectScheduleBlocks(::scheduleBlock).asFlow().mapToList(dispatcher)
    override suspend fun upsert(block: ScheduleBlock) = withContext(dispatcher) {
        require(scheduleError(block) == null) { scheduleError(block).orEmpty() }
        queries.upsertScheduleBlock(block.id, block.title.trim(), block.startMinute.toLong(), block.endMinute.toLong(),
            block.weekdays.fold(0L) { mask, day -> mask or (1L shl (day - 1)) }, block.date)
        Unit
    }
    override suspend fun delete(id: String) = withContext(dispatcher) { queries.deleteScheduleBlock(id); Unit }
}
