package ru.lifety.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationContractTest {
    @Test
    fun taskWireFormatPreservesNullableTimesAndUnicode() {
        val input = """{"id":"task-1","title":"Учёба 🗓","scheduledAt":null,"deadlineAt":1792886400000,"isCompleted":false}"""
        val task = Json.decodeFromString<Task>(input)
        assertEquals(Task("task-1", "Учёба 🗓", null, 1792886400000L, false), task)
        assertEquals(Json.parseToJsonElement(input), Json.parseToJsonElement(Json.encodeToString(task)))
    }

    @Test
    fun expenseMinorUnitsAreNeverRoundedThroughFloatingPoint() {
        val expense = Expense("expense-1", 9_007_199_254_740_993L, "RUB", "Магазин", "other", 1792886400000, "manual")
        val encoded = Json.encodeToString(expense)
        assertEquals(expense, Json.decodeFromString<Expense>(encoded))
        assertEquals("9007199254740993", Json.parseToJsonElement(encoded).jsonObject.getValue("amountMinor").toString())
    }

    @Test
    fun missingHealthMeasurementsRemainNullRatherThanZero() {
        val snapshot = Json.decodeFromString<HealthSnapshot>("""{"timestamp":1792886400000,"sleepMinutes":null,"steps":null}""")
        assertEquals(HealthSnapshot(1792886400000L, null, null), snapshot)
        assertEquals(snapshot, Json.decodeFromString<HealthSnapshot>(Json.encodeToString(snapshot)))
    }
}
