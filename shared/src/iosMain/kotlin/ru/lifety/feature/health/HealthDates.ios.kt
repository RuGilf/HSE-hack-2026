@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.lifety.feature.health

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

internal actual fun healthRecordedAt(timestamp: Long): String = NSDateFormatter().apply { dateFormat = "d MMM yyyy, HH:mm" }
    .stringFromDate(NSDate(timeIntervalSince1970 = timestamp / 1000.0))
