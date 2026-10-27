package ru.lifety.feature.people

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import ru.lifety.core.model.Person
import ru.lifety.core.model.personValidationError
import kotlin.test.*

class PeopleDatesTest {
    private val today = LocalDate(2026, 10, 27)
    private val person = Person("p", "Анна Иванова", 10, 27, 1998, "Дизайн", "Бег", "Книга")

    @Test fun knownAndUnknownYearRoundTripSerialization() {
        listOf(person, person.copy(birthYear = null), Person("p2", "Иван")).forEach {
            assertEquals(it, Json.decodeFromString<Person>(Json.encodeToString(it)))
            assertNull(personValidationError(it, today))
        }
    }
    @Test fun birthdayRecursEveryYearAndDoesNotAppearBeforeBirth() {
        assertEquals(LocalDate(2026, 10, 27), birthdayInYear(person, 2026))
        assertEquals(LocalDate(2027, 10, 27), birthdayInYear(person, 2027))
        assertNull(birthdayInYear(person, 1997))
        assertEquals(listOf(person), birthdaysOnDate(listOf(person), today))
        assertEquals(emptyList(), birthdaysOnDate(listOf(person), LocalDate(2026, 10, 28)))
    }
    @Test fun nextBirthdayIncludesTodayAndRollsAcrossYears() {
        assertEquals(today, nextBirthday(person, today))
        assertEquals(LocalDate(2027, 10, 27), nextBirthday(person, LocalDate(2026, 10, 28)))
        assertEquals(LocalDate(2027, 1, 1), nextBirthday(person.copy(birthdayMonth = 1, birthdayDay = 1), LocalDate(2026, 12, 31)))
    }
    @Test fun leapBirthdayHasExplicitFeb28PolicyWithoutChangingTheOriginalDate() {
        val leap = person.copy(birthdayMonth = 2, birthdayDay = 29, birthYear = 2000)
        assertNull(personValidationError(leap, today))
        assertEquals(LocalDate(2027, 2, 28), birthdayInYear(leap, 2027))
        assertEquals(LocalDate(2028, 2, 29), birthdayInYear(leap, 2028))
        assertEquals(LocalDate(2100, 2, 28), birthdayInYear(leap, 2100))
        assertEquals(LocalDate(2028, 2, 29), nextBirthday(leap, LocalDate(2027, 3, 1)))
        assertEquals(29, leap.birthdayDay)
        assertNull(personValidationError(leap.copy(birthYear = null), today))
    }
    @Test fun noBirthdayProducesNoCalendarEventOrFabricatedAge() {
        val unknown = Person("p", "Без даты")
        assertNull(nextBirthday(unknown, today))
        assertNull(birthdayLabel(unknown))
        assertEquals(emptyList(), birthdaysOnDate(listOf(unknown), today))
        assertEquals("День рождения", birthdayAgeLabel(person.copy(birthYear = null), today))
    }
    @Test fun invalidAndPartialDatesAndFutureBirthAreRejected() {
        listOf(
            person.copy(birthdayMonth = 4, birthdayDay = 31),
            person.copy(birthdayMonth = 13), person.copy(birthdayDay = 0),
            person.copy(birthdayMonth = null), person.copy(birthdayDay = null),
            person.copy(birthYear = 0), person.copy(birthYear = 2027),
            person.copy(birthYear = 2026, birthdayDay = 28),
            person.copy(birthYear = 2001, birthdayMonth = 2, birthdayDay = 29),
            person.copy(birthdayMonth = null, birthdayDay = null),
        ).forEach { assertNotNull(personValidationError(it, today), it.toString()) }
        assertNull(birthdayInYear(person.copy(birthdayMonth = 99), 2026))
    }
    @Test fun blankNameAndOversizedNotesAreRejected() {
        assertNotNull(personValidationError(person.copy(fullName = "  "), today))
        assertNotNull(personValidationError(person.copy(giftIdeas = "x".repeat(10_001)), today))
        assertNull(personValidationError(person.copy(giftIdeas = "x".repeat(10_000)), today))
    }
    @Test fun ageGrammarAndUnknownYearAreDisplayedCorrectly() {
        assertEquals("27 октября 1998", birthdayLabel(person))
        assertEquals("27 октября", birthdayLabel(person.copy(birthYear = null)))
        assertEquals("День рождения · 28 лет", birthdayAgeLabel(person, today))
        assertEquals("День рождения · 21 год", birthdayAgeLabel(person.copy(birthYear = 2005), today))
        assertEquals("День рождения · 22 года", birthdayAgeLabel(person.copy(birthYear = 2004), today))
        assertEquals("День рождения · 11 лет", birthdayAgeLabel(person.copy(birthYear = 2015), today))
    }
}
