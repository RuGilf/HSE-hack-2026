package ru.lifety.feature.people

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import ru.lifety.core.model.Person
import ru.lifety.core.model.personValidationError

internal data class BirthdaySelection(val day: Int, val month: Int, val year: Int?)

internal fun birthdayMonthDays(month: Int, year: Int?): Int {
    val first = LocalDate(year ?: 2000, month, 1)
    return (first.plus(DatePeriod(months = 1)).toEpochDays() - first.toEpochDays()).toInt()
}

internal fun clampBirthday(selection: BirthdaySelection): BirthdaySelection =
    selection.copy(day = selection.day.coerceIn(1, birthdayMonthDays(selection.month, selection.year)))

internal fun birthdaySelectionError(selection: BirthdaySelection, today: LocalDate): String? =
    personValidationError(Person("picker", "birthday", selection.month, selection.day, selection.year), today)

@Composable
internal expect fun BirthdayPickerDialog(initial: BirthdaySelection?, onConfirm: (BirthdaySelection) -> Unit, onDismiss: () -> Unit)
