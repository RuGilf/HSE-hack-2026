package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.sync.Mutex
import platform.Foundation.*
import kotlinx.serialization.Serializable
import ru.lifety.core.platform.IosServices

private val agentOperationGate = Mutex()
private val agentSessionLease = Mutex()
fun createIosAgentRuntime(): AgentRuntime {
    val root = NSHomeDirectory() + "/Library/Application Support/LifetyAgent"
    val privateDisk = LocalAgentDisk("$root/private")
    return AgentRuntime(AgentWorkspace(LocalAgentDisk("$root/workspace")), JsonAgentStore(privateDisk), IosKeychainVault(),
        { (NSDate().timeIntervalSince1970 * 1000).toLong() }, agentOperationGate, agentSessionLease)
}

@Serializable
private data class StoredIosCredentials(val settings: ProviderSettings, val key: String)

private class IosKeychainVault : AgentVault {
    override val description = "После сохранения настройки и API-ключ хранятся в Keychain этого устройства. Рабочая папка агента не имеет доступа к ключу."
    override suspend fun load(): AgentCredentials {
        val storage = IosServices.credentials ?: throw AgentFault("storage", "Keychain не подключён приложением iOS")
        val result = storage.read()
        if (result.error != null) throw AgentFault("storage", "Не удалось прочитать Keychain. Разблокируйте устройство и повторите.")
        val stored = result.value?.let { agentJson.decodeFromString<StoredIosCredentials>(it) } ?: return AgentCredentials()
        return AgentCredentials(stored.settings, stored.key)
    }
    override suspend fun save(credentials: AgentCredentials) {
        credentials.settings.validate()
        val storage = IosServices.credentials ?: throw AgentFault("storage", "Keychain не подключён приложением iOS")
        if (!storage.write(agentJson.encodeToString(StoredIosCredentials(credentials.settings, credentials.key))))
            throw AgentFault("storage", "Не удалось сохранить Keychain. Настройки не сохранены.")
    }
}
