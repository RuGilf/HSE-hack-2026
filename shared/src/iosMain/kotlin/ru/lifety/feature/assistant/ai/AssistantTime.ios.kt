@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.lifety.feature.assistant.ai

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun assistantLocalTime(now: Long): String = NSDateFormatter().apply {
    locale = NSLocale("en_US_POSIX")
    dateFormat = "yyyy-MM-dd'T'HH:mm:ssZZZZZ"
}.stringFromDate(NSDate(timeIntervalSince1970 = now / 1000.0))
