# Peregrine (`peregrine-geo`)

Android mock-location client, kinematic simulation engine, and cloud fleet console.

Peregrine enables mobile developers and QA engineers to place Android devices at arbitrary locations, replay realistic kinematic routes with genuine vehicle dynamics and GNSS noise models, and coordinate multi-device scenarios from a web console—all using official Android mock-location APIs without root.

---

## Architecture & Technology Stack

- **Android App (`app/`)**: Kotlin 2.x, Jetpack Compose, Material 3, Clean Architecture + MVI, MapLibre Native Android, Room, DataStore, OkHttp.
- **Protocol (`protocol/`)**: JSON Schema definitions for WebSocket and REST payloads, golden kinematic test vectors, contract fixtures.
- **Backend (`backend/`)**: Node.js 22+, TypeScript strict, Hono (`@hono/node-server`, `@hono/node-ws`), Zod validation, multi-tenant RBAC, SSRF protection.
- **Web Console (`web/`)**: Next.js App Router, TypeScript strict, Tailwind CSS, MapLibre GL JS, TanStack Query, Recharts.
- **Device Simulator (`tools/device-sim`)**: High-scale device simulation CLI benchmarking up to 500+ virtual devices at 1 Hz.

---

## Quick Start & Developer Commands

Peregrine uses `just` as the single command surface:

```bash
# Verify entire monorepo (clean-room, lint, typecheck, unit tests, E2E benchmarks, build)
just verify

# Run all test suites across workspaces
just test

# Lint protocol schemas and configs
just lint

# Compile and package protocol and distribution bundles
just build

# Generate production release artifacts with unified version bumping
just release
```

---

## Operating Rules & Non-Goals

1. **Official Android Mock Location APIs Only**: No hiding of the mock flag, no root/Xposed hooks, no Play Integrity/SafetyNet bypass.
2. **Clean-Room License Purity**: Peregrine is clean-room engineered and licensed under Apache-2.0. Zero AGPL code.
3. **Pure Domain Kinematics**: Kinematic calculation engine and math modules (`core/kinematics/`) contain zero Android framework or network imports.
4. **Multi-Tenant Scoping**: All backend and WebSocket operations are strictly scoped by organization ID.

For detailed development guidelines and agent instructions, see [AGENTS.md](file:///C:/Users/DELL/Desktop/Peregrine/AGENTS.md).
