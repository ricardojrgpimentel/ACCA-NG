package mattecarra.accapp.acc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mattecarra.accapp.models.AccHealthSnapshot
import mattecarra.accapp.models.ProfileActivation
import mattecarra.accapp.utils.RootShell
import java.io.File
import java.io.IOException
import java.util.UUID

object AccTroubleshooter {
    private const val MODULE = "/data/adb/vr25/acc"
    private const val CONFIG = "/data/adb/vr25/acc-data/config.txt"
    private const val MANAGER = "/dev/.vr25/acc/acca"

    suspend fun read(): AccHealthSnapshot = withContext(Dispatchers.IO) {
        val result = RootShell.execScript("""
            printf '__CONFIG__\n'
            cat $CONFIG || exit 1
            printf '\n__MODULE__\n'
            sed -n 's/^versionCode=/version=/p' $MODULE/module.prop 2>/dev/null
            sha256sum $MODULE/accd.sh 2>/dev/null | sed 's/ .*//; s/^/hash=/'
            printf '__SOURCE__\n'
            cat $MODULE/accd.sh 2>/dev/null
            printf '\n__POWER__\n'
            for field in status capacity; do
                key=battStatus; [ "${'$'}field" != capacity ] || key=battCapacity
                path=${'$'}(sed -n "s/^${'$'}key=//p" /dev/.vr25/acc/.batt-interface.sh 2>/dev/null)
                printf '%s' "${'$'}path" | grep -Eq "^[A-Za-z0-9_-]+/${'$'}field${'$'}" || path=battery/${'$'}field
                value=${'$'}(cat /sys/class/power_supply/${'$'}path 2>/dev/null) && printf '%s=%s\n' "${'$'}field" "${'$'}value"
            done
            currentFile=${'$'}(sed -n 's/^currFile=//p' /dev/.vr25/acc/.batt-interface.sh 2>/dev/null)
            if printf '%s' "${'$'}currentFile" | grep -Eq '^[A-Za-z0-9_-]+/current_now${'$'}'; then
                value=${'$'}(cat /sys/class/power_supply/${'$'}currentFile 2>/dev/null) && printf 'current=%s\n' "${'$'}value"
                printf 'sensor=true\n'
                sed -n 's/^ampFactor_=/factor=/p' /dev/.vr25/acc/.batt-interface.sh 2>/dev/null
            fi
            seen=false; connected=false
            for path in /sys/class/power_supply/*/online; do
                case "${'$'}path" in */bms/*) continue;; esac
                value=${'$'}(cat "${'$'}path" 2>/dev/null) || continue
                case "${'$'}value" in 0) seen=true;; 1) seen=true; connected=true;; esac
            done
            ${'$'}seen && printf 'online=%s\n' "${'$'}connected"
            $MANAGER -D >/dev/null 2>&1
            printf 'daemon=%s\n' "${'$'}?"
        """.trimIndent(), 8)
        if (!result.isSuccess) return@withContext AccHealthSnapshot()
        parse(result.out.joinToString("\n"))
    }

