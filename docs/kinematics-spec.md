# Peregrine Kinematic Engine & Simulation Specification

This document provides the formal mathematical specification for the pure Kotlin and TypeScript kinematic movement simulation engine (`core/kinematics/` and `web/src/lib/kinematics/`).

---

## 1. Geodesic Foundation & Route Geometry

All positions are expressed on the WGS-84 reference ellipsoid, using a spherical mean radius approximation $R_{\text{earth}} = 6,371,000.0\text{ m}$.

### 1.1 Distance & Bearing
For two points $P_1(\phi_1, \lambda_1)$ and $P_2(\phi_2, \lambda_2)$:
- **Haversine Distance**:
  $$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos\phi_1 \cos\phi_2 \sin^2\left(\frac{\Delta\lambda}{2}\right)$$
  $$d(P_1, P_2) = 2 R_{\text{earth}} \text{atan2}(\sqrt{a}, \sqrt{1-a})$$
- **Initial Forward Bearing**:
  $$\theta = \text{atan2}\left(\sin\Delta\lambda \cos\phi_2, \cos\phi_1 \sin\phi_2 - \sin\phi_1 \cos\phi_2 \cos\Delta\lambda\right) \pmod{360^\circ}$$

### 1.2 Local Radius of Curvature (Circumradius)
At each interior vertex $P_i$ between $P_{i-1}$ and $P_{i+1}$, with segment lengths $a = d(P_i, P_{i+1})$, $b = d(P_{i-1}, P_{i+1})$, $c = d(P_{i-1}, P_i)$ and semi-perimeter $s = \frac{a+b+c}{2}$:
$$R_{\text{curvature}} = \frac{abc}{4 \sqrt{s(s-a)(s-b)(s-c)}}$$
For collinear points or straight segments, $R_{\text{curvature}}$ is capped at $10,000.0\text{ m}$.

---

## 2. Speed Profile Planner

Rather than stepping at a naive constant speed, Peregrine executes forward and backward kinematic passes:

### 2.1 Vertex Speed Limit
Centripetal acceleration limit:
$$v_{\text{lat}} = \sqrt{a_{\text{lat\_max}} \times R_{\text{curvature}}}$$
$$v_{\text{lim}, i} = \min(v_{\text{target}}, v_{\text{lat}})$$
For turns sharper than $45^\circ$, the corner cap $v_{\text{corner}}$ is enforced:
$$\text{If } \Delta\theta_i > 45^\circ \implies v_{\text{lim}, i} \leftarrow \min(v_{\text{lim}, i}, v_{\text{corner}})$$

### 2.2 Backward Deceleration Pass
Enforces the maximum deceleration limit $a_{\text{dec}}$ so the vehicle begins slowing down before reaching the turn:
- Destination vertex: $v_{\text{planned}, n-1} = 0\text{ m/s}$ (Once mode).
- For $i = n-2$ down to $0$:
  $$v_{\text{planned}, i} = \min\left(v_{\text{lim}, i}, \sqrt{v_{\text{planned}, i+1}^2 + 2 a_{\text{dec}} d_i}\right)$$

### 2.3 Forward Acceleration Pass
Enforces the maximum acceleration limit $a_{\text{acc}}$ when departing a turn or stop:
- $v_{\text{planned}, 0} = v_{\text{lim}, 0}$
- For $i = 0$ to $n-2$:
  $$v_{\text{planned}, i+1} = \min\left(v_{\text{planned}, i+1}, \sqrt{v_{\text{planned}, i}^2 + 2 a_{\text{acc}} d_i}\right)$$

### 2.4 Mid-Segment Braking Cap
At an arbitrary distance $s$ along segment $i$:
$$v_{\text{brake}}(s) = \sqrt{\max\left(0, v_{\text{planned}, i+1}^2 + 2 a_{\text{dec}} (d_{i+1} - s)\right)}$$
$$v_{\text{limit}}(s) = \min(v_{\text{target}}, v_{\text{brake}}(s))$$

---

## 3. Kinematic Time-Stepping Integration

At each simulation tick with step duration $\Delta t = \frac{1}{\text{tickHz}}$ (default $10\text{ Hz} \implies \Delta t = 0.1\text{ s}$):

1. **Velocity Ramping**:
   $$v_{t+\Delta t} = \begin{cases}
   \min(v_{\text{limit}}(s), v_t + a_{\text{acc}} \Delta t) & \text{if } v_t < v_{\text{limit}}(s) \\
   \max(v_{\text{limit}}(s), v_t - a_{\text{dec}} \Delta t) & \text{if } v_t > v_{\text{limit}}(s)
   \end{cases}$$
2. **Distance Integration**:
   $$\Delta s = v_{t+\Delta t} \times \Delta t$$
   $$s_{t+\Delta t} = s_t + \Delta s$$
3. **Polyline Interpolation**:
   Interpolate true coordinate along polyline at distance $s_{t+\Delta t}$ using great-circle interpolation.

---

## 4. Gauss-Markov GNSS Noise Model

True simulated positions are perturbed by a continuous-time first-order Gauss-Markov (Ornstein-Uhlenbeck) stochastic process with correlation time $\tau = 20.0\text{ s}$:

$$\alpha = \exp\left(-\frac{\Delta t}{\tau}\right)$$
$$\sigma_{\text{driving}} = \sigma \sqrt{\max(0, 1 - \alpha^2)}$$
$$e_{\text{north}, t+\Delta t} = \alpha e_{\text{north}, t} + \sigma_{\text{driving}} \mathcal{N}(0, 1) + \eta_{\text{jitter}}$$
$$e_{\text{east}, t+\Delta t} = \alpha e_{\text{east}, t} + \sigma_{\text{driving}} \mathcal{N}(0, 1) + \eta_{\text{jitter}}$$

### Dynamic Accuracy & Bearing Filtering:
- **Horizontal Accuracy**: Computed as a function of instantaneous error + jitter:
  $$\text{accuracy} = 1.5 \sigma + 0.3 \sqrt{e_{\text{north}}^2 + e_{\text{east}}^2} + 0.2 |\mathcal{N}(0, 1)|$$
- **Bearing Dead-Band**: When velocity drops below $0.5\text{ m/s}$, the bearing angle holds its last heading to prevent erratic rotational jitter while stationary.

---

## 5. Signal Zones (Degraded, Dropout, Tunnel)

Signal zones modify GNSS telemetry along designated route intervals:
- **Degraded**: Multiplies horizontal and vertical accuracy error by configured factor ($1.5\times$ to $5.0\times$).
- **Dropout**: Drops GNSS emission completely or repeats last fix with incremented timestamps.
- **Tunnel (Dead Reckoning)**: Simulates inertial dead reckoning inside tunnels; accuracy steadily degrades over distance ($+0.1\text{ m}$ per meter traveled in tunnel) and snaps back to GPS upon exiting.
- **Smooth Transition Fading**: Applies sinusoidal fade over configured `fadeDistanceMeters` at zone boundaries.
