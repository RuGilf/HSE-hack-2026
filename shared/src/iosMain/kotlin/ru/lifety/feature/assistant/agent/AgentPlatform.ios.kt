package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.sync.Mutex
import platform.Foundation.*

private val agentOperationGate = Mutex()
private val agentSessionLease = Mutex()
fun createIosAgentRuntime(): AgentRuntime {
    val root = NSHomeDirectory() + "/Library/Application Support/LifetyAgent"
    val privateDisk = LocalAgentDisk("$root/private")
    return AgentRuntime(AgentWorkspace(LocalAgentDisk("$root/workspace")), JsonAgentStore(privateDisk), IosSessionVault(privateDisk),
        { (NSDate().timeIntervalSince1970 * 1000).toLong() }, agentOperationGate, agentSessionLease)
}

private class IosSessionVault(private val disk: AgentDisk) : AgentVault {
    override val description = "На iOS API-ключ хранится только до закрытия этого экрана; настройки провайдера сохраняются."
    private var key = ""
    override suspend fun load(): AgentCredentials = AgentCredentials(disk.read("provider.json")?.let { agentJson.decodeFromString<ProviderSettings>(it) } ?: ProviderSettings(), key)
    override suspend fun save(credentials: AgentCredentials) {
        credentials.settings.validate()
        disk.write("provider.json", agentJson.encodeToString(credentials.settings)); key = credentials.key
    }
}
