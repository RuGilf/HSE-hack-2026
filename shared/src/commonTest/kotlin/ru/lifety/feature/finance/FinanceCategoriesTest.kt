package ru.lifety.feature.finance

import ru.lifety.core.model.ExpenseCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceCategoriesTest {
    @Test fun overridesDoNotDuplicateBuiltinsAndCustomIdsRemainStable() {
        val renamed = ExpenseCategory("food", "Моя еда", "#ABCDEF", true)
        val custom = ExpenseCategory("custom:travel", "Путешествия", "#123456")
        val result = resolveExpenseCategories(listOf(renamed, custom))
        assertEquals(9, result.size)
        assertEquals(renamed, result.single { it.id == "food" })
        assertEquals(custom, result.single { it.id == "custom:travel" })
        assertTrue(result.any { it.id == "transport" && !it.archived })
    }

    @Test fun validationRejectsDuplicateActiveNamesAndInvalidColorsButAllowsRenamingSelf() {
        val existing = defaultExpenseCategories()
        assertNotNull(categoryValidationError(ExpenseCategory("new", " транспорт ", "#123456"), existing))
        assertNotNull(categoryValidationError(ExpenseCategory("new", "Поездки", "123456"), existing))
        assertNotNull(categoryValidationError(ExpenseCategory("new", " ", "#123456"), existing))
        assertNull(categoryValidationError(existing.first().copy(name = "Еда", colorHex = "#abcdef"), existing))
        assertNull(categoryValidationError(ExpenseCategory("new", "Поездки", "#123456"), existing))
    }

    @Test fun hidingTheLastVisibleCategoryIsRejectedAndArchiveDoesNotDiscardDefinition() {
        val last = defaultExpenseCategories().first()
        assertNotNull(categoryValidationError(last.copy(archived = true), listOf(last)))
        val result = resolveExpenseCategories(listOf(last.copy(archived = true)))
        assertEquals(last.name, result.single { it.id == last.id }.name)
        assertTrue(result.single { it.id == last.id }.archived)
    }

    @Test fun legacyCategoryIdsRemainAvailableForEditingWithoutReclassifyingExpenses() {
        val result = resolveExpenseCategories(emptyList(), listOf("travel", "travel", "food"))
        assertEquals(9, result.size)
        assertEquals("travel", result.single { it.id == "travel" }.name)
        assertEquals(defaultExpenseCategories().last().colorHex, result.single { it.id == "travel" }.colorHex)
    }
}
