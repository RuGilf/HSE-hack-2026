package ru.lifety.android

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.lifety.app.LifetyApp
import ru.lifety.app.RepositoryBootstrap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val app = application as LifetyApplication
        setContent {
            RepositoryBootstrap(load = { app.container().await().repositories }) { repositories ->
                LifetyApp(repositories)
            }
        }
    }
}
