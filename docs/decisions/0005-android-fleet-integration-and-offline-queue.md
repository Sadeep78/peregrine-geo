# ADR 0005: Android Fleet Integration, Offline Queue, and Conflict Resolution

## Status
Accepted

## Context
Phase 4 connects the Android Peregrine client (`dev.peregrinegeo.peregrine`) to the central fleet management backend developed in Phase 3. Real-world Android devices operate over variable network connections (Wi-Fi, 4G/5G, tunnel dropouts). The client must:
1. Maintain a resilient WebSocket connection with exponential backoff and jitter.
2. Store outgoing telemetry points when offline up to a safe memory/disk limit without crashing or exhausting storage.
3. Obey strict privacy and user sovereignty: remote control is opt-in, revocable at any time, and clearly signaled via a persistent UI banner.
4. Resolve conflicts when an operator dispatches a remote route while a local user is actively testing a route.

## Decision

### 1. Connection Lifecycle & Exponential Backoff
- The client connects to `/ws/device` using the paired device token.
- Initial reconnect delay: $1.0\text{ s}$, doubling up to $60.0\text{ s}$ maximum.
- Every retry applies $\pm 20\%$ randomized full jitter:
  $$\Delta t = \min(60.0, 1.0 \times 2^{\text{attempt}}) \times (0.8 + 0.4 \times \text{random}())$$
- Heartbeat ping is dispatched every $30\text{ s}$ with battery level, charging status, and mock state.

### 2. Offline Telemetry Queue
- Telemetry points are queued in an in-memory / persistent buffer during disconnection.
- Maximum queue capacity: **10,000 points** (~2.7 hours at 1 Hz).
- Eviction policy: When the queue reaches 10,000 points, the oldest points are dropped (`dropOldest`) to ensure the latest real-time status is preserved when reconnected.
- Discard metrics are logged and transmitted in the first telemetry batch after reconnect.
- Reconnect drain: Flushed in FIFO order in chunks of up to 50 points per `telemetry.batch` message to avoid exceeding WebSocket frame limits.

### 3. Remote Control Consent & Standby Service
- Remote control is controlled by a persistent preference (`remote_control_enabled`), defaulted to `false`.
- If disabled, any incoming mutative command (`start_mock`, `set_speed`, etc.) is rejected with status `rejected` and reason `REMOTE_CONTROL_DISABLED`.
- When enabled, a persistent high-contrast banner is displayed on the map screen: `REMOTE CONTROL ACTIVE (FLEET)`.
- `RemoteStandbyService` runs a foreground service with a partial WakeLock to keep the device reachable when screen is off.

### 4. Route Conflict Resolution
When an incoming `start_mock` or `load_route` command arrives while a local mock is actively running:
- **Keep mine**: The local mock continues uninterrupted. The remote command is rejected with `REJECTED_BY_DEVICE_USER`.
- **Keep theirs**: The local mock is stopped, the remote route is loaded and executed.
- **Keep both**: The local route is saved to the Library (`FavoriteRoutes`), and the remote route is applied.

## Consequences
- Guarantees zero data loss during short network outages (< 2.7 hours at 1 Hz).
- Prevents unbounded memory growth.
- Enforces strict user sovereignty compliant with product non-goals.
