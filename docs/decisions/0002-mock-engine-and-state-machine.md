# 0002. Mock Engine and State Machine Architecture

Date: 2026-10-22

## Status

Accepted

## Context

Android location mocking requires coordination between two distinct subsystems:
1. Android OS framework `LocationManager` test providers (`LocationManager.GPS_PROVIDER` and `LocationManager.NETWORK_PROVIDER`).
2. Google Play Services `FusedLocationProviderClient` mock mode (`setMockMode(true)` and `setMockLocation()`).

Additionally, apps inspecting location may detect inconsistencies if:
- GPS provider is mocked but Network provider is real or missing.
- Timestamps (`time` vs `elapsedRealtimeNanos`) diverge or lack monotonic consistency.
- The app transitions between mock targets unexpectedly without user confirmation.
- Process death occurs while mocking a route, causing the user to lose their simulation state.

We must define:
- The dual-provider injection architecture.
- The strict `MockStateMachine` lifecycle and Preview vs Active separation.
- The route recovery persistence mechanism.

## Decision

1. **Dual Mock Injection Architecture**:
   - `DualMockProviderManager` coordinates all injection.
   - When active, it registers test providers for both `GPS_PROVIDER` and `NETWORK_PROVIDER` using accurate properties (power requirement, accuracy, cell/network capability).
   - Fused location client is initialized with `setMockMode(true)` prior to injecting samples.
   - A single writer coroutine produces each simulated location sample. Both LocationManager providers and the Fused client receive the same timestamped sample simultaneously.
   - NETWORK samples are assigned a larger configurable accuracy radius (default 15m) to mirror real-world cell/Wi-Fi triangulation, while GPS samples reflect simulated GPS accuracy.
   - If `addTestProvider` throws `SecurityException`, it is mapped to a `NotMockApp` state which directs the user to the onboarding checklist.
   - On stop or unexpected error, test providers are explicitly removed and `setMockMode(false)` is invoked.

2. **Strict State Machine (`MockStateMachine`)**:
   - States: `Idle`, `Preview`, `Active(Playing | Paused)`, `Error`, and `NotMockApp`.
   - `Preview` and `Active` are strictly separate immutable state models to ensure no shared rendering or mutation artifacts occur between previewing a prospective route and actively driving one.
   - Switching from an existing `Active` mock to a new one requires displaying a **Confirmation Diff Dialog** showing `Current` vs `New` (target, profile, speed, noise, duration).

3. **Route Recovery Strategy**:
   - The current active route and progress (distance traveled, current waypoint index, speed multiplier) are persisted to local storage at regular checkpoints (every 2 seconds) and on state transitions.
   - The user preference `Recover route` supports three modes:
     - `Off`: Does not resume on app relaunch.
     - `Ask`: Prompts user with a dialog to resume or discard after unexpected restart.
     - `Auto`: Seamlessly resumes mock from the last recorded checkpoint.

4. **Visual Language and Accessibility**:
   - Preview state is rendered in Teal (`#00897B`).
   - Active Paused state is rendered with an Amber pulse (`#FFA000`).
   - Active Playing state is rendered with an Emerald pulse (`#2E7D32`).
   - All state indicators pair color with distinct icons and TalkBack text labels to ensure accessibility (WCAG AA).
   - Touch targets are enforced to $\ge 48\text{ dp}$.

## Consequences

### Positive
- Robust mock coverage: Apps reading from LocationManager or FusedLocationProvider receive synchronized fixes.
- Fail-safe teardown: test providers are cleaned up on stop or process exit.
- Clear separation between preview and active playback prevents accidental location jumps.
- Reliable crash recovery.

### Negative / Tradeoffs
- Dual injection requires maintaining mock state in multiple providers simultaneously.

### Neutral / Notes
- The mock engine does NOT conceal mock location flags, abiding strictly by Android standards and product non-goals.

## Compliance and Traceability

- Related User Stories: US-01, US-02, US-03, US-09
- Related Phase Gate: Phase 1 Android core
- Verification Method: Unit and state machine tests; DualMockProviderManager mock verification.
