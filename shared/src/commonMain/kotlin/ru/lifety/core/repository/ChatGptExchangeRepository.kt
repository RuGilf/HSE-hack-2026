package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.planning.*

interface ChatGptExchangeRepository {
    fun observe(): Flow<ChatGptWorkspace>
    suspend fun savePreferences(preferences: ChatGptPreferences)
    suspend fun saveDraft(draft: ChatGptDraft)
    suspend fun markSent(draftId: String, now: Long)
}
