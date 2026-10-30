package ru.lifety.feature.tasks

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
internal expect fun TaskDateTimeField(label: String, description: String, value: Long?, enabled: Boolean,
    referenceDate: LocalDate, deadline: Boolean, onChange: (Long?) -> Unit)
