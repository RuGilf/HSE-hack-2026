package ru.lifety.core.appearance

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class AppearanceControllerTest {
    private class Platform : AppearancePlatform {
        var theme: String? = null
        var icon = LifetyPalette.Lifety
        var saveFails = false
        var iconFails = false
        var gate: CompletableDeferred<Unit>? = null
        var iconCalls = 0
        override val supportsIcons = true
        override fun readTheme() = theme
        override fun currentIcon() = icon
        override suspend fun saveTheme(id: String) { if (saveFails) error("disk full"); theme = id }
        override suspend fun changeIcon(theme: LifetyPalette) {
            iconCalls++; gate?.await(); if (iconFails) error("OS refused")
            icon = theme
        }
    }
    @Test fun savedThemeRestoresWithoutChangingIcon() = runBlocking {
        val platform = Platform()
        val controller = AppearanceController(platform, this)
        LifetyPalette.entries.forEach { theme ->
            controller.selectTheme(theme)
            controller.state.first { !it.savingTheme }
            assertEquals(theme, AppearanceController(platform, this).state.value.theme)
        }
        assertEquals(LifetyPalette.Lifety, platform.icon)
        assertEquals(0, platform.iconCalls)
    }
    @Test fun failedPersistenceRestoresPreviousTheme() = runBlocking {
        val platform = Platform().apply { theme = "nord"; saveFails = true }
        val controller = AppearanceController(platform, this)
        controller.selectTheme(LifetyPalette.Gruvbox)
        controller.state.first { !it.savingTheme }
        assertEquals(LifetyPalette.Nord, controller.state.value.theme)
        assertNotNull(controller.state.value.error)
        Unit
    }
    @Test fun iconFailureDoesNotClaimSuccessOrChangeTheme() = runBlocking {
        val platform = Platform().apply { theme = "nord"; iconFails = true }
        val controller = AppearanceController(platform, this)
        controller.changeIcon(LifetyPalette.Nord)
        controller.state.first { !it.changingIcon }
        assertEquals(LifetyPalette.Lifety, controller.state.value.icon)
        assertEquals(LifetyPalette.Nord, controller.state.value.theme)
        assertNotNull(controller.state.value.error)
        Unit
    }
    @Test fun iconRequestIsNotDuplicatedAndResetUsesActualSystemState() = runBlocking {
        val platform = Platform().apply { gate = CompletableDeferred() }
        val controller = AppearanceController(platform, this)
        controller.changeIcon(LifetyPalette.TokyoNight)
        controller.changeIcon(LifetyPalette.Nord)
        yield()
        assertEquals(1, platform.iconCalls)
        platform.gate!!.complete(Unit)
        controller.state.first { !it.changingIcon }
        assertEquals(LifetyPalette.TokyoNight, controller.state.value.icon)
        platform.gate = null
        controller.changeIcon(LifetyPalette.Lifety)
        controller.state.first { !it.changingIcon }
        assertEquals(LifetyPalette.Lifety, platform.icon)
    }
    @Test fun unknownSavedPreferenceFallsBackToOriginalTheme() {
        assertEquals(LifetyPalette.Lifety, LifetyPalette.fromId("old-or-damaged-value"))
        assertEquals(LifetyPalette.Lifety, LifetyPalette.fromIconName(null))
        assertEquals(LifetyPalette.entries.size, LifetyPalette.entries.map { it.id }.toSet().size)
    }
}
