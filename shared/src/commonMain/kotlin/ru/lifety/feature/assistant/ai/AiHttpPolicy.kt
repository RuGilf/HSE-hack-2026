package ru.lifety.feature.assistant.ai

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/** Reasoning models and free queues can take longer than the generic 30-second API budget. */
internal fun HttpClientConfig<*>.configureAiTimeouts() {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 120_000
    }
}
