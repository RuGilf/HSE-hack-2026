package ru.lifety.core.planner

/** Number of intersections of half-open intervals in O(log n), including deeply nested slots. */
internal class IntervalIndex(intervals: List<TimeWindow>) {
    private val starts = intervals.map { it.start }.sorted().toLongArray()
    private val ends = intervals.map { it.end }.sorted().toLongArray()

    fun count(start: Long, end: Long): Int {
        if (end <= start) return 0
        return bound(starts, end, inclusive = false) - bound(ends, start, inclusive = true)
    }

    private fun bound(values: LongArray, target: Long, inclusive: Boolean): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val mid = low + (high - low) / 2
            if (values[mid] < target || inclusive && values[mid] == target) low = mid + 1 else high = mid
        }
        return low
    }
}
