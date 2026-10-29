package ru.lifety.app.navigation

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.repository.NavigationPreferencesRepository
import ru.lifety.core.ui.LifetySymbol

val defaultNavigation = listOf("dashboard", "tasks", "finance", "health", "assistant")
val availableNavigation = defaultNavigation + listOf("calendar", "people")
fun normalizedNavigation(routes: List<String>): List<String> = routes.filter { it in availableNavigation }.distinct().take(5).ifEmpty { defaultNavigation }
internal val AppDestination.symbol: LifetySymbol get() = when (this) {
    AppDestination.DASHBOARD -> LifetySymbol.Home
    AppDestination.TASKS -> LifetySymbol.Plan
    AppDestination.CALENDAR -> LifetySymbol.Calendar
    AppDestination.FINANCE -> LifetySymbol.Wallet
    AppDestination.HEALTH -> LifetySymbol.Heart
    AppDestination.PEOPLE -> LifetySymbol.People
    AppDestination.ASSISTANT -> LifetySymbol.Assistant
}
internal data class NavigationState(val routes: List<String> = defaultNavigation, val saving: Boolean = false, val error: String? = null)
internal class NavigationController(private val repository: NavigationPreferencesRepository?, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(NavigationState())
    val state = mutable.asStateFlow()
    init { scope.launch { try { repository?.observe()?.collect { mutable.update { s -> s.copy(routes = normalizedNavigation(it)) } } }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { mutable.update { it.copy(error = "Не удалось загрузить панель") } } } }
    fun save(routes: List<String>, onSaved: () -> Unit) {
        if (state.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try { requireNotNull(repository).save(routes); onSaved() }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { mutable.update { it.copy(error = "Не удалось сохранить панель") } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
internal val LocalNavigationController = staticCompositionLocalOf<NavigationController?> { null }
internal val LocalNavigationState = staticCompositionLocalOf { NavigationState() }
internal val LocalNavigationItems = staticCompositionLocalOf { defaultNavigation.map(AppDestination::fromRoute) }
@Composable internal fun NavigationProvider(repository: NavigationPreferencesRepository?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) { NavigationController(repository, scope) }
    val state by controller.state.collectAsState()
    CompositionLocalProvider(LocalNavigationController provides controller, LocalNavigationState provides state,
        LocalNavigationItems provides state.routes.map(AppDestination::fromRoute), content = content)
}
