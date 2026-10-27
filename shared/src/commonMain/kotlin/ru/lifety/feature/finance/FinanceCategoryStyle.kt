package ru.lifety.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LifetyColors
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol

/** Category IDs, rather than their order or translated labels, determine the accent. */
internal fun defaultCategoryColor(category: String): Color = with(LifetyColors.ExpenseCategory) {
    when (category) {
        "food" -> Food
        "transport" -> Transport
        "shopping" -> Shopping
        "bills" -> Bills
        "health" -> Health
        "leisure" -> Leisure
        "education" -> Education
        else -> Other
    }
}

internal fun categoryHexColor(hex: String): Color? = if (Regex("#[0-9A-Fa-f]{6}").matches(hex))
    Color(0xFF000000L or hex.drop(1).toLong(16)) else null

@Composable
internal fun categoryColor(category: String): Color = LocalExpenseCategories.current.firstOrNull { it.id == category }
    ?.colorHex?.let(::categoryHexColor) ?: defaultCategoryColor(category)

@Composable
internal fun categoryDisplayName(category: String): String = LocalExpenseCategories.current.firstOrNull { it.id == category }
    ?.name ?: categoryTitle(category)

/** Decorative: the adjacent category name supplies the accessible label. */
@Composable
internal fun CategoryMarker(category: String) {
    Box(Modifier.size(10.dp).background(categoryColor(category), CircleShape)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).clearAndSetSemantics {})
}

@Composable
internal fun CategoryFilterChip(category: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = categoryColor(category)
    FilterChip(selected = selected, enabled = enabled, onClick = onClick,
        label = { Text(categoryDisplayName(category)) },
        leadingIcon = {
            if (selected) LifetyIcon(LifetySymbol.Check, Modifier.size(18.dp).clearAndSetSemantics {}, color = MaterialTheme.colorScheme.onSurface)
            else CategoryMarker(category)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.17f).compositeOver(MaterialTheme.colorScheme.surface),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = accent.copy(alpha = 0.75f)),
    )
}
