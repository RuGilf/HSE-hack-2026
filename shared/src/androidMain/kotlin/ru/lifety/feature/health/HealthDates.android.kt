package ru.lifety.feature.health

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun healthRecordedAt(timestamp: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))
