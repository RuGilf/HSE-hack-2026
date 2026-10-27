package ru.lifety.feature.people

import kotlinx.datetime.LocalDate
import ru.lifety.core.model.Person

private val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

/** Annual local calendar occurrence. Feb 29 is observed on Feb 28 in non-leap years. */
fun birthdayInYear(person: Person, year: Int): LocalDate? {
    val month = person.birthdayMonth ?: return null
    val day = person.birthdayDay ?: return null
    if (person.birthYear != null && year < person.birthYear) return null
    // Also reject corrupt/invalid month-day pairs instead of inventing a date.
    if (runCatching { LocalDate(person.birthYear ?: 2000, month, day) }.isFailure) return null
    return runCatching { LocalDate(year, month, day) }.getOrElse {
        if (month == 2 && day == 29) LocalDate(year, 2, 28) else return null
    }
}

fun nextBirthday(person: Person, from: LocalDate): LocalDate? {
    val year = maxOf(from.year, person.birthYear ?: from.year)
    val date = birthdayInYear(person, year) ?: return null
    return if (date >= from) date else birthdayInYear(person, year + 1)
}

fun birthdayLabel(person: Person): String? {
    val month = person.birthdayMonth ?: return null
    val day = person.birthdayDay ?: return null
    if (month !in 1..12) return null
    return "$day ${months[month - 1]}" + (person.birthYear?.let { " $it" } ?: "")
}

fun birthdayAgeLabel(person: Person, date: LocalDate): String {
    val age = person.birthYear?.let { date.year - it }?.takeIf { it > 0 } ?: return "День рождения"
    val word = when {
        age % 100 in 11..14 -> "лет"
        age % 10 == 1 -> "год"
        age % 10 in 2..4 -> "года"
        else -> "лет"
    }
    return "День рождения · $age $word"
}

fun birthdaysOnDate(people: List<Person>, date: LocalDate): List<Person> = people
    .filter { birthdayInYear(it, date.year) == date }
    .sortedWith(compareBy<Person> { it.fullName.lowercase() }.thenBy { it.id })
