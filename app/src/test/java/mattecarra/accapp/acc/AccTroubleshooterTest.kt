package mattecarra.accapp.acc

import org.junit.Assert.*
import org.junit.Test

class AccTroubleshooterTest {
    @Test fun rawConfigAndSensorScaleAreReadWithoutExecutingConfig() {
        val s = AccTroubleshooter.parse("""
            __CONFIG__
            configVerCode=202310160
            capacity=(2 101 50 60 false false)
            dischargePolarity='-'
            battStatusWorkaround=true
            ampFactor=1000000
            chargingSwitch=(battery/batt_slate_mode 0 1)
            __MODULE__
            version=202310160
            hash=abc123
            __SOURCE__
            ctrl_charging() { _ge_pause_cap; _le_resume_cap; }
            __POWER__
            status=Discharging
            capacity=98
            current=-250000
            factor=1000
            sensor=true
            online=false
            daemon=0
        """.trimIndent())
        assertTrue(s.available)
        assertEquals(true, s.running)
        assertEquals(60, s.pause)
        assertEquals(50, s.resume)
        assertEquals("-", s.polarity)
        assertEquals(-250.0, s.currentMa!!, .001)
        assertFalse(s.fixedLimits)
    }

    @Test fun missingDataIsNotReportedAsHealthyOrCalibratable() {
        val s = AccTroubleshooter.parse("permission denied")
        assertFalse(s.available)
        assertNull(s.running)
        assertFalse(s.hasCurrentSensor)
        assertNull(s.online)
    }
    @Test fun diagnosticsIncludeSeparateHardwareControlStatus() {
        val s = AccTroubleshooter.parse("""
            __CONFIG__
            configVerCode=202310160
            __MODULE__
            version=202610106
            __SOURCE__
            __POWER__
            status=Charging
            daemon=0
            __LIMITS__
            powerLimitsVersion=1
            current.requested=500
            current.supported=true
            current.state=failed
            voltage.requested=default
            voltage.supported=false
            voltage.state=off
        """.trimIndent())
        assertTrue(s.available)
        assertEquals(true, s.running)
        assertEquals(PowerLimitState.FAILED, s.powerLimits?.current?.state)
    }

}
