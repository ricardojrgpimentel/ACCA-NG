package mattecarra.accapp.acc

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalPowerSupplyTest {
    private fun read(supplies: Map<String, Pair<String, String?>>): String {
        val root = Files.createTempDirectory("power supply ").toFile()
        try {
            supplies.forEach { (name, readings) ->
                val supply = root.resolve(name).apply { mkdir() }
                supply.resolve("online").writeText(readings.first)
                readings.second?.let { supply.resolve("type").writeText(it) }
            }
            val process = ProcessBuilder("sh", "-c", ExternalPowerSupply.script(root.absolutePath)).start()
            return process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
        } finally { root.deleteRecursively() }
    }

    @Test fun samsungBatteryEnumAndOtgAreNotIncomingPower() {
        assertEquals("online=false", read(mapOf("battery" to ("1" to null),
            "otg" to ("1" to null), "ac" to ("0" to "Mains"), "usb" to ("0" to "USB"))))
    }

    @Test fun connectedExternalChargerWinsOverDisconnectedSupplies() {
        assertEquals("online=true", read(mapOf("battery" to ("19" to null),
            "ac" to ("1" to "Mains"), "usb" to ("0" to "USB"))))
    }

    @Test fun differentlyNamedBatteryIsExcludedByItsType() {
        assertEquals("online=false", read(mapOf("main_battery" to ("1" to "Battery"),
            "wireless" to ("0" to "Wireless"))))
    }

    @Test fun unknownExternalReadingsDoNotAuthorizeCalibration() {
        assertEquals("", read(mapOf("battery" to ("1" to null))))
        assertEquals("", read(mapOf("usb" to ("2" to "USB"))))
    }
}
