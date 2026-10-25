package ru.lifety.app.navigation

/** Stable route identifiers; titles may be localized without changing routes. */
enum class AppDestination(val route: String, val title: String) {
    DASHBOARD("dashboard", "Обзор"),
    TASKS("tasks", "Задачи"),
    CALENDAR("calendar", "Календарь"),
    FINANCE("finance", "Финансы"),
    ASSISTANT("assistant", "Ассистент"),
    HEALTH("health", "Здоровье");

    companion object {
        fun fromRoute(route: String): AppDestination = entries.firstOrNull { it.route == route } ?: DASHBOARD
    }
}
