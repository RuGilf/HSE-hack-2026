package ru.lifety.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.lifety.app.LifetyApp
import ru.lifety.app.RepositoryBootstrap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LifetyApplication
        setContent {
            RepositoryBootstrap(load = { app.container().await().repositories }) { repositories ->
                LifetyApp(repositories)
            }
        }
    }
}
