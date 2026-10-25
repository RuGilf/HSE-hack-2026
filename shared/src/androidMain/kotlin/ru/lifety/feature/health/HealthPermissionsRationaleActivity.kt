package ru.lifety.feature.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import ru.lifety.core.ui.LifetySpacing
import ru.lifety.core.ui.LifetyTheme

/** Opened by Health Connect's privacy-policy link. Does not open repositories or read data. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifetyTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(LifetySpacing.Screen),
                        verticalArrangement = Arrangement.spacedBy(LifetySpacing.Content)) {
                        Text("Lifety и ваши показатели", style = MaterialTheme.typography.headlineLarge)
                        Text(HEALTH_CONNECT_PRIVACY, style = MaterialTheme.typography.bodyLarge)
                        Text("Вы можете выдать доступ только к одному показателю и отозвать его в Health Connect в любой момент. Уже сохранённые в Lifety снимки после отзыва доступа остаются на устройстве.")
                        Text("Для импорта откройте «Здоровье» в Lifety: настройте разрешения, нажмите «Прочитать показатели», проверьте результат и подтвердите сохранение. Если записей нет, локальные данные не заменяются.")
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = LifetySpacing.ControlHeight)) { Text("Понятно") }
                    }
                }
            }
        }
    }
}
