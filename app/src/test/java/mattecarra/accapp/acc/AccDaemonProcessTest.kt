package mattecarra.accapp.acc

import org.junit.Assert.*
import org.junit.Test

class AccDaemonProcessTest {
    @Test fun recognisesOnlyKnownDaemonAndChargingTestProcesses() {
        assertEquals(22312, AccDaemonProcess.parse("22312 sh /data/adb/vr25/acc/accd.sh /data/adb/vr25/acc-data/config.txt")?.pid)
        assertEquals("sh /data/adb/vr25/acc/accd.sh", AccDaemonProcess.parse("22312 /system/bin/sh /data/adb/vr25/acc/accd.sh")?.command)
        assertNotNull(AccDaemonProcess.parse("12800 sh /dev/.vr25/acc/acc /data/adb/vr25/acc-data/config.txt -t --"))
        assertFalse(AccDaemonProcess.parse("22312 sh /data/adb/vr25/acc/accd.sh")!!.isTestWorker)
        assertTrue(AccDaemonProcess.parse("12800 sh /dev/.vr25/acc/acc -t --")!!.isTestWorker)
    }

    @Test fun doesNotTargetOtherAppsReadersOrSimilarNames() {
        for (line in listOf(
            "23591 com.accang.app.debug", "3801 dev.montra.debug",
            "42 sh /data/adb/other/accd.sh", "42 sh /data/adb/vr25/acc/accd.sh.backup",
            "42 sh /data/adb/vr25/acc/acc.sh -i", "42 sh /dev/.vr25/acc/acc -D",
            "42 sh -c ps -A -o PID,ARGS", "42 tail -F /dev/.vr25/acc/accd.log",
            "1 sh /data/adb/vr25/acc/accd.sh", "PID ARGS", "42 sh")) {
            assertNull(line, AccDaemonProcess.parse(line))
        }
    }
}
