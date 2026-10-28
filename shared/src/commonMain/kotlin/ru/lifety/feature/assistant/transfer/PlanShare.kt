package ru.lifety.feature.assistant.transfer

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberPlanShare(): (String) -> Unit

@Composable
internal expect fun rememberPlanImportFile(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit
