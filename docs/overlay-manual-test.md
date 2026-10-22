# Peregrine Overlay HUD & Floating Joystick - Manual Verification Guide

This document provides QA engineers and developers with the step-by-step verification checklist for the Phase 6 Floating Window & Overlay Joystick (`SYSTEM_ALERT_WINDOW`).

---

## 1. Permission Flow Verification
1. Launch **Peregrine** on a device or emulator (Android 10+ / API 29+).
2. Navigate to **Settings > Floating Overlay**.
3. Verify live permission indicator:
   - If not yet granted, status displays `Permission Required` in amber text.
   - Deep link button `Grant Overlay Permission` is enabled.
4. Tap `Grant Overlay Permission`:
   - System settings screen opens directly to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for `dev.peregrinegeo.peregrine`.
   - Enable "Allow display over other apps".
5. Return to Peregrine:
   - Permission status updates in real-time to `Granted` with green checkmark.

---

## 2. Floating Mini-HUD & Joystick Verification
1. Toggle `Enable Floating Overlay` to **ON**.
2. Minimize Peregrine and open target application under test (e.g., Google Maps or test ride-hailing app).
3. Verify floating window layout:
   - **Mini-HUD**: Displays current simulated coordinates (`Lat`, `Lng`), instantaneous speed (`m/s`), bearing (`deg`), and active profile name (`Drive`, `Bike`, `Run`, `Walk`).
   - **Playback Controls**: Floating Play/Pause and Stop buttons directly control the `MockStateMachine`.
   - **Floating Joystick**: Draggable circular thumb inside boundary ring with clear center mark.

---

## 3. Joystick Kinematic Touch Math
1. **Dead Zone Check**:
   - Touch and drag the joystick handle slightly within the inner 10% radius ring.
   - Verify speed remains strictly `0.0 m/s` (no jitter or micro-drift).
2. **Quadratic Scaling**:
   - Deflect handle slightly outside dead zone: verify fine-grained speed control below `1.0 m/s` (enabling delicate vehicle parking and walking maneuvers).
   - Deflect handle to full outer boundary: verify speed reaches configured `v_target` (e.g., 18.0 m/s for Drive profile).
3. **Spring-to-Center**:
   - Lift finger: verify handle animates smoothly back to center and speed returns to `0.0 m/s`.
4. **Heading Reference Modes**:
   - Switch mode to `North Relative`: Up on joystick moves device due North (0°).
   - Switch mode to `Heading Relative`: Up on joystick moves forward along current vehicle heading; Right turns 90° clockwise.

---

## 4. Edge Docking & Opacity
1. **Opacity Slider**:
   - Drag opacity slider to 20%: controls become transparent, keeping underlying map visible.
   - Drag opacity slider to 100%: controls become fully opaque.
2. **Minimize to Bubble**:
   - Tap minimize button: HUD collapses to a small circular bubble showing app icon and pulsing status ring.
3. **Edge Docking Snap**:
   - Drag bubble towards the left screen edge: on release, snaps cleanly to the left screen boundary.
   - Drag bubble towards the right screen edge: on release, snaps cleanly to the right screen boundary.
   - Tap bubble: expands back to full Mini-HUD.
