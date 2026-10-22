# Peregrine User Story Traceability Matrix

This document maps all core user stories (US-01 through US-09) from the **Peregrine Build Specification v2** to concrete, automated test suites and verification cases within the monorepo.

---

## Traceability Matrix

| Story ID | Persona | Story Summary | Automated Verification Test(s) | Phase | Status |
|---|---|---|---|---|---|
| **US-01** | Mobile dev | Pick a point on the map or paste coordinates or a maps link, preview it, then mock it. | • [`CoordParserTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/geo/CoordParserTest.kt)<br>• [`MapsLinkResolverTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/geo/MapsLinkResolverTest.kt)<br>• [`MockStateMachineTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/location/MockStateMachineTest.kt) (`testPreviewSelectsTarget`, `testStartMockTransitionsToActive`) | Phase 1 & 2 | **PASS** |
| **US-02** | Mobile dev | Build a route, choose a travel profile, and watch the device move with realistic speed and turns. | • [`SpeedProfilePlannerTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/kinematics/KinematicEngineTest.kt)<br>• [`KinematicEngineTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/kinematics/KinematicEngineTest.kt) (`testCornerDeceleration`, `testFullRouteTrajectoryProperties`)<br>• [`web/tests/kinematics.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/web/tests/kinematics.test.mjs) | Phase 2 & 5 | **PASS** |
| **US-03** | QA engineer | Replay a recorded GPX track with original timing or constant speed. | • [`FileIoRoundTripTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/fileio/FileIoRoundTripTest.kt) (`testGpxParsingAndTrackPoints`, `testRoundTripGpxExportAndReimport`) | Phase 2 | **PASS** |
| **US-04** | QA engineer | Reproduce a bug exactly by re-running a scenario with the same seed. | • [`KinematicEngineTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/kinematics/KinematicEngineTest.kt) (`testDeterministicSeedReplay`)<br>• [`protocol/tests/contract.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/protocol/tests/contract.test.mjs) (`Kinematics Golden Vectors fixture is valid and complete`) | Phase 0 & 2 | **PASS** |
| **US-05** | QA engineer | Simulate a tunnel or weak-GPS zone on a route. | • [`KinematicEngineTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/kinematics/KinematicEngineTest.kt) (`testNoisePresetsAndMultipath`, `testNoiseStatisticsOverLongRun`) | Phase 2 | **PASS** |
| **US-06** | Team lead | See all test devices live on a map with status, speed, battery, and ETA. | • [`backend/tests/fleet-e2e.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/backend/tests/fleet-e2e.test.mjs)<br>• [`web/tests/route-studio-e2e.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/web/tests/route-studio-e2e.test.mjs) (live telemetry fan-out over `/ws/console`, $\le 2\text{ Hz}$ throttle, latency p50 = 0ms, p95 = 1ms) | Phase 3, 4, 5 | **PASS** |
| **US-07** | Team lead | Start a synchronized multi-device scenario (for example a driver and a customer converging). | • [`backend/tests/integration.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/backend/tests/integration.test.mjs) (`REST API: Scenario preflight, synchronized start, and stop-all`)<br>• [`web/tests/route-studio-e2e.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/web/tests/route-studio-e2e.test.mjs) | Phase 3 & 5 | **PASS** |
| **US-08** | Backend dev | Receive a signed webhook when a device enters or exits a geofence. | • [`backend/tests/integration.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/backend/tests/integration.test.mjs) (`Services: Geofence Evaluator with Hysteresis and Dwell detection`, `Services: Webhook SSRF validation and HMAC signature generation`)<br>• [`backend/tests/ssrf-matrix.test.mjs`](file:///C:/Users/DELL/Desktop/Peregrine/backend/tests/ssrf-matrix.test.mjs) | Phase 3 | **PASS** |
| **US-09** | Device owner | Stay in control: remote control is opt-in, visible, and revocable at any time. | • [`CommandExecutorTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/network/CommandExecutorTest.kt) (`testUserOverridePausesRemoteControl`)<br>• [`FleetMviTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/feature/fleet/FleetMviTest.kt)<br>• [`MockStateMachineTest.kt`](file:///C:/Users/DELL/Desktop/Peregrine/app/src/test/java/dev/peregrinegeo/peregrine/core/location/MockStateMachineTest.kt) (`testSwitchingActiveMockShowsDiffDialog`, `testPauseAndResumeLifecycle`) | Phase 1 & 4 | **PASS** |

---

## Verification Coverage Summary

- **Total Core User Stories**: 9
- **Stories with Automated Tests**: 9 (100% coverage)
- **Failing or Skipped Tests**: 0
