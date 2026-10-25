package ru.lifety.feature.health

import androidx.compose.runtime.Composable
import ru.lifety.core.repository.HealthRepository

/** Health Connect is Android-only. Manual local input remains available on iOS. */
@Composable
internal actual fun HealthConnectSection(repository: HealthRepository?, enabled: Boolean, onBusyChanged: (Boolean) -> Unit) = Unit
