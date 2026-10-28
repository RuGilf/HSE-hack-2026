package ru.lifety.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

private class AndroidSectionLockPlatform(context: Context) : SectionLockPlatform {
    private val preferences = context.getSharedPreferences("lifety-section-lock", Context.MODE_PRIVATE)
    private val alias = "lifety-section-lock-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): String? {
        val saved = preferences.getString("configuration", null) ?: return null
        val parts = saved.split(":")
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).decodeToString()
    }
    override fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val saved = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.encodeToByteArray()), Base64.NO_WRAP)
        check(preferences.edit().putString("configuration", saved).commit())
    }
    override fun salt(): String = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
    override fun derive(pin: String, salt: String): String {
        // This KDF stays identical across Android upgrades and is available on minSdk 24.
        val specification = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 1_300_000, 256)
        return try { Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(specification).encoded, Base64.NO_WRAP) }
        finally { specification.clearPassword() }
    }
}

private var sectionLockController: SectionLockController? = null
@Composable internal actual fun rememberSectionLockController(): SectionLockController {
    val context = LocalContext.current.applicationContext
    return remember(context) { sectionLockController ?: SectionLockController(AndroidSectionLockPlatform(context),
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)).also { sectionLockController = it } }
}

@Composable internal actual fun ObserveSectionLockBackground(onBackground: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(onBackground)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) latest() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
