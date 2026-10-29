package ru.lifety.core.model

import kotlinx.serialization.Serializable

@Serializable enum class MetricKind { NUMBER, TEXT, BOOLEAN }
@Serializable enum class MetricAggregation { LATEST, SUM, AVERAGE }
@Serializable data class HealthMetric(val id: String, val name: String, val unit: String = "", val kind: MetricKind = MetricKind.NUMBER,
    val aggregation: MetricAggregation = MetricAggregation.LATEST, val sourceKey: String? = null, val archived: Boolean = false)
@Serializable data class HealthEntry(val id: String, val metricId: String, val timestamp: Long, val value: String, val source: String = "manual")
@Serializable data class HealthMetricSnapshot(val metrics: List<HealthMetric> = emptyList(), val entries: List<HealthEntry> = emptyList())
data class HealthApiSample(val key: String, val timestamp: Long, val value: Double)

val healthApiMetrics = listOf(
    HealthMetric("device-weight", "Вес", "кг", sourceKey = "weight"),
    HealthMetric("device-resting-heart-rate", "Пульс в покое", "уд/мин", sourceKey = "resting-heart-rate"),
    HealthMetric("device-water", "Вода", "мл", aggregation = MetricAggregation.SUM, sourceKey = "water"),
    HealthMetric("device-active-energy", "Активная энергия", "ккал", aggregation = MetricAggregation.SUM, sourceKey = "active-energy"),
)

fun normalizedMetricValue(metric: HealthMetric, value: String): String {
    val text = value.trim()
    return when (metric.kind) {
        MetricKind.NUMBER -> {
            require(text.length <= 80)
            val number = text.replace(',', '.').toDoubleOrNull()
            require(number != null && number.isFinite() && kotlin.math.abs(number) <= 1e12) { "Введите число без единиц измерения" }
            number.toString()
        }
        MetricKind.TEXT -> { require(text.isNotEmpty() && text.length <= 4000) { "Добавьте заметку до 4000 символов" }; text }
        MetricKind.BOOLEAN -> { require(text == "true" || text == "false"); text }
    }
}
