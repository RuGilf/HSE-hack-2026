package ru.lifety.core.model

import kotlinx.datetime.LocalDate
import ru.lifety.core.time.today

fun personValidationError(person: Person, currentDate: LocalDate = today()): String? = when {
    person.id.isBlank() || person.id.length > 100 -> "Некорректный идентификатор"
    person.fullName.isBlank() -> "Укажите имя человека"
    person.fullName.length > 200 -> "Имя должно быть не длиннее 200 символов"
    listOf(person.interests, person.hobbies, person.giftIdeas).any { it.length > 10_000 } -> "Заметка должна быть не длиннее 10 000 символов"
    (person.birthdayMonth == null) != (person.birthdayDay == null) -> "Укажите и день, и месяц рождения"
    person.birthYear != null && person.birthdayMonth == null -> "Укажите день и месяц рождения"
    person.birthYear != null && person.birthYear !in 1..currentDate.year -> "Проверьте год рождения"
    person.birthdayMonth != null && runCatching {
        LocalDate(person.birthYear ?: 2000, person.birthdayMonth, requireNotNull(person.birthdayDay))
    }.isFailure -> "Такой даты рождения нет"
    person.birthYear != null && LocalDate(person.birthYear, person.birthdayMonth!!, person.birthdayDay!!) > currentDate -> "Дата рождения ещё не наступила"
    else -> null
}
