@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import platform.Foundation.*
import ru.lifety.core.platform.*
import kotlin.test.*

class IosAgentRuntimeTest {
    @Test fun nativeQuickJsCalculatesBigIntAndPersistsFiles(): Unit = runBlocking {
        val root = NSTemporaryDirectory() + "/lifety-js-${NSUUID().UUIDString}"
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root))
            workspace.write("scripts/check.js", """
                const amount = 9007199254740993n + 7n;
                await fs.writeText('reports/total.txt', String(amount));
                console.log([typeof fetch, typeof require, String(amount)].join(','));
            """.trimIndent())
            val result = workspace.runScript("scripts/check.js")
            assertEquals("ok", result["status"]!!.jsonPrimitive.content)
            assertTrue(result.toString().contains("undefined,undefined,9007199254741000"))
            assertEquals("9007199254741000", AgentWorkspace(LocalAgentDisk(root)).read("reports/total.txt"))
        } finally { NSFileManager.defaultManager.removeItemAtPath(root, error = null) }
    }

    @Test fun credentialsSurviveRuntimeRecreationThroughTheHostKeychainBoundary(): Unit = runBlocking {
        val previous = IosServices.credentials
        var stored: String? = null
        IosServices.credentials = object : IosCredentialStorage {
            override fun read() = IosSecretResult(stored, null)
            override fun write(value: String): Boolean { stored = value; return true }
        }
        try {
            val credentials = AgentCredentials(ProviderSettings(baseUrl = "https://example.invalid/v1", model = "test"), "synthetic-test-key")
            createIosAgentRuntime().vault.save(credentials)
            assertEquals(credentials, createIosAgentRuntime().vault.load())
        } finally { IosServices.credentials = previous }
    }
}
