package mattecarra.accapp.acc

/** Release dates identify builds; this metadata identifies the frontend contract. */
data class NgEngineContract(val api: Int, val schema: Int?, val capabilities: Set<String>) {
    val supportsConfig: Boolean get() = api == 1 && schema == 202310160
    val usesResumeTemperature: Boolean get() = supportsConfig

    companion object {
        private val knownCapabilities = setOf("info-key-value-si", "capacity-six", "resume-temperature",
            "resume-temperature-override", "manual-discharge-polarity", "ng-notifications")

        fun parse(metadata: String): NgEngineContract? {
            val fields = metadata.lineSequence().map { it.trim() }
                .filter { !it.startsWith("#") && it.contains('=') }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            if ("ngApiVersion" !in fields) return null
            val api = fields["ngApiVersion"]?.toIntOrNull() ?: -1
            // Old NG API 1 builds predate explicit schema/capability metadata.
            // Their baseline contract is fixed; never infer it from versionCode.
            val schema = when {
                "ngConfigSchema" in fields -> fields["ngConfigSchema"]?.toIntOrNull()
                "upstreamVersionCode" in fields -> fields["upstreamVersionCode"]?.toIntOrNull()
                api == 1 -> 202310160
                else -> null
            }
            val advertised = fields["ngCapabilities"].orEmpty().split(',').map { it.trim() }.toSet()
            return NgEngineContract(api, schema,
                if (api == 1 && schema == 202310160) advertised.intersect(knownCapabilities) else emptySet())
        }
    }
}
