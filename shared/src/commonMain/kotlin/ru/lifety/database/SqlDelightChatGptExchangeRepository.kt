package ru.lifety.database

import app.cash.sqldelight.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import ru.lifety.core.planning.*
import ru.lifety.core.repository.ChatGptExchangeRepository
import ru.lifety.database.generated.LifetyDatabase

internal fun LifetyQueries.readChatGptWorkspace(): ChatGptWorkspace = selectChatGptExchange().executeAsOneOrNull()?.let { Json.decodeFromString<ChatGptWorkspace>(it) } ?: ChatGptWorkspace()
internal fun LifetyQueries.writeChatGptWorkspace(workspace: ChatGptWorkspace) = upsertChatGptExchange(Json.encodeToString(workspace))

class SqlDelightChatGptExchangeRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ChatGptExchangeRepository {
    private val queries = database.lifetyQueries
    override fun observe(): Flow<ChatGptWorkspace> = queries.selectChatGptExchange().asFlow().mapToOneOrNull(dispatcher)
        .map { it?.let { Json.decodeFromString<ChatGptWorkspace>(it) } ?: ChatGptWorkspace() }
    override suspend fun savePreferences(preferences: ChatGptPreferences) = withContext(dispatcher) {
        require(preferences.healthMetricIds.size <= 50)
        require(preferences.instructions.length <= 4000 && preferences.conversationUrl.length <= 2048 && validConversationUrl(preferences.conversationUrl)) { "Укажите личную ссылку https://chatgpt.com/c/… или оставьте поле пустым" }
        queries.transaction {
            val old = queries.readChatGptWorkspace()
            // Prepared text may contain sleep data; discard it when permission/profile changes.
            val changedContext = profileFingerprint(old.preferences) != profileFingerprint(preferences)
            queries.writeChatGptWorkspace(old.copy(preferences = preferences, draft = if (old.preferences == preferences) old.draft else null,
                lastSent = if (changedContext) old.lastSent?.copy(awaitingReply = false) else old.lastSent))
        }
    }
    override suspend fun saveDraft(draft: ChatGptDraft) = withContext(dispatcher) {
        require(draft.prompt.length <= 1_000_000 && draft.id.isNotBlank())
        queries.transaction {
            val old = queries.readChatGptWorkspace()
            require(profileFingerprint(old.preferences) == draft.profileFingerprint) { "Настройки изменились. Подготовьте запрос ещё раз." }
            queries.writeChatGptWorkspace(old.copy(draft = draft))
        }
    }
    override suspend fun markSent(draftId: String, now: Long) = withContext(dispatcher) {
        queries.transaction {
            val old = queries.readChatGptWorkspace()
            val draft = old.draft
            require(draft != null && draft.id == draftId) { "Запрос изменился. Подготовьте актуальный контекст." }
            queries.writeChatGptWorkspace(old.copy(lastSent = ChatGptSent(draft.id, now, draft.baseSnapshot, draft.sourceFingerprint, draft.profileFingerprint, draft.baseline)))
        }
    }
}
