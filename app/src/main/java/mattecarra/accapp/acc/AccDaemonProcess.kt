package mattecarra.accapp.acc

data class AccDaemonProcess(val pid: Int, val command: String) {
    val isTestWorker: Boolean
        get() = command.split(' ').getOrNull(1)?.substringAfterLast('/') !in listOf("accd.sh", "accd")

    companion object {
        fun parse(line: String): AccDaemonProcess? {
            val fields = line.trim().split(Regex("\\s+"), limit = 2)
            val pid = fields.firstOrNull()?.toIntOrNull()?.takeIf { it > 1 } ?: return null
            val command = fields.getOrNull(1) ?: return null
            val args = command.split(Regex("\\s+"))
            if (args.firstOrNull() !in listOf("sh", "/system/bin/sh")) return null
            val path = args.getOrNull(1) ?: return null
            val daemon = path in listOf("/data/adb/vr25/acc/accd.sh", "/data/adb/modules/acc/accd.sh", "/dev/.vr25/acc/accd.sh", "/dev/.vr25/acc/accd")
            val testWorker = path in listOf("/dev/.vr25/acc/acc", "/data/adb/vr25/acc/acc.sh", "/data/adb/modules/acc/acc.sh") &&
                args.drop(2).any { it == "--test" || it.matches(Regex("-t[0-9]{0,2}")) }
            return if (daemon || testWorker) AccDaemonProcess(pid, "sh " + args.drop(1).joinToString(" ")) else null
        }
    }
}
