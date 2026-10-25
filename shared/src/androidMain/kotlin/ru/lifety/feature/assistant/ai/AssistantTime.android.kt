package ru.lifety.feature.assistant.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun assistantLocalTime(now: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(Date(now))
