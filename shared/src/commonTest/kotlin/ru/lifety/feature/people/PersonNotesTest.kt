package ru.lifety.feature.people

import kotlinx.datetime.LocalDate
import kotlin.test.*

class PersonNotesTest {
    @Test fun legacyParagraphsAndCommasArePreservedAsItems() {
        assertEquals(listOf("Кино, театр", "https://example.org/gift?q=a,b"),
            personNoteItems(" Кино, театр \nhttps://example.org/gift?q=a,b"))
    }
    @Test fun blankLinesAndWindowsLineEndingsNormalizeWithoutLosingOrder() {
        assertEquals(listOf("Кино", "Бег", "Книга"), personNoteItems("\r\n Кино\r\n \nБег\nКнига\n"))
        assertEquals("Кино\nБег\nКнига", personNoteText(listOf(" Кино ", "", "Бег\r\nКнига")))
        assertEquals(emptyList(), personNoteItems("  \n\n"))
    }
    @Test fun multilinePasteInsertsRowsInPlaceAndRetainsNeighbors() {
        assertEquals(listOf("Кино", "Плавание", "Бег", "Музыка"),
            replacePersonNoteItem(listOf("Кино", "Спорт", "Музыка"), 1, "Плавание\r\nБег"))
    }
    @Test fun listStorageRoundTripsNotesAndLinksThroughExistingStringContract() {
        val items = listOf("Фотография", "Книга: архитектура XX века", "https://example.org/gift#size")
        assertEquals(items, personNoteItems(personNoteText(items)))
        assertEquals("Фотография · Книга: архитектура XX века · https://example.org/gift#size", personNotePreview(personNoteText(items)))
        assertEquals("", personNoteText(emptyList()))
    }
    @Test fun blankNewRowCanBeEditedBeforeSavingAndIsNotStoredAsAnEmptyNote() {
        val rows = listOf("Кино", "")
        assertEquals("Кино", personNoteText(rows))
        assertEquals("Кино\nБег", personNoteText(replacePersonNoteItem(rows, 1, "Бег")))
    }
    @Test fun wheelMonthLengthsRespectKnownAndUnknownYears() {
        assertEquals(29, birthdayMonthDays(2, null))
        assertEquals(28, birthdayMonthDays(2, 2026))
        assertEquals(29, birthdayMonthDays(2, 2028))
        assertEquals(28, birthdayMonthDays(2, 2100))
        assertEquals(30, birthdayMonthDays(4, null))
        assertEquals(31, birthdayMonthDays(1, 1998))
    }
    @Test fun movingWheelFromLongMonthOrLeapYearClampsDay() {
        assertEquals(BirthdaySelection(28, 2, 2026), clampBirthday(BirthdaySelection(31, 2, 2026)))
        assertEquals(BirthdaySelection(29, 2, null), clampBirthday(BirthdaySelection(31, 2, null)))
        assertEquals(BirthdaySelection(30, 4, 2000), clampBirthday(BirthdaySelection(31, 4, 2000)))
    }
    @Test fun unknownYearAllowsFutureMonthButKnownBirthDateCannotBeInFuture() {
        val date = LocalDate(2026, 10, 27)
        assertNull(birthdaySelectionError(BirthdaySelection(31, 12, null), date))
        assertNotNull(birthdaySelectionError(BirthdaySelection(28, 10, 2026), date))
        assertNull(birthdaySelectionError(BirthdaySelection(27, 10, 2026), date))
    }
}
