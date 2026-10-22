# 0001. Foundation and Architecture Baseline

Date: 2026-10-22

## Status

Accepted

## Context

Peregrine (`peregrine-geo`) requires an integrated monorepo hosting multiple technological targets:
1. Android mobile application (`dev.peregrinegeo.peregrine`) in Kotlin/Jetpack Compose.
2. Pure Kotlin kinematic simulation engine.
3. Node.js/TypeScript backend services (Hono, Prisma, PostgreSQL 16 + PostGIS, Redis 7).
4. Web fleet console (Next.js App Router, Tailwind, MapLibre GL JS).
5. High-scale device simulator CLI (`tools/device-sim`).
6. Cross-platform protocol definitions and shared contract fixtures.

Additionally, Peregrine is inspired by Kestrel (`narumiruna/kestrel`, AGPL-3.0). To ensure clean-room compliance, Peregrine must be licensed Apache-2.0 without copying or paraphrasing any code from Kestrel.

We need to establish:
- The monorepo workspace layout.
- The command surface orchestration (`justfile`).
- The protocol single source of truth format and fixture strategy.
- Local infrastructure dependencies via Docker Compose.

## Decision

1. **Monorepo Layout & Tooling**:
   - npm/pnpm workspaces orchestrate TypeScript targets: `protocol`, `backend`, `web`, and `tools/device-sim`.
   - Android target lives in `app/` using Gradle (Kotlin DSL, Kotlin 2.x, Gradle 8.x).
   - Domain and kinematics modules are isolated in `app/src/main/java/dev/peregrinegeo/peregrine/core/kinematics/` with zero Android or framework imports.

2. **Clean-Room License Enforcement**:
   - Peregrine is licensed under Apache-2.0.
   - All modules, algorithms, and models are written from the ground up strictly based on the Peregrine v2 specification.
   - Clean-room verification checks will be enforced before release.

3. **Protocol Single Source of Truth**:
   - `protocol/schemas/` contains formal JSON Schemas defining the WebSocket envelope `{ v, id, type, ts, payload }` and every message type across device, backend, and console.
   - `protocol/fixtures/` contains concrete JSON test fixtures for every schema plus golden test vectors for the kinematic simulation engine.
   - Contract test suite in `protocol/` validates that all fixtures adhere to their schemas.
   - Backend and Web use TypeScript types generated or validated against these schemas.
   - Android maps these schemas to `kotlinx.serialization` `@Serializable` data classes.

4. **Single Command Surface (`justfile`)**:
   - All developer operations (`verify`, `lint`, `test`, `build`, `cloud-up`, `cloud-down`, `release`) are driven by `justfile`.
   - The justfile uses portable shell scripting capable of running on Windows (PowerShell/cmd) and Linux/macOS runners.
   - In environments where Docker is not yet active, `just cloud-up` validates compose configuration and service requirements gracefully.

5. **Local Cloud Stack**:
   - `compose.dev.yaml` standardizes PostgreSQL 16 + PostGIS (`postgis/postgis:16-3.4`), Redis 7 (`redis:7-alpine`), and OSRM (`ghcr.io/project-osrm/osrm-backend`).
   - `.env.example` documents all required environment variables and sensible local defaults.

## Consequences

### Positive
- Strict separation of concerns while sharing schemas and contract test vectors across Android, Backend, and Web.
- Rapid developer onboarding with a single command interface (`just`).
- Full clean-room compliance with Apache-2.0 licensing.
- Early detection of protocol drifts across client and server.

### Negative / Tradeoffs
- Multi-language repository (Kotlin + TypeScript) requires developers/CI to maintain both JVM and Node toolchains.

### Neutral / Notes
- Android Gradle wrapper and Node workspaces can build independently in CI lanes using path filters.

## Compliance and Traceability

- Related User Stories: US-01 through US-09
- Related Phase Gate: Phase 0 Foundation
- Verification Method: `just verify` and contract test suite validation
