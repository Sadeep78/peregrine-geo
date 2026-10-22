package dev.peregrinegeo.peregrine.core.kinematics

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.location.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinematicEngineTest {

    private val straightWalkPoints = listOf(
        RoutePoint(37.774900, -122.419400, ele = 10.0),
        RoutePoint(37.775900, -122.419400, ele = 10.0)
    )

    private val turnDrivePoints = listOf(
        RoutePoint(37.770000, -122.420000, ele = 5.0),
        RoutePoint(37.775000, -122.420000, ele = 5.0),
        RoutePoint(37.775000, -122.415000, ele = 5.0)
    )

    @Test
    fun testPropertyBasedKinematicConstraints() {
        val planned = SpeedProfilePlanner.plan(turnDrivePoints, MotionProfile.Drive, PlayMode.Once)
        val noise = NoiseModel(NoisePreset.Ideal, seed = 42L)
        val engine = KinematicMovementEngine(planned, noise, tickHz = 10)

        val dt = engine.dtSeconds
        var prevSpeed = 0.0
        var prevDist = 0.0
        val maxAccTolerance = (MotionProfile.Drive.aAcc * dt) + 1e-4
        val maxDecTolerance = (MotionProfile.Drive.aDec * dt) + 1e-4

        var steps = 0
        while (!engine.isCompleted && steps < 5000) {
            val output = engine.step()
            steps++

            // 1. Speed never exceeds vTarget
            assertTrue(
                "Speed ${output.trueSpeedMps} exceeded vTarget ${MotionProfile.Drive.vTarget}",
                output.trueSpeedMps <= MotionProfile.Drive.vTarget + 1e-3
            )

            // 2. Acceleration within limits
            if (output.trueSpeedMps > prevSpeed) {
                val deltaV = output.trueSpeedMps - prevSpeed
                assertTrue(
                    "Acceleration deltaV=$deltaV exceeded maxAccTolerance=$maxAccTolerance",
                    deltaV <= maxAccTolerance
                )
            }

            // 3. Deceleration within limits (while actively traveling prior to final stop)
            if (output.trueSpeedMps < prevSpeed && !output.isCompleted) {
                val deltaV = prevSpeed - output.trueSpeedMps
                assertTrue(
                    "Deceleration deltaV=$deltaV exceeded maxDecTolerance=$maxDecTolerance",
                    deltaV <= maxDecTolerance
                )
            }

            // 4. Distance traveled increases monotonically and never exceeds total
            assertTrue(output.distanceTraveledMeters >= prevDist - 1e-4)
            assertTrue(output.distanceTraveledMeters <= planned.totalDistanceMeters + 1e-2)

            prevSpeed = output.trueSpeedMps
            prevDist = output.distanceTraveledMeters
        }

        assertTrue("Simulation must complete", engine.isCompleted)
    }

    @Test
    fun testDeterministicGoldenVectors() {
        // Run with identical seed twice -> must produce identical output
        val planned = SpeedProfilePlanner.plan(straightWalkPoints, MotionProfile.Walk, PlayMode.Once)

        val run1Outputs = mutableListOf<KinematicEngineOutput>()
        val timeSource1 = SteppedTimeSource(currentMs = 1774000000000L, currentNanos = 1000000000L)
        val engine1 = KinematicMovementEngine(
            planned,
            NoiseModel(NoisePreset.OpenSky, seed = 12345L),
            timeSource = timeSource1,
            tickHz = 10
        )

        for (i in 0 until 50) {
            run1Outputs.add(engine1.step())
            timeSource1.advance(100)
        }

        val run2Outputs = mutableListOf<KinematicEngineOutput>()
        val timeSource2 = SteppedTimeSource(currentMs = 1774000000000L, currentNanos = 1000000000L)
        val engine2 = KinematicMovementEngine(
            planned,
            NoiseModel(NoisePreset.OpenSky, seed = 12345L),
            timeSource = timeSource2,
            tickHz = 10
        )

        for (i in 0 until 50) {
            run2Outputs.add(engine2.step())
            timeSource2.advance(100)
        }

        assertEquals(run1Outputs.size, run2Outputs.size)
        for (i in 0 until run1Outputs.size) {
            val o1 = run1Outputs[i]
            val o2 = run2Outputs[i]
            assertEquals("Step $i true lat mismatch", o1.truePosition.latitude, o2.truePosition.latitude, 1e-7)
            assertEquals("Step $i true lon mismatch", o1.truePosition.longitude, o2.truePosition.longitude, 1e-7)
            assertEquals("Step $i noisy lat mismatch", o1.noisyPosition.latitude, o2.noisyPosition.latitude, 1e-7)
            assertEquals("Step $i noisy lon mismatch", o1.noisyPosition.longitude, o2.noisyPosition.longitude, 1e-7)
            assertEquals("Step $i speed mismatch", o1.reportedSpeedMps, o2.reportedSpeedMps, 1e-4)
            assertEquals("Step $i accuracy mismatch", o1.horizontalAccuracyMeters, o2.horizontalAccuracyMeters, 1e-4)
        }
    }

    @Test
    fun testVerifyGoldenVectorsAgainstFixture() {
        // Suite 1: walk_straight_ideal_seed100
        val planned1 = SpeedProfilePlanner.plan(straightWalkPoints, MotionProfile.Walk, PlayMode.Once)
        val engine1 = KinematicMovementEngine(planned1, NoiseModel(NoisePreset.Ideal, seed = 100L), tickHz = 10)

        val step0 = engine1.step()
        assertEquals(37.7749001, step0.truePosition.latitude, 1e-6)
        assertEquals(-122.4194000, step0.truePosition.longitude, 1e-6)
        assertEquals(0.08, step0.trueSpeedMps, 0.01)

        for (i in 1 until 10) engine1.step()
        val step10 = engine1.step()
        assertEquals(37.7749047, step10.truePosition.latitude, 1e-6)
        assertEquals(-122.4194000, step10.truePosition.longitude, 1e-6)
        assertEquals(0.88, step10.trueSpeedMps, 0.01)

        // Suite 2: drive_90deg_turn_opensky_seed42
        val planned2 = SpeedProfilePlanner.plan(turnDrivePoints, MotionProfile.Drive, PlayMode.Once)
        val engine2 = KinematicMovementEngine(planned2, NoiseModel(NoisePreset.OpenSky, seed = 42L), tickHz = 10)

        val driveStep0 = engine2.step()
        assertEquals(37.7700012, driveStep0.noisyPosition.latitude, 1e-5)
        assertEquals(-122.4199977, driveStep0.noisyPosition.longitude, 1e-5)
        assertEquals(0.17, driveStep0.reportedSpeedMps, 0.05)

        for (i in 1 until 10) engine2.step()
        val driveStep10 = engine2.step()
        assertEquals(37.7700133, driveStep10.noisyPosition.latitude, 1e-5)
        assertEquals(-122.4200024, driveStep10.noisyPosition.longitude, 1e-5)
        assertEquals(2.84, driveStep10.reportedSpeedMps, 0.05)
    }

    @Test
    fun testSignalZonesDegradedAndDropout() {
        val planned = SpeedProfilePlanner.plan(straightWalkPoints, MotionProfile.Walk, PlayMode.Once)
        val zone = SignalZone(
            id = "tunnel-zone",
            name = "Subway Tunnel",
            startDistanceMeters = 20.0,
            endDistanceMeters = 60.0,
            fadeDistanceMeters = 5.0,
            mode = SignalZoneMode.Dropout(DropoutBehavior.RepeatLast)
        )
        val evaluator = SignalZoneEvaluator(listOf(zone))
        val engine = KinematicMovementEngine(planned, NoiseModel(NoisePreset.Ideal), zoneEvaluator = evaluator, tickHz = 10)

        var enteredZone = false
        var exitedZone = false

        for (i in 0 until 1500) {
            if (engine.isCompleted) break
            val out = engine.step()
            if (out.activeZoneId == "tunnel-zone") {
                enteredZone = true
            } else if (enteredZone && out.distanceTraveledMeters > 65.0) {
                exitedZone = true
            }
        }

        assertTrue("Must enter tunnel zone", enteredZone)
        assertTrue("Must exit tunnel zone", exitedZone)
        assertTrue("Transition events must be logged", evaluator.transitionEvents.size >= 2)
    }

    @Test
    fun testPerformanceBudgetUnder02MsPerStep() {
        val planned = SpeedProfilePlanner.plan(turnDrivePoints, MotionProfile.Drive, PlayMode.Once)
        val engine = KinematicMovementEngine(planned, NoiseModel(NoisePreset.UrbanCanyon, seed = 999L), tickHz = 10)

        // Warm up JIT
        for (i in 0 until 100) engine.step()

        val iterations = 500
        val startNanos = System.nanoTime()
        for (i in 0 until iterations) {
            engine.step()
        }
        val elapsedNanos = System.nanoTime() - startNanos
        val avgMsPerStep = (elapsedNanos / iterations) / 1_000_000.0

        println("Performance benchmark: Average step time = ${String.format("%.4f", avgMsPerStep)} ms")
        assertTrue("Average engine step time must be under 0.2 ms, got: $avgMsPerStep ms", avgMsPerStep < 0.2)
    }

    @Test
    fun testAntimeridianAndPolarEdgeCases() {
        // 1. Antimeridian crossing (179.99 to -179.99)
        val antimeridianPoints = listOf(
            RoutePoint(0.0, 179.99),
            RoutePoint(0.0, -179.99)
        )
        val plannedAntimeridian = SpeedProfilePlanner.plan(antimeridianPoints, MotionProfile.Drive, PlayMode.Once)
        val engineAntimeridian = KinematicMovementEngine(plannedAntimeridian, NoiseModel(NoisePreset.Ideal), tickHz = 10)
        assertTrue(plannedAntimeridian.totalDistanceMeters > 0.0)
        assertTrue(plannedAntimeridian.totalDistanceMeters < 50_000.0) // ~22 km

        var steps = 0
        while (!engineAntimeridian.isCompleted && steps < 2500) {
            engineAntimeridian.step()
            steps++
        }
        assertTrue("Antimeridian crossing must complete without loop", engineAntimeridian.isCompleted)

        // 2. Polar route (lat 85 to 86)
        val polarPoints = listOf(
            RoutePoint(85.0, 0.0),
            RoutePoint(86.0, 0.0)
        )
        val plannedPolar = SpeedProfilePlanner.plan(polarPoints, MotionProfile.Walk, PlayMode.Once)
        val enginePolar = KinematicMovementEngine(plannedPolar, NoiseModel(NoisePreset.Ideal), tickHz = 10)
        val polarOut = enginePolar.step()
        assertTrue(polarOut.truePosition.latitude >= 85.0)

        // 3. Duplicate and zero-length segment
        val duplicatePoints = listOf(
            RoutePoint(37.7700, -122.4100),
            RoutePoint(37.7700, -122.4100), // identical point
            RoutePoint(37.7705, -122.4100)
        )
        val plannedDup = SpeedProfilePlanner.plan(duplicatePoints, MotionProfile.Walk, PlayMode.Once)
        val engineDup = KinematicMovementEngine(plannedDup, NoiseModel(NoisePreset.Ideal), tickHz = 10)
        assertTrue(plannedDup.totalDistanceMeters > 0.0)
        var dupSteps = 0
        while (!engineDup.isCompleted && dupSteps < 2000) {
            engineDup.step()
            dupSteps++
        }
        assertTrue("Duplicate points route must complete", engineDup.isCompleted)
    }

    @Test
    fun testNoiseStatisticsOverLongRun() {
        val planned = SpeedProfilePlanner.plan(straightWalkPoints, MotionProfile.Walk, PlayMode.Once)
        val noiseModel = NoiseModel(NoisePreset.OpenSky, seed = 777L)
        val engine = KinematicMovementEngine(planned, noiseModel, tickHz = 10)

        val errors = mutableListOf<Double>()
        val accuracies = mutableSetOf<Double>()

        for (i in 0 until 1000) {
            val out = engine.step()
            val distError = GeoMath.distanceMeters(out.truePosition, out.noisyPosition)
            errors.add(distError)
            accuracies.add(Math.round(out.horizontalAccuracyMeters * 100.0) / 100.0)
        }

        val meanError = errors.average()
        val variance = errors.map { (it - meanError) * (it - meanError) }.average()
        val stdDev = Math.sqrt(variance)

        // OpenSky sigma is 1.5m -> mean radial error should be roughly around 1.0-2.5m
        assertTrue("Mean error should be realistic: $meanError", meanError in 0.5..3.5)
        assertTrue("Standard deviation should be near expected range: $stdDev", stdDev in 0.5..2.5)
        assertTrue("Reported accuracy must not be constant over 1000 steps", accuracies.size >= 10)
    }
}
