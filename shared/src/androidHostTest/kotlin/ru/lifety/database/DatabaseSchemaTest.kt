package ru.lifety.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseSchemaTest {
    @Test
    fun generatedSchemaCreatesAllTablesAndPreservesModelStorageTypes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            LifetyDatabase.Schema.create(driver)
            createDatabase(driver)
            val tables = driver.executeQuery(null,
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += requireNotNull(cursor.getString(0))
                    QueryResult.Value(names)
                }, 0,
            ).value
            assertEquals(listOf("expense", "expenseCategory", "goal", "healthSnapshot", "person", "planReport", "task", "taskPlan"), tables)

            driver.execute(null, "INSERT INTO task VALUES ('t1', 'Task', NULL, NULL, 0)", 0)
            driver.execute(null, "INSERT INTO expense VALUES ('e1', 9007199254740993, 'RUB', 'Shop', 'other', 1792886400000, 'manual')", 0)
            driver.execute(null, "INSERT INTO healthSnapshot VALUES (1792886400000, NULL, NULL)", 0)
            val amount = driver.executeQuery(null, "SELECT amountMinor FROM expense",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0))
                }, 0,
            ).value
            assertEquals(9_007_199_254_740_993L, amount)
        } finally {
            driver.close()
        }
    }
}
