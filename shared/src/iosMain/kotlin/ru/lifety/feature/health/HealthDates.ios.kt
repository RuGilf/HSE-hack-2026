@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.lifety.feature.health

import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.NSLocale
import platform.Foundation.NSDateFormatter

internal actual fun healthRecordedAt(timestamp: Long): String = NSDateFormatter().apply { locale = NSLocale("ru_RU"); dateFormat = "d MMM yyyy, HH:mm" }
    .stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))
