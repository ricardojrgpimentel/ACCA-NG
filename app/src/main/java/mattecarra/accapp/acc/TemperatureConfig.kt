package mattecarra.accapp.acc

import mattecarra.accapp.models.AccConfig

/** ACC replaced its timed thermal pause with a resume temperature in August 2023. */
object TemperatureConfig {
    fun usesResumeTemperature(version: Int) = version >= 202308120

    // Old profiles contain seconds, not degrees. Use a five-degree hysteresis
    // when migrating their meaning; never reinterpret e.g. a 90s pause as 90°C.
    fun modern(value: AccConfig.ConfigTemperature): AccConfig.ConfigTemperature = value.copy(
        pause = 90, // legacy-only field, excluded from modern profile identity
        resumeTemperature = value.resumeTemperature ?: (value.maxTemperature - 5).coerceAtLeast(0)
    )

    fun command(value: AccConfig.ConfigTemperature): String {
        val modern = modern(value)
        val resume = requireNotNull(modern.resumeTemperature)
        require(resume >= 0 && resume < modern.maxTemperature) {
            "Resume temperature must be below the maximum temperature"
        }
        return "env async=true /dev/.vr25/acc/acca -s cooldown_temp=${modern.coolDownTemperature} " +
            "max_temp=${modern.maxTemperature} resume_temp=$resume" +
            if (modern.resumeTemperatureOverridesCapacity) "r" else ""
    }
}
