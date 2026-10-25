package ru.lifety.feature.assistant.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private val agentOperationGate = Mutex()
private val agentSessionLease = Mutex()
fun createAndroidAgentRuntime(context: Context): AgentRuntime {
    val application = context.applicationContext
    val root = File(application.noBackupFilesDir, "lifety-agent")
    return AgentRuntime(AgentWorkspace(LocalAgentDisk(File(root, "workspace").path)), JsonAgentStore(LocalAgentDisk(File(root, "private").path)),
        AndroidAgentVault(application), System::currentTimeMillis, agentOperationGate, agentSessionLease)
}

private class AndroidAgentVault(context: Context) : AgentVault {
    override val description = "Ключ шифруется ключом Android Keystore. Рабочая папка агента к нему доступа не имеет."
    private val preferences = context.getSharedPreferences("lifety-agent-provider", Context.MODE_PRIVATE)
    private val alias = "lifety.agent.api.v1"
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override suspend fun load(): AgentCredentials = withContext(Dispatchers.IO) {
        val settings = preferences.getString("settings", null)?.let { agentJson.decodeFromString<ProviderSettings>(it) } ?: ProviderSettings()
        val encrypted = preferences.getString("ciphertext", null)
        val key = if (encrypted == null) "" else {
            val iv = Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv)) }
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).decodeToString()
        }
        AgentCredentials(settings, key)
    }
    override suspend fun save(credentials: AgentCredentials) = withContext(Dispatchers.IO) {
        credentials.settings.validate()
        val editor = preferences.edit().putString("settings", agentJson.encodeToString(credentials.settings))
        if (credentials.key.isBlank()) editor.remove("ciphertext").remove("iv") else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
            editor.putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            editor.putString("ciphertext", Base64.encodeToString(cipher.doFinal(credentials.key.encodeToByteArray()), Base64.NO_WRAP))
        }
        if (!editor.commit()) throw AgentFault("storage", "Не удалось сохранить настройки API")
    }
}
