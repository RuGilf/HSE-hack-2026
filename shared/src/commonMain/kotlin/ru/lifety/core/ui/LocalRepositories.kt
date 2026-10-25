package ru.lifety.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.lifety.core.repository.Repositories

/** Production entry points provide the shared SQLite repositories; null permits isolated previews. */
val LocalRepositories = staticCompositionLocalOf<Repositories?> { null }
