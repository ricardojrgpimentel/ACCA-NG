package mattecarra.accapp.acc

import mattecarra.accapp.acc.v202107280.AccHandler
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PrintedConfigContractTest {
    private val handler = AccHandler(202610095, NgEngineContract(1, 202310160, emptySet()))

    @Test fun quotedManualSwitchIsUnwrappedAndStillForced() {
        for (quote in listOf("'", "\"", "")) {
            val config = "charging_switch=${quote}battery/charging_enabled 1 0 --$quote # selected\r\n"
            assertEquals("battery/charging_enabled 1 0", handler.getCurrentChargingSwitch(config))
            assertFalse(handler.isAutomaticSwitchEnabled(config))
        }
    }

    @Test fun trailingFlagsInOtherCommandsDoNotForceAnAutomaticSwitch() {
        val config = "apply_on_boot=printf --\ncharging_switch=battery/charging_enabled 1 0"
        assertTrue(handler.isAutomaticSwitchEnabled(config))
    }

    @Test fun hookQuotesHashesAndCommentBoundariesArePreserved() {
        val hook = "printf '# keep'; echo 'a' 'b'"
        assertEquals(hook, handler.parseConfig("apply_on_boot=$hook # comment").configOnBoot)
        assertEquals("echo path#fragment", handler.parseConfig("apply_on_plug='echo path#fragment'").configOnPlug)
        assertEquals("'a' 'b'", AccOutput.configValue("apply_on_boot='a' 'b'", "apply_on_boot"))
        assertNull(handler.parseConfig("apply_on_boot=''\napply_on_plug=\"\"").configOnBoot)
    }

    @Test fun negativeShutdownSentinelIsNotReplacedWithZero() {
        assertEquals(-1, handler.parseConfig("shutdown_capacity=-1\nresume_capacity=50\npause_capacity=60").configCapacity.shutdown)
    }

    @Test fun commandArgumentsPreserveLiteralShellSyntaxWithoutExecutingIt() {
        val root = Files.createTempDirectory("acc hook ").toFile()
        try {
            val sentinel = root.resolve("must-not-exist")
            val hook = "printf '# keep'; echo \"\$HOME\"; \$(touch ${sentinel.absolutePath}); echo 'quoted'"
            val executable = root.resolve("acca").apply {
                writeText("#!/bin/sh\nprintf '%s\\n' \"\$@\"\n")
                setExecutable(true)
            }
            for (command in listOf(handler.getUpdateAccOnBootCommand(hook), handler.getUpdateAccOnPluggedCommand(hook))) {
                val process = ProcessBuilder("sh", "-c", command.replace("/dev/.vr25/acc/acca", "'${executable.absolutePath}'")).start()
                val arguments = process.inputStream.bufferedReader().readLines()
                assertEquals(0, process.waitFor())
                assertEquals(2, arguments.size)
                assertEquals("-s", arguments[0])
                assertEquals(hook, arguments[1].substringAfter('='))
                assertFalse(sentinel.exists())
            }
        } finally { root.deleteRecursively() }
    }
}
