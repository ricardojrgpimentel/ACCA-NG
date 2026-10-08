package mattecarra.accapp.acc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mattecarra.accapp.utils.RootShell
import java.io.IOException

object ModernAccDaemon {
    private const val MANAGER = "/dev/.vr25/acc/acca"

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        when (val code = RootShell.exec("$MANAGER -D", 5).code) {
            0, 8 -> true
            9 -> false
            else -> throw IOException("ACC status command failed: $code")
        }
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning()) return@withContext true
        if (!RootShell.exec("$MANAGER -D start", 10).isSuccess) {
            // A launch may report a timeout after the service was started.
            return@withContext waitForState(true)
        }
        waitForState(true)
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning()) return@withContext true
        val processes = RootShell.exec("ps -A -o PID,ARGS", 5).let { result ->
            if (!result.isSuccess) return@withContext false
            result.out.mapNotNull(AccDaemonProcess::parse)
        }
        // Charging tests keep a saved configuration in their EXIT trap. An
        // orphaned test must not restore that older configuration during stop.
        signalVerified(processes.filter { it.isTestWorker }, "KILL")
        val daemons = processes.filterNot { it.isTestWorker }
        val lockPid = RootShell.exec("cat /dev/.vr25/acc/acc.lock", 5).out.firstOrNull()?.trim()?.toIntOrNull()
        // Avoid the known wait on an obsolete PID. A normal, current PID uses
        // ACC's own shutdown path first, allowing its cleanup hooks to run.
        if (daemons.any { it.pid == lockPid }) {
            RootShell.exec("$MANAGER -D stop", 12)
            if (!isRunning()) return@withContext true
        }
        // Recover only verified ACC daemon/test processes; never trust that PID.
        signalVerified(daemons, "TERM")
        delay(2000)
        signalVerified(daemons, "KILL")
        waitForState(false)
    }

    suspend fun restart(): Boolean = stop() && start()

    private fun signalVerified(processes: List<AccDaemonProcess>, signal: String) {
        if (processes.isEmpty()) return
        val script = processes.joinToString("\n") { process ->
            // Recheck the command immediately before signalling: PIDs can be reused.
            "if [ \"\$(tr '\\000' ' ' < /proc/${process.pid}/cmdline 2>/dev/null | sed 's|^/system/bin/sh |sh |; s/ *$//')\" = ${RootShell.quote(process.command)} ]; then kill -$signal ${process.pid} 2>/dev/null || :; fi"
        }
        RootShell.execScript(script, 5)
    }

    private suspend fun waitForState(running: Boolean): Boolean {
        repeat(8) {
            if (isRunning() == running) return true
            delay(500)
        }
        return false
    }
}
