# 0003. Kinematic Engine and Noise Model Architecture

Date: 2026-10-22

## Status

Accepted

## Context

Simulating realistic GNSS location movements requires moving beyond naive constant-step movement along polyline points. Real-world vehicles and pedestrians:
1. Accelerate and decelerate gradually within physiological or mechanical limits ($a_{\text{acc}}$, $a_{\text{dec}}$).
2. Must slow down before sharp corners to respect lateral acceleration limits ($a_{\text{lat\_max}}$) and road geometry.
3. Come to a smooth stop at their destination in `Once` mode, or transition seamlessly through vertex loops in `Loop` mode.
4. Experience realistic GNSS noise: not purely white Gaussian noise (which jumps erratically), but a correlated random walk (Ornstein-Uhlenbeck / Gauss-Markov process) creating slow wander, overlaid with small white jitter, multipath bursts, and variable reported accuracy.
5. Encounter signal dropouts, degraded reception, and tunnel dead-reckoning with uncertainty blowup.

## Decision

1. **Pure Domain Kinematics Module**:
   - Implemented in `core/kinematics/` in pure Kotlin with zero Android, framework, or network dependencies.
   - Deterministic execution: for a given route, motion profile, noise profile, and seed, the simulation output is bit-for-bit identical across runs.
   - Clocks are abstracted via a `TimeSource` interface.

2. **Speed Profile Planning (Forward-Backward Passes)**:
   - For every vertex $i$, compute turn angle $\theta_i$ and local radius of curvature $R_i$ using the circumradius of 3 consecutive points $(p_{i-1}, p_i, p_{i+1})$, smoothed over adjacent segments.
   - Vertex speed cap:
     $$v_{\text{lim}, i} = \min\left(v_{\text{target}}, \sqrt{a_{\text{lat\_max}} \cdot R_i}\right)$$
     Turns sharper than $45^\circ$ receive a profile corner cap $v_{\text{corner}}$ (Drive: 5 m/s, Bike: 3, Run: 2, Walk: 1).
   - **Backward pass** (enforcing deceleration limit before corners and end):
     $$v_i = \min\left(v_{\text{lim}, i}, \sqrt{v_{i+1}^2 + 2 \cdot a_{\text{dec\_max}} \cdot d_i}\right)$$
     In `Once` mode, $v_{\text{last}} = 0$. In `Loop` mode, the loop junction is treated as a continuous vertex.
   - **Forward pass** (enforcing acceleration limit from start and out of corners):
     $$v_{i+1} = \min\left(v_{i+1}, \sqrt{v_i^2 + 2 \cdot a_{\text{acc\_max}} \cdot d_i}\right)$$

3. **Time-Stepped Integration**:
   - Fixed simulation tick $dt = 1 / \text{tickHz}$ (1 to 10 Hz).
   - Distance traveled $\Delta s$ is integrated per tick. Mid-route speed multiplier adjustments are ramped smoothly within $[ -a_{\text{dec}}, +a_{\text{acc}} ]$.
   - Geodesic interpolation along the polyline using WGS-84 geodesic math.

4. **Gauss-Markov (Ornstein-Uhlenbeck) Noise Model**:
   - Output position error is computed in a local East-North tangent frame:
     $$x_{t+dt} = x_t \cdot e^{-dt / \tau} + \sigma \sqrt{1 - e^{-2 dt / \tau}} \cdot W_t + \sigma_w \cdot J_t$$
     where $\tau = 20\text{ s}$, $W_t \sim \mathcal{N}(0, 1)$, and $J_t \sim \mathcal{N}(0, 1)$ is white jitter.
   - Presets: `Ideal` ($\sigma = 0$), `OpenSky` ($\sigma = 1.5\text{ m}$), `Suburban` ($\sigma = 3.0\text{ m}$), `UrbanCanyon` ($\sigma = 5.0\text{ m}$ + multipath bursts up to $15\text{ m}$).
   - Reported horizontal accuracy is proportional to injected error ($1.5\sigma$ to $2\sigma$ with smooth slow variation), never a constant.
   - True speed and bearing are preserved; bearing is held below $0.5\text{ m/s}$.

5. **Signal Zones**:
   - Supports `Degraded`, `Dropout` (`StopUpdates`, `RepeatLast`, `AccuracyBlowup`), and `Tunnel` (dead-reckoned continuation with expanding accuracy circle, followed by re-acquisition position jump).

## Consequences

### Positive
- Physically realistic mock movement adhering strictly to vehicle and pedestrian dynamics.
- Eliminates impossible cornering speeds and abrupt acceleration spikes.
- Golden test vectors allow cross-validation between Kotlin engine and TypeScript Web console port.
- Microsecond-scale step execution ($< 0.2\text{ ms}$ per tick).

### Negative / Tradeoffs
- Forward-backward planner requires precomputing vertex properties before playback begins.

## Compliance and Traceability

- Related User Stories: US-02, US-04, US-05
- Related Phase Gate: Phase 2 Kinematics and routing
- Verification Method: Property-based tests, golden vector tests, performance benchmarks.
