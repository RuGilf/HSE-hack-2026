package ru.lifety.database

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import ru.lifety.database.generated.LifetyDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

private val intAdapter = object : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int {
        require(databaseValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "SQLite integer exceeds Int range" }
        return databaseValue.toInt()
    }
    override fun encode(value: Int): Long = value.toLong()
}

/** Retain the driver and close it when the application container is disposed. */
fun createDatabase(driver: SqlDriver): LifetyDatabase = LifetyDatabase(
    driver,
    healthSnapshotAdapter = HealthSnapshot.Adapter(intAdapter, intAdapter),
    personAdapter = Person.Adapter(intAdapter, intAdapter, intAdapter),
)
