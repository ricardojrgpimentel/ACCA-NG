package mattecarra.accapp.acc

import java.io.IOException

/** Parsers shared by command callers and contract fixtures; no shell execution. */
object AccOutput {
    fun version(output: String): Int? = Regex("^\\s*\\S+ \\((\\d+)\\)\\s*$", RegexOption.MULTILINE)
        .find(output)?.groupValues?.get(1)?.toIntOrNull()

    fun daemonRunning(code: Int): Boolean = when (code) {
        0, 8 -> true
        9 -> false
        else -> throw IOException("ACC status command failed: $code")
    }

    fun idleSupported(code: Int, output: String): Pair<Int, Boolean> = code to
        (code in listOf(0, 15) && Regex("^\\s*-\\s*battIdleMode=true\\s*$", RegexOption.MULTILINE)
            .containsMatchIn(output))

    fun switches(code: Int, output: String): List<String> =
        if (code == 0) output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        else emptyList()

    fun configValue(config: CharSequence, key: String): String? {
        val line = config.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("$key=") }
            ?: return null
        val raw = line.substringAfter('=')
        var quote: Char? = null
        var escaped = false
        var end = raw.length
        for ((index, char) in raw.withIndex()) {
            if (escaped) { escaped = false; continue }
            if (char == '\\' && quote != '\'') { escaped = true; continue }
            if (char == quote) quote = null
            else if (quote == null && (char == '\'' || char == '"')) quote = char
            else if (quote == null && char == '#' && (index == 0 || raw[index - 1].isWhitespace())) {
                end = index; break
            }
        }
        var value = raw.substring(0, end).trim()
        // Strip an enclosing quote pair only when it covers the whole value.
        // Preserve quotes inside hooks, e.g. printf '# keep' or 'a' 'b'.
        if (value.length >= 2 && value.first() in "\'\"" && value.last() == value.first()) {
            val delimiter = value.first()
            var closing = -1
            escaped = false
            for (index in 1 until value.length) {
                if (escaped) { escaped = false; continue }
                if (value[index] == '\\' && delimiter == '"') { escaped = true; continue }
                if (value[index] == delimiter) { closing = index; break }
            }
            if (closing == value.lastIndex) value = value.substring(1, value.lastIndex)
        }
        return value.ifBlank { null }
    }
}
