package ru.lifety.core.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

private class AndroidAppearance(private val context: Context) : AppearancePlatform {
    private val preferences = context.getSharedPreferences("lifety_appearance", Context.MODE_PRIVATE)
    private val manager = context.packageManager
    private fun component(theme: LifetyPalette) = ComponentName(context.packageName, "${context.packageName}.Launcher${theme.name}")
    override fun readTheme(): String? = preferences.getString("theme", null)
    override suspend fun saveTheme(id: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("theme", id).commit())
    }
    override val supportsIcons = true
    override fun currentIcon(): LifetyPalette = LifetyPalette.entries.firstOrNull { theme ->
        val state = manager.getComponentEnabledSetting(component(theme))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (theme == LifetyPalette.Lifety && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    } ?: LifetyPalette.Lifety

    override suspend fun changeIcon(theme: LifetyPalette) = withContext(Dispatchers.IO) {
        val old = currentIcon()
        fun apply(selected: LifetyPalette) {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.setComponentEnabledSettings(LifetyPalette.entries.map {
                    PackageManager.ComponentEnabledSetting(component(it), if (it == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                })
            } else {
                // Enable the replacement first so older launchers always have an entry point.
                manager.setComponentEnabledSetting(component(selected), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                LifetyPalette.entries.filter { it != selected }.forEach {
                    manager.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        }
        try { apply(theme) } catch (error: Exception) { runCatching { apply(old) }; throw error }
    }
}

private var controller: AppearanceController? = null
@Composable internal actual fun rememberAppearanceController(): AppearanceController {
    val context = LocalContext.current.applicationContext
    return remember(context) { controller ?: AppearanceController(AndroidAppearance(context), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)).also { controller = it } }
}
@Composable internal actual fun ApplyPlatformAppearance(palette: ThemePalette) = Unit
