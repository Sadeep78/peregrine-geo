package dev.peregrinegeo.peregrine.feature.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickMathTest {

    private val maxRadius = 100.0
    private val maxSpeed = 20.0 // 20 m/s

    @Test
    fun `dead zone 10 percent filters small displacements to zero speed`() {
        // Center displacement
        val out0 = JoystickMath.calculate(0.0, 0.0, maxRadius, maxSpeed)
        assertTrue(out0.inDeadZone)
        assertEquals(0.0, out0.speedMps, 1e-4)
        assertEquals(0.0, out0.deflectionFraction, 1e-4)

        // Inside dead zone (r = 7.07 < 10)
        val out1 = JoystickMath.calculate(5.0, 5.0, maxRadius, maxSpeed)
        assertTrue(out1.inDeadZone)
        assertEquals(0.0, out1.speedMps, 1e-4)

        // Exactly at dead zone boundary (r = 10)
        val out2 = JoystickMath.calculate(10.0, 0.0, maxRadius, maxSpeed)
        assertTrue(out2.inDeadZone)
        assertEquals(0.0, out2.speedMps, 1e-4)

        // Just outside dead zone (r = 10.5 > 10)
        val out3 = JoystickMath.calculate(10.5, 0.0, maxRadius, maxSpeed)
        assertFalse(out3.inDeadZone)
        assertTrue("Speed should be > 0 just outside dead zone", out3.speedMps > 0.0)
    }

    @Test
    fun `quadratic scaling provides smooth low speed sensitivity and reaches max at full deflection`() {
        // Active range is 10 to 100 (range = 90)
        // Midpoint of active range: r = 10 + 45 = 55.0 -> deflection = 0.5
        // v = maxSpeed * (0.5)^2 = 20.0 * 0.25 = 5.0 m/s
        val outMid = JoystickMath.calculate(0.0, -55.0, maxRadius, maxSpeed)
        assertEquals(0.5, outMid.deflectionFraction, 1e-4)
        assertEquals(5.0, outMid.speedMps, 1e-4)

        // 25% of active range: r = 10 + 22.5 = 32.5 -> deflection = 0.25
        // v = 20.0 * (0.25)^2 = 1.25 m/s (fine control)
        val outLow = JoystickMath.calculate(0.0, -32.5, maxRadius, maxSpeed)
        assertEquals(0.25, outLow.deflectionFraction, 1e-4)
        assertEquals(1.25, outLow.speedMps, 1e-4)

        // Full deflection: r = 100.0 -> deflection = 1.0 -> v = 20.0 m/s
        val outFull = JoystickMath.calculate(0.0, -100.0, maxRadius, maxSpeed)
        assertEquals(1.0, outFull.deflectionFraction, 1e-4)
        assertEquals(20.0, outFull.speedMps, 1e-4)

        // Over-deflection clamped to max
        val outOver = JoystickMath.calculate(0.0, -150.0, maxRadius, maxSpeed)
        assertEquals(1.0, outOver.deflectionFraction, 1e-4)
        assertEquals(20.0, outOver.speedMps, 1e-4)
    }

    @Test
    fun `angle calculations correctly map four quadrants in screen coordinates`() {
        // Up = North (0 deg)
        val outUp = JoystickMath.calculate(0.0, -50.0, maxRadius, maxSpeed)
        assertEquals(0.0, outUp.bearingDeg, 1e-2)

        // Right = East (90 deg)
        val outRight = JoystickMath.calculate(50.0, 0.0, maxRadius, maxSpeed)
        assertEquals(90.0, outRight.bearingDeg, 1e-2)

        // Down = South (180 deg)
        val outDown = JoystickMath.calculate(0.0, 50.0, maxRadius, maxSpeed)
        assertEquals(180.0, outDown.bearingDeg, 1e-2)

        // Left = West (270 deg)
        val outLeft = JoystickMath.calculate(-50.0, 0.0, maxRadius, maxSpeed)
        assertEquals(270.0, outLeft.bearingDeg, 1e-2)

        // Up-Right = North-East (45 deg)
        val outNE = JoystickMath.calculate(50.0, -50.0, maxRadius, maxSpeed)
        assertEquals(45.0, outNE.bearingDeg, 1e-2)
    }

    @Test
    fun `heading relative mode adds deflection angle to current vehicle heading`() {
        val vehicleHeading = 90.0 // Heading East

        // Pushing joystick Up (forward) maintains 90 deg
        val outFwd = JoystickMath.calculate(
            dx = 0.0, dy = -50.0, maxRadius = maxRadius, maxSpeedMps = maxSpeed,
            mode = JoystickMode.HEADING_RELATIVE, currentHeadingDeg = vehicleHeading
        )
        assertEquals(90.0, outFwd.bearingDeg, 1e-2)

        // Pushing joystick Right (turn 90 deg starboard) -> 180 deg (South)
        val outRight = JoystickMath.calculate(
            dx = 50.0, dy = 0.0, maxRadius = maxRadius, maxSpeedMps = maxSpeed,
            mode = JoystickMode.HEADING_RELATIVE, currentHeadingDeg = vehicleHeading
        )
        assertEquals(180.0, outRight.bearingDeg, 1e-2)

        // Pushing joystick Down (reverse) -> 270 deg (West)
        val outRev = JoystickMath.calculate(
            dx = 0.0, dy = 50.0, maxRadius = maxRadius, maxSpeedMps = maxSpeed,
            mode = JoystickMode.HEADING_RELATIVE, currentHeadingDeg = vehicleHeading
        )
        assertEquals(270.0, outRev.bearingDeg, 1e-2)
    }

    @Test
    fun `road snapped mode aligns bearing to road angle or reverse road angle`() {
        val roadBearing = 40.0 // Road runs at 40 deg / 220 deg

        // Joystick aimed at 35 deg (close to 40 deg forward)
        val snappedFwd = JoystickMath.snapToRoad(35.0, roadBearing)
        assertEquals(40.0, snappedFwd, 1e-2)

        // Joystick aimed at 215 deg (close to 220 deg reverse)
        val snappedRev = JoystickMath.snapToRoad(215.0, roadBearing)
        assertEquals(220.0, snappedRev, 1e-2)
    }
}