    internal fun parse(output: String): AccHealthSnapshot {
        fun section(from: String, until: String) = output.substringAfter(from, "").substringBefore(until)
        fun values(text: String) = text.lineSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && !p[0].startsWith("#")) p[0].trim() to p[1].trim().trim('\'', '"') else null
        }.toMap()
        val config = values(section("__CONFIG__", "__MODULE__"))
        val module = values(section("__MODULE__", "__SOURCE__"))
        val power = values(output.substringAfter("__POWER__", ""))
        val capacity = config["capacity"]?.trim('(', ')')?.split(Regex("\\s+"))
        return AccHealthSnapshot(
            available = config["configVerCode"] != null && power["status"] != null,
            running = when (power["daemon"]) { "0", "8" -> true; "9" -> false; else -> null },
            version = module["version"]?.toIntOrNull(), moduleHash = module["hash"],
            fixedLimits = AccHealthSnapshot.hasFixedLimitLoop(section("__SOURCE__", "__POWER__")),
            polarity = config["dischargePolarity"], workaround = config["battStatusWorkaround"] == "true",
            hasCurrentSensor = power["sensor"] == "true", currentRaw = power["current"]?.toLongOrNull(),
            ampFactor = config["ampFactor"]?.toLongOrNull() ?: power["factor"]?.toLongOrNull(),
            idleThreshold = config["idleThreshold"]?.toLongOrNull()?.coerceAtLeast(0) ?: 40,
            status = power["status"], online = power["online"]?.toBooleanStrictOrNull(),
            capacity = power["capacity"]?.toIntOrNull(), pause = capacity?.getOrNull(3)?.toIntOrNull(),
            resume = capacity?.getOrNull(2)?.toIntOrNull(), switch = config["chargingSwitch"]
        )
    }

    private fun backupPath() = "/data/adb/vr25/acc-data/backup/troubleshoot-${UUID.randomUUID()}"

    suspend fun calibrate(): String? = withContext(Dispatchers.IO) {
        val samples = mutableListOf<AccHealthSnapshot>()
        repeat(5) { n -> samples += read(); if (n < 4) delay(2500) }
        val polarity = AccHealthSnapshot.calibrationPolarity(samples) ?: return@withContext null
        if (read().online != false) return@withContext null
        val backup = backupPath()
        val result = RootShell.execScript("""
            set -eu
            mkdir -p ${RootShell.quote(backup)}
            cp -p $CONFIG ${RootShell.quote("$backup/config.txt")}
            async=true $MANAGER -s discharge_polarity=$polarity
        """.trimIndent(), 10)
        if (result.isSuccess && read().polarity == polarity) backup else null
    }

    /** Same-version service repair, without downgrading a newer module or
     * running its installer (which intentionally skips equal versions). */
    suspend fun restoreDaemon(context: Context): String = withContext(Dispatchers.IO) {
        val before = read()
        check(before.available && before.version == Acc.bundledVersion) { "Incompatible ACC version" }
        val preservedConfig = Acc.instance.readConfig()
        val expectedHash = BundledAccDaemon.hash(context)
        val backup = backupPath()
        val staged = "$MODULE/accd.sh.recovery-${UUID.randomUUID()}"
        val local = File(context.filesDir, "accd-recovery-${UUID.randomUUID()}.sh")
        local.writeBytes(BundledAccDaemon.bytes(context))
        var stopped = false
        try {
            requireSuccess(RootShell.execScript("""
                set -eu
                mkdir -p ${RootShell.quote(backup)}
                cp -a $MODULE/. ${RootShell.quote("$backup/module")}
                cp -p $CONFIG ${RootShell.quote("$backup/config.txt")}
                cp ${RootShell.quote(local.absolutePath)} ${RootShell.quote(staged)}
                chmod 0755 ${RootShell.quote(staged)}
                chown 0:0 ${RootShell.quote(staged)}
                /system/bin/sh -n ${RootShell.quote(staged)}
                actual=${'$'}(sha256sum ${RootShell.quote(staged)} | cut -d ' ' -f 1)
                [ "${'$'}actual" = ${RootShell.quote(expectedHash)} ]
            """.trimIndent(), 20).isSuccess, backup)
            stopped = true
            requireSuccess(ModernAccDaemon.stop(), backup)
            requireSuccess(RootShell.execScript("""
                set -eu
                actual=${'$'}(sha256sum $MODULE/accd.sh | cut -d ' ' -f 1)
                [ "${'$'}actual" = ${RootShell.quote(before.moduleHash ?: "")} ]
                mv ${RootShell.quote(staged)} $MODULE/accd.sh
            """.trimIndent(), 5).isSuccess, backup)
            requireSuccess(ModernAccDaemon.start(), backup)
            requireSuccess(read().moduleHash == expectedHash &&
                ProfileActivation.matches(Acc.instance.readConfig(), preservedConfig), backup)
            backup
        } catch (ex: Exception) {
            if (stopped) {
                // Restore the saved executable atomically if verification fails.
                withContext(NonCancellable) {
                    if (ModernAccDaemon.stop()) RootShell.execScript("""
                        set -eu
                        actual=${'$'}(sha256sum $MODULE/accd.sh | cut -d ' ' -f 1)
                        if [ "${'$'}actual" = ${RootShell.quote(expectedHash)} ]; then
                            cp -p ${RootShell.quote("$backup/module/accd.sh")} ${RootShell.quote(staged)}
                            mv ${RootShell.quote(staged)} $MODULE/accd.sh
                        fi
                    """.trimIndent(), 10)
                    ModernAccDaemon.start()
                }
            }
            if (ex is CancellationException) throw ex
            throw IOException("ACC recovery failed. Backup: $backup", ex)
        } finally {
            local.delete()
            withContext(NonCancellable) { RootShell.execScript("rm -f ${RootShell.quote(staged)}", 5) }
        }
    }

    private fun requireSuccess(success: Boolean, backup: String) {
        if (!success) throw IOException("ACC recovery verification failed. Backup: $backup")
    }
}
