package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.Person

interface PersonRepository {
    fun observeAll(): Flow<List<Person>>
    suspend fun upsert(person: Person)
    suspend fun delete(id: String)
}
