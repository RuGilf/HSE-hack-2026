package ru.lifety.core.security

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillResignActiveNotification
import ru.lifety.core.platform.IosServices
import ru.lifety.core.platform.IosSecretResult

private class IosSectionLockPlatform : SectionLockPlatform {
    private val service get() = checkNotNull(IosServices.sectionLocks) { "Section lock storage unavailable" }
    private fun IosSecretResult.checked(): String? { check(error == null); return value }
    override fun read(): String? = service.read().checked()
    override fun write(value: String) { check(service.write(value)) }
    override fun salt(): String = checkNotNull(service.createSalt().checked())
    override fun derive(pin: String, salt: String): String = checkNotNull(service.derivePin(pin, salt).checked())
}
private val sectionLockController by lazy { SectionLockController(IosSectionLockPlatform(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
@Composable internal actual fun rememberSectionLockController(): SectionLockController = sectionLockController

@Composable internal actual fun ObserveSectionLockBackground(onBackground: () -> Unit) {
    val latest by rememberUpdatedState(onBackground)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { latest() }
        onDispose { center.removeObserver(observer) }
    }
}
