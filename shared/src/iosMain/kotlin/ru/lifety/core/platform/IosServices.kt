package ru.lifety.core.platform

import ru.lifety.core.model.HealthSnapshot

/** Implemented by the iOS host; credentials never enter the agent's file workspace. */
interface IosCredentialStorage {
    fun read(): IosSecretResult
    fun write(value: String): Boolean
}
data class IosSecretResult(val value: String?, val error: String?)

/** HealthKit deliberately does not reveal whether a read permission was denied. */
interface IosHealthSource {
    val available: Boolean
    fun requestAccess(sleep: Boolean, steps: Boolean, completion: (String?) -> Unit)
    fun readDays(sleep: Boolean, steps: Boolean, days: Int, completion: (List<HealthSnapshot>?, String?) -> Unit)
    fun cancelRead()
}

object IosServices {
    var credentials: IosCredentialStorage? = null
    var health: IosHealthSource? = null
}
