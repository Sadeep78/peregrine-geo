# ADR 0007: Overlay HUD, Floating Joystick Math, and Permissions

## Status
Accepted

## Context
Phase 6 adds the on-screen Overlay HUD and Floating Joystick to the Peregrine Android client (`dev.peregrinegeo.peregrine.feature.overlay`).
The overlay lets QA engineers and developers test mock location movement while interacting with another app on the device screen.
Key requirements:
1. `SYSTEM_ALERT_WINDOW` permission must be requested gracefully with clear user instructions.
2. Floating joystick math must provide fine-grained velocity control at low speeds without jitter.
3. Clean separation between the pure mathematical joystick model and Android `WindowManager` views to allow comprehensive testing.

## Decision

### 1. Pure Joystick Mathematical Model (`JoystickMath.kt`)
- All vector math is implemented in pure Kotlin with zero Android framework imports:
- **Dead Zone**: $10\%$ of radius $R$. Any displacement $r \le 0.10 R$ yields $0\text{ m/s}$.
- **Quadratic Velocity Scaling**:
  $$v(r) = v_{\text{max}} \times \left(\frac{r - r_{\text{dead}}}{R - r_{\text{dead}}}\right)^2$$
  This quadratic response curve provides high sensitivity for micro-adjustments ($< 1\text{ m/s}$) while reaching $v_{\text{max}}$ at full deflection.
- **Angle & Bearing Calculation**:
  $$\theta = \left(\text{atan2}(dx, -dy) \times \frac{180}{\pi} + 360\right) \pmod{360}$$
- **Modes**:
  - `NorthRelative`: Joystick Up $= 0^\circ$ (True North).
  - `HeadingRelative`: Joystick Up $=$ current vehicle heading $\phi$. Displacement angle is added to $\phi$.
  - `RoadSnapped`: Snapped to nearest road bearing.

### 2. Window & View Hierarchy
- Uses `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (API 26+).
- Window flags include `FLAG_NOT_FOCUSABLE` so touches outside the floating controls pass through to the underlying app under test.
- Spring-to-center animation on touch `ACTION_UP` / `ACTION_CANCEL`.
- Edge docking: Releasing the collapsed bubble snaps smoothly to the nearest screen edge (left or right).
- Opacity slider: Ranges from $20\%$ (semi-transparent) to $100\%$ (solid).

### 3. Permission Management
- Checked via `Settings.canDrawOverlays(context)`.
- If missing, launches `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with URI `package:dev.peregrinegeo.peregrine`.

## Consequences
- Guaranteed mathematical correctness and domain purity.
- QA engineers can maneuver devices through tight test routes directly on top of client applications.
