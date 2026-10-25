package ru.lifety.database

import app.cash.sqldelight.db.SqlDriver
import ru.lifety.database.generated.LifetyDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Retain the driver and close it when the application container is disposed. */
fun createDatabase(driver: SqlDriver): LifetyDatabase = LifetyDatabase(driver)
