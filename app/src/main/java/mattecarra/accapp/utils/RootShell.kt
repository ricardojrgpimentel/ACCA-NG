package mattecarra.accapp.utils

import com.topjohnwu.superuser.Shell

/**
 * Central choke point for every root shell invocation in the app.
 *
 * ACC daemon commands can block indefinitely on-device (e.g. `set_ch_curr`
 * waits for the battery to be *charging* when current-control files were
 * never detected; stale daemon locks serialize `--set` workers forever).
 * A single hung call used to wedge libsu's shared root shell and freeze
 * every subsequent root read in the app — the "infinite loading" class of
 * bugs. Bounding every command with the platform `timeout` applet keeps a
 * wedged daemon from wedging the UI: the call fails fast and the caller
 * degrades (error state / retry) instead of spinning forever.
 */
object RootShell {
    /** Default per-command ceiling in seconds. */
    const val DEFAULT_TIMEOUT_SECS = 20

    /** For operations that legitimately take longer (switch testing). */
    const val LONG_TIMEOUT_SECS = 60

    fun exec(cmd: String, timeoutSecs: Int = DEFAULT_TIMEOUT_SECS): Shell.Result =
        execute(cmd, cmd, timeoutSecs)

    private fun execute(cmd: String, displayedCommand: String, timeoutSecs: Int): Shell.Result {
        val trace = CommandTraceContext.current()
        val step = trace?.started(displayedCommand)
        try {
            val result = Shell.su("timeout -k 2 $timeoutSecs $cmd").exec()
            if (step != null) {
                val error = if (result.isSuccess) null else
                    result.err.takeLast(6).joinToString("\n").ifBlank {
                        result.out.takeLast(6).joinToString("\n")
                    }
                trace?.finished(step, result.code, error)
            }
            return result
        } catch (ex: Exception) {
            if (step != null) trace?.finished(step, null, ex.message ?: ex.javaClass.simpleName)
            throw ex
        }
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    fun execScript(script: String, timeoutSecs: Int = DEFAULT_TIMEOUT_SECS): Shell.Result =
        execute("/system/bin/sh -c ${quote(script)}", script, timeoutSecs)
}
