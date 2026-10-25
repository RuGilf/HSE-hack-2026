package ru.lifety.feature.assistant.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable internal actual fun rememberAgentRuntime(): AgentRuntime {
    val application = LocalContext.current.applicationContext
    return remember(application) { createAndroidAgentRuntime(application) }
}
