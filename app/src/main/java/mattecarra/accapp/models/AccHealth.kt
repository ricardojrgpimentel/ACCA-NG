package mattecarra.accapp.models

enum class AccHealthIssue {
    UNAVAILABLE, FIXED_LIMIT_MODULE, DIFFERENT_MODULE, CALIBRATION_PENDING,
    DAEMON_STOPPED, CHARGING_ABOVE_LIMIT
}

data class AccHealthSnapshot(
    val available: Boolean = false,
    val running: Boolean? = null,
    val version: Int? = null,
    val moduleHash: String? = null,
    val fixedLimits: Boolean = false,
    val polarity: String? = null,
    val workaround: Boolean = false,
    val hasCurrentSensor: Boolean = false,
    val currentRaw: Long? = null,
    val ampFactor: Long? = null,
    val idleThreshold: Long = 40,
    val status: String? = null,
    val online: Boolean? = null,
    val capacity: Int? = null,
    val pause: Int? = null,
    val resume: Int? = null,
    val switch: String? = null
) {
    val currentMa: Double? get() = currentRaw?.let { raw ->
        ampFactor?.takeIf { it > 0 }?.let { raw.toDouble() * 1000 / it }
    }
    val calibrationPending: Boolean get() = running == true && workaround &&
        hasCurrentSensor && polarity !in listOf("+", "-")

    companion object {
        fun hasFixedLimitLoop(source: String): Boolean {
            val loop = source.substringAfter("ctrl_charging()", "").substringBefore("force_off()")
            // Recognise the diagnosed fork; a differing hash alone is not proof
            // of a broken module (newer official releases also differ).
            return Regex("batt_level\"?\\s+-ge\\s+90").containsMatchIn(loop) &&
                Regex("batt_level\"?\\s+-le\\s+80").containsMatchIn(loop) &&
                !loop.contains("_ge_pause_cap") && !loop.contains("_le_resume_cap")
        }

        fun calibrationPolarity(samples: List<AccHealthSnapshot>): String? {
            if (samples.size < 5 || samples.any {
                !it.available || it.online != false || it.status != "Discharging" ||
                    !it.hasCurrentSensor || it.currentMa == null ||
                    kotlin.math.abs(it.currentMa!!) <= it.idleThreshold
            }) return null
            return when {
                samples.all { it.currentRaw!! < 0 } -> "-"
                samples.all { it.currentRaw!! > 0 } -> "+"
                else -> null
            }
        }
    }
}

data class AccHealthReport(val snapshot: AccHealthSnapshot, val issues: List<AccHealthIssue>)

/** Use monotonic time and consecutive fresh readings, never a single USB transient. */
class AccHealthTracker {
    private var chargingSince: Long? = null
    private var lastReading: Long? = null
    private var limit: Int? = null

    fun evaluate(s: AccHealthSnapshot, bundledHash: String, now: Long): AccHealthReport {
        val issues = mutableListOf<AccHealthIssue>()
        if (!s.available || s.running == null || s.moduleHash == null) issues += AccHealthIssue.UNAVAILABLE
        if (s.available) {
            if (s.fixedLimits) issues += AccHealthIssue.FIXED_LIMIT_MODULE
            else if (s.moduleHash != null && s.moduleHash != bundledHash)
                issues += AccHealthIssue.DIFFERENT_MODULE
            if (s.calibrationPending) issues += AccHealthIssue.CALIBRATION_PENDING
            if (s.running == false) issues += AccHealthIssue.DAEMON_STOPPED
        }
        val realCharge = s.currentMa?.let { current ->
            when (s.polarity) {
                "-" -> current > s.idleThreshold
                "+" -> current < -s.idleThreshold
                else -> !s.workaround && s.status == "Charging"
            }
        } ?: (s.status == "Charging" && !s.workaround)
        val aboveLimit = s.available && s.running == true && !s.calibrationPending &&
            s.pause in 1..100 && s.capacity != null && s.capacity >= s.pause!! && realCharge
        if (!aboveLimit || limit != s.pause || lastReading?.let { now - it > 20_000 } == true) {
            chargingSince = null
        }
        limit = s.pause
        lastReading = now
        if (aboveLimit) {
            val since = chargingSince ?: now.also { chargingSince = it }
            if (now - since >= 30_000) issues += AccHealthIssue.CHARGING_ABOVE_LIMIT
        }
        return AccHealthReport(s, issues)
    }
}
