package ru.lifety.feature.assistant.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable internal actual fun rememberAgentRuntime(): AgentRuntime = remember { createIosAgentRuntime() }
