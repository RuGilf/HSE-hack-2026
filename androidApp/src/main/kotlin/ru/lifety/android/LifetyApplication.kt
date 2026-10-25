package ru.lifety.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import ru.lifety.core.di.AppContainer
import ru.lifety.database.DatabaseDriverFactory

class LifetyApplication : Application() {
    // Application lifetime: shared by activities, survives configuration changes.
    // The process releases the connection at shutdown; an individual screen never closes it.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Deferred<AppContainer>? = null

    @Synchronized
    fun container(): Deferred<AppContainer> {
        pending?.takeUnless { it.isCancelled }?.let { return it }
        return applicationScope.async {
            AppContainer(DatabaseDriverFactory(this@LifetyApplication).createDriver())
        }.also { pending = it }
    }
}
