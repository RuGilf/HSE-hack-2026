package ru.lifety.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ru.lifety.database.generated.LifetyDatabase

actual class DatabaseDriverFactory(context: Context) {
    private val applicationContext = context.applicationContext

    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = LifetyDatabase.Schema,
        context = applicationContext,
        name = "lifety.db",
    )
}
