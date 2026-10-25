package ru.lifety.feature.assistant.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Explicit opt-in API check. Only synthetic context is sent; credentials stay in the environment. */
class LiveAiProviderTest {
    @Test fun existingAnalysisFlowAcceptsTheConfiguredLiveModel() = runBlocking {
        val key = System.getenv("LIFETY_LLM_API_KEY")
        val base = System.getenv("LIFETY_LLM_BASE_URL")
        val model = System.getenv("LIFETY_LLM_MODEL")
        org.junit.Assume.assumeTrue("Set LIFETY_LLM_* to enable live eval",
            !key.isNullOrBlank() && !base.isNullOrBlank() && !model.isNullOrBlank())
        val result = KtorAiProvider().analyze(
            AiSettings(endpoint = base!!.trimEnd('/') + "/chat/completions", model = model!!,
                apiKey = ApiKey(key!!), permissions = ContextPermissions(tasks = true)),
            AnalysisGoal.TASK_REVIEW,
            AssistantContext(1_792_915_200_000, tasks = TasksContext(0, 0, 0, emptyList())),
        )
        assertTrue(result.summary.isNotBlank())
        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.all { it.taskRef == null && it.proposedAt == null })
        println("LIVE_ANALYSIS summary=present recommendations=${result.recommendations.size}")
    }
}
