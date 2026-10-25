package ru.lifety.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.lifety.core.repository.Repositories

/** Null only while running the foundation placeholders without implementations. */
val LocalRepositories = staticCompositionLocalOf<Repositories?> { null }
