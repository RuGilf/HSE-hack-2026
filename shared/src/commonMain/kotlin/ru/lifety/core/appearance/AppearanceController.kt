package ru.lifety.core.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface AppearancePlatform {
    fun readTheme(): String?
    suspend fun saveTheme(id: String)
    val supportsIcons: Boolean
    fun currentIcon(): LifetyPalette
    suspend fun changeIcon(theme: LifetyPalette)
}

internal data class AppearanceState(val theme: LifetyPalette = LifetyPalette.Lifety,
    val icon: LifetyPalette = LifetyPalette.Lifety, val supportsIcons: Boolean = false,
    val savingTheme: Boolean = false, val changingIcon: Boolean = false, val error: String? = null)

internal class AppearanceController(private val platform: AppearancePlatform, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(AppearanceState(theme = LifetyPalette.fromId(platform.readTheme()),
        icon = platform.currentIcon(), supportsIcons = platform.supportsIcons))
    val state = mutable.asStateFlow()

    fun selectTheme(theme: LifetyPalette) {
        if (state.value.savingTheme || state.value.theme == theme) return
        val previous = state.value.theme
        mutable.value = state.value.copy(theme = theme, savingTheme = true, error = null)
        scope.launch {
            try { platform.saveTheme(theme.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(theme = previous, error = "Не удалось сохранить тему. Попробуйте ещё раз.") }
            finally { mutable.value = state.value.copy(savingTheme = false) }
        }
    }

    fun refreshIcon() { mutable.value = state.value.copy(icon = platform.currentIcon()) }

    fun changeIcon(theme: LifetyPalette) {
        if (!state.value.supportsIcons || state.value.changingIcon || state.value.icon == theme) return
        mutable.value = state.value.copy(changingIcon = true, error = null)
        scope.launch {
            try { platform.changeIcon(theme) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(error = "Не удалось сменить иконку. Тема приложения сохранена; попробуйте ещё раз.") }
            finally { mutable.value = state.value.copy(changingIcon = false, icon = platform.currentIcon()) }
        }
    }
}

internal val LocalAppearance = staticCompositionLocalOf<AppearanceController?> { null }
@Composable internal expect fun rememberAppearanceController(): AppearanceController
@Composable internal expect fun ApplyPlatformAppearance(palette: ThemePalette)
