package ru.lifety.app

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformTopBar(title: String, showMascot: Boolean, onSections: () -> Unit, onSettings: () -> Unit)
