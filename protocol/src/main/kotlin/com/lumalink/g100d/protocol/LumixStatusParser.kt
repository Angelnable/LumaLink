package com.lumalink.g100d.protocol

object LumixStatusParser {

    fun parse(body: String): CameraStatus {
        val fields = XmlFields.extract(body)

        val batteryValue = XmlFields.value(
            fields,
            "battery",
            "batt",
            "batterypercent",
            "batterylevel",
            "remain",
        )
        val battery = parsePercent(batteryValue)

        val mode = XmlFields.value(fields, "mode", "cameramode", "shootingmode", "function")
        val recording = parseBoolean(
            XmlFields.value(fields, "rec", "recording", "recstate", "movierec", "movie"),
        )
        val remainingShots = XmlFields.value(
            fields,
            "remain",
            "remainingshots",
            "shots",
            "counter",
            "remaining",
        )?.let(::firstInteger)

        return CameraStatus(
            batteryPercent = battery,
            mode = mode,
            recording = recording,
            remainingShots = remainingShots,
            rawFields = fields,
        )
    }

    private fun parsePercent(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val number = firstInteger(value) ?: return null
        if (number !in 0..100) return null
        // Some cameras report battery bars (0..10) instead of percent. Only
        // accept a small number when the source explicitly says percent.
        if (number <= 10 && !value.contains('%') && !value.contains("percent", ignoreCase = true)) {
            return null
        }
        return number
    }

    private fun parseBoolean(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return when (value.trim().lowercase()) {
            "1", "true", "on", "yes", "rec", "recording", "active" -> true
            else -> false
        }
    }

    private fun firstInteger(value: String): Int? =
        Regex("""\d+""").find(value)?.value?.toIntOrNull()
}
