package com.lumalink.g100d.core.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (value >= 10.0) {
        String.format(Locale.US, "%.0f %s", value, units[unit])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatEta(seconds: Long): String {
    if (seconds <= 0L) return "—"
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remainingSeconds = seconds % 60L
    return when {
        hours > 0L -> "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        minutes > 0L -> "%d:%02d".format(minutes, remainingSeconds)
        else -> "${remainingSeconds}秒"
    }
}
