package ru.lifety.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lifety.core.di.AppContainer
import ru.lifety.database.DatabaseDriverFactory

// Retained for the application process, shared across view controllers.
private val appContainer by lazy { AppContainer(DatabaseDriverFactory().createDriver()) }

fun MainViewController() = ComposeUIViewController {
    RepositoryBootstrap(load = { withContext(Dispatchers.IO) { appContainer.repositories } }) {
        LifetyApp(it)
    }
}
