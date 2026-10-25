package ru.lifety.feature.assistant

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.*
import ru.lifety.core.repository.*
import ru.lifety.feature.assistant.ai.*

private class Fixture {
    val taskValues = MutableStateFlow(listOf(Task("private-id", "План", 100, 200, false)))
    val expenseValues = MutableStateFlow(listOf(Expense("private-expense", 12345, "RUB", "PRIVATE SHOP", "PRIVATE CATEGORY", 90, "PRIVATE SOURCE")))
    val healthValue = MutableStateFlow<HealthSnapshot?>(HealthSnapshot(90, 450, 5000))
    var taskReads = 0; var expenseReads = 0; var healthReads = 0; var writes = 0
    val repositories = Repositories(
        object : TaskRepository {
            override fun observeAll(): Flow<List<Task>> { taskReads++; return taskValues }
            override suspend fun upsert(task: Task) { writes++ }
            override suspend fun delete(id: String) { writes++ }
        },
        object : ExpenseRepository {
            override fun observeAll(): Flow<List<Expense>> { expenseReads++; return expenseValues }
            override suspend fun upsert(expense: Expense) { writes++ }
            override suspend fun delete(id: String) { writes++ }
        },
        object : HealthRepository {
            override fun observeLatest(): Flow<HealthSnapshot?> { healthReads++; return healthValue }
            override suspend fun update(snapshot: HealthSnapshot) { writes++ }
        },
    )
}

class AssistantPrivacyTest {
    @Test fun everyPermissionCombinationOnlyReadsAllowedRepositories() = runBlocking {
        for (bits in 0..7) {
            val fixture = Fixture()
            val permission = ContextPermissions(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0)
            val context = ContextBuilder(fixture.repositories).build(permission, 100)
            assertEquals(if (permission.tasks) 1 else 0, fixture.taskReads)
            assertEquals(if (permission.finances) 1 else 0, fixture.expenseReads)
            assertEquals(if (permission.health) 1 else 0, fixture.healthReads)
            val json = context.previewJson()
            assertEquals(permission.tasks, "\"tasks\"" in json)
            assertEquals(permission.finances, "\"finances\"" in json)
            assertEquals(permission.health, "\"health\"" in json)
            listOf("private-id", "private-expense", "PRIVATE SHOP", "PRIVATE CATEGORY", "PRIVATE SOURCE").forEach { assertFalse(it in json) }
        }
    }

    @Test fun emptyPermittedRepositoriesRemainHonest() = runBlocking {
        val fixture = Fixture()
        fixture.taskValues.value = emptyList(); fixture.expenseValues.value = emptyList(); fixture.healthValue.value = null
        val context = ContextBuilder(fixture.repositories).build(ContextPermissions(true, true, true), 100)
        assertEquals(0, context.tasks?.total)
        assertEquals(emptyList(), context.finances?.spending)
        assertNull(context.health)
    }

    @Test fun contextIsBoundedAndReportsOmittedTasks() = runBlocking {
        val fixture = Fixture()
        fixture.taskValues.value = (0..49).map { Task("id$it", "x".repeat(200), null, null, false) }
        val tasks = ContextBuilder(fixture.repositories).build(ContextPermissions(tasks = true), 100).tasks!!
        assertEquals(40, tasks.items.size)
        assertEquals(10, tasks.omitted)
        assertTrue(tasks.items.all { it.title.length == 100 })
    }

    @Test fun controllerNeverSendsOnStartupSettingsOrRepositoryChange() {
        val fixture = Fixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            var sent: AssistantContext? = null
            val provider = object : AiProvider {
                override suspend fun analyze(settings: AiSettings, goal: AnalysisGoal, context: AssistantContext): AnalysisResult {
                    calls++; sent = context
                    return AnalysisResult("План", listOf(Recommendation("Шаг", "Причина")))
                }
            }
            val settings = AiSettings(model = "fixture-model", apiKey = ApiKey("fixture-key"), permissions = ContextPermissions(tasks = true))
            val controller = AssistantController(fixture.repositories, scope, provider, { 100 }, settings, {})
            assertEquals(0, calls)
            controller.analyze()
            assertEquals(0, calls)
            controller.updateSettings(settings)
            controller.preparePreview()
            val preview = controller.state.value.preview
            assertNotNull(preview)
            assertEquals(0, calls)
            fixture.taskValues.value = emptyList()
            assertEquals(0, calls)
            controller.analyze()
            assertEquals(1, calls)
            assertSame(preview, sent)
            assertEquals(0, fixture.writes)
            controller.updateSettings(settings.copy(permissions = ContextPermissions()))
            assertNull(controller.state.value.preview)
            controller.analyze()
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun missingKeyAndProviderFailuresDoNotCrashOrWrite() {
        val fixture = Fixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val provider = object : AiProvider {
                override suspend fun analyze(settings: AiSettings, goal: AnalysisGoal, context: AssistantContext): AnalysisResult {
                    calls++; throw AiFailure("API unavailable")
                }
            }
            val settings = AiSettings(model = "fixture-model", permissions = ContextPermissions(tasks = true))
            val controller = AssistantController(fixture.repositories, scope, provider, { 100 }, settings, {})
            controller.preparePreview(); controller.analyze()
            assertEquals(0, calls)
            assertNotNull(controller.state.value.error)
            controller.updateSettings(settings.copy(apiKey = ApiKey("fixture-key")))
            controller.preparePreview(); controller.analyze()
            assertEquals(1, calls)
            assertEquals("API unavailable", controller.state.value.error)
            assertFalse(controller.state.value.busy)
            assertEquals(0, fixture.writes)
        } finally { scope.cancel() }
    }

    @Test fun cancellingAnalysisPreservesCancellationAndAllowsRetry() {
        val fixture = Fixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var cancelled = false
            val provider = object : AiProvider {
                override suspend fun analyze(settings: AiSettings, goal: AnalysisGoal, context: AssistantContext): AnalysisResult {
                    try { awaitCancellation() } finally { cancelled = true }
                }
            }
            val controller = AssistantController(fixture.repositories, scope, provider, { 100 },
                AiSettings(model = "fixture-model", apiKey = ApiKey("fixture-key"), permissions = ContextPermissions(tasks = true)), {})
            controller.preparePreview(); controller.analyze()
            assertTrue(controller.state.value.busy)
            controller.cancel()
            assertTrue(cancelled)
            assertFalse(controller.state.value.busy)
            assertNull(controller.state.value.error)
        } finally { scope.cancel() }
    }
}
