package ru.lifety.core.planning

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.*
import ru.lifety.core.model.*
import ru.lifety.app.navigation.*
import kotlin.test.*
import kotlin.time.Instant

class HealthMetricContextTest {
    private val zone = TimeZone.UTC
    private val now = time("2026-10-30T12:00:00Z")
    private fun time(value: String) = Instant.parse(value).toEpochMilliseconds()
    private fun row(id: String, day: String, value: String, source: String = "manual") =
        HealthEntry(id, "water", time("${day}T10:00:00Z"), value, source)

    @Test fun dailyAggregationUsesManualEntriesOrOneAlternativeProviderTotal() {
        val metric = HealthMetric("water", "Вода", "мл", aggregation = MetricAggregation.SUM)
        val rows = listOf(row("a", "2026-10-28", "300"), row("b", "2026-10-28", "200"),
            row("api", "2026-10-28", "2000", "healthkit"), row("c", "2026-10-29", "1500", "healthkit"),
            row("d", "2026-10-29", "1600", "health-connect"), row("future", "2026-11-01", "999"))
        val values = metricDailyValues(metric, rows, now, zone).mapKeys { it.key.toString() }
        assertEquals(mapOf("2026-10-28" to "500.0", "2026-10-29" to "1600.0"), values)
        assertEquals("250.0", metricDailyValues(metric.copy(aggregation = MetricAggregation.AVERAGE), rows, now, zone).values.first())
        assertEquals("200", metricDailyValues(metric.copy(aggregation = MetricAggregation.LATEST), rows, now, zone).values.first())
    }

    @Test fun summaryKeepsSevenDaysAndMonthlyMedianWithoutMissingZeros() {
        val metric = HealthMetric("water", "Вода")
        val rows = listOf(row("old", "2026-01-01", "999"), row("a", "2026-10-24", "0"), row("b", "2026-10-30", "200"))
        val summary = summarizeMetric(metric, rows, now, zone)
        assertEquals(7, summary.last7Days.size)
        assertEquals("0", summary.last7Days.first().value)
        assertNull(summary.last7Days[1].value)
        assertEquals(100.0, summary.months.last().median)
        assertEquals(2, summary.months.last().recordedDays)
        val text = summarizeMetric(metric.copy(kind = MetricKind.TEXT), rows.map { it.copy(value = "private-${it.id}") }, now, zone)
        assertTrue(text.months.isEmpty())
        assertFalse(Json.encodeToString(text).contains("private-old"))
    }

    @Test fun selectedContextExcludesUnselectedDataAndSupportsHealthOnlyDiscussion() {
        val metrics = HealthMetricSnapshot(listOf(HealthMetric("water", "Вода"), HealthMetric("secret", "Секрет")),
            listOf(row("a", "2026-10-30", "500"), HealthEntry("b", "secret", now, "888")))
        val preferences = ChatGptPreferences(includeTasks = false, includeGoals = false, healthMetricIds = setOf("water"))
        val extra = selectedExtraContext(preferences, metrics, emptyList(),
            listOf(Expense("e", 100, "RUB", "private-merchant", "private-category", now, "manual")),
            listOf(Person("p", "private-person")), now, zone)!!
        val snapshot = PlanSnapshot(listOf(Task("t", "private-task", null, null, false)), listOf(Goal("g", "private-goal")), emptyList())
        val draft = prepareChatGptDraft(snapshot, preferences, null, null, true, "Мой вопрос", "request", now, zone, extra)
        assertTrue(draft.prompt.contains("Вода"))
        for (text in listOf("Секрет", "888", "private-merchant", "private-category", "private-person", "private-task", "private-goal", "\"deferredTasks\""))
            assertFalse(draft.prompt.contains(text), text)
        val empty = PlanSnapshot(emptyList(), emptyList(), emptyList())
        assertNotNull(prepareChatGptDraft(empty, preferences, null, null, true, "", "empty", now, zone, extra))
        assertFailsWith<IllegalArgumentException> {
            validateChatGptResponse(ImportedPlan("lifety-plan-v1", "", "", emptyList()), ChatGptWorkspace(preferences), empty, null, now, zone)
        }
    }

    @Test fun financeTotalsStayExactAndProfilesAreStableForSetOrder() {
        assertEquals("18446744073709551614", exactMinorTotal(listOf(Long.MAX_VALUE, Long.MAX_VALUE)))
        assertEquals(profileFingerprint(ChatGptPreferences(healthMetricIds = linkedSetOf("a", "b"))),
            profileFingerprint(ChatGptPreferences(healthMetricIds = linkedSetOf("b", "a"))))
        assertNull(selectedExtraContext(ChatGptPreferences(), HealthMetricSnapshot(), emptyList(), emptyList(), emptyList(), now, zone))
    }

    @Test fun validationAndNavigationHandleInvalidValues() {
        val metric = HealthMetric("n", "Число")
        assertEquals("1.5", normalizedMetricValue(metric, " 1,5 "))
        for (value in listOf("NaN", "Infinity", "1e100", "")) assertFailsWith<IllegalArgumentException> { normalizedMetricValue(metric, value) }
        assertEquals("нет боли", normalizedMetricValue(metric.copy(kind = MetricKind.TEXT), " нет боли "))
        assertEquals("false", normalizedMetricValue(metric.copy(kind = MetricKind.BOOLEAN), "false"))
        assertEquals(defaultNavigation, normalizedNavigation(listOf("invalid")))
        assertEquals(listOf("people", "calendar", "health"), normalizedNavigation(listOf("people", "calendar", "people", "invalid", "health")))
        assertEquals(5, normalizedNavigation(availableNavigation).size)
    }
}
