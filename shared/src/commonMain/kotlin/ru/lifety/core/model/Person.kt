package ru.lifety.core.model

import kotlinx.serialization.Serializable

/** Birthday is a civil month/day, with an optional known year; never a UTC instant. */
@Serializable
data class Person(
    val id: String,
    val fullName: String,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
    val birthYear: Int? = null,
    val interests: String = "",
    val hobbies: String = "",
    val giftIdeas: String = "",
)
