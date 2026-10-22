# ADR 0006: Web Console Architecture and TypeScript Kinematic Engine Port

## Status
Accepted

## Context
Phase 5 implements the Web Fleet Console for team leads, QA engineers, and mobile developers (Personas US-06, US-07, US-08).
Key challenges:
1. Operators creating routes in the browser need an accurate preview of how a real vehicle or pedestrian will move (speed profile, acceleration, decelerations, cornering slowdowns) before pushing scenarios to field devices.
2. The kinematic model in the browser must produce identical coordinates and speeds to the Kotlin Android engine.
3. Fleet telemetry streams from multiple devices concurrently over `/ws/console` and must update the map and detail sheets smoothly at 2 Hz without UI freezes.

## Decision

### 1. TypeScript Kinematic Engine Port
- A strict TypeScript clean-room port of `SpeedProfilePlanner` and `KinematicMovementEngine` is implemented in `web/src/lib/kinematics/`.
- Uses geodesic math (WGS-84 Haversine / spherical law of cosines and forward azimuth).
- Verifies output against `protocol/fixtures/kinematics/golden_vectors.json` to ensure parity within $\le 10^{-4}$ tolerance.

### 2. Web Console Components
- **Fleet Live Map (`FleetMap.tsx`)**:
  - MapLibre GL JS vector map with device markers showing orientation bearing vectors, status badges (idle, mocking, error, offline), battery level, and velocity.
  - Multi-select control enabling batch operations (`start_mock`, `stop_mock`, `set_speed`).
- **Route Studio (`RouteStudio.tsx`)**:
  - Interactive polyline drawing on the map.
  - Local simulation preview: runs the TS kinematic engine at $1\times$ to $5\times$ speed, animating a vehicle marker along the curve to preview turn deceleration and acceleration phases before sending to devices.
- **Device Detail Sheet (`DeviceDetailSheet.tsx`)**:
  - Live telemetry graphs for speed, altitude, and accuracy over time.
  - Command audit trail showing delivery and execution timestamps with latency.
- **Geofence Editor (`GeofenceEditor.tsx`)**:
  - Circle and polygon geofence creation with enter/exit webhook dispatch configuration.

### 3. Console WebSocket Client
- Automatically connects to `/ws/console`.
- Maintains real-time local state dictionary of connected devices.
- Updates device telemetry at 2 Hz with smooth marker interpolation.

## Consequences
- Guaranteed visual parity between Route Studio simulation and real Android device movement.
- Operators can identify unrealistic turn speeds or acceleration jerk before sending scenarios to devices.
