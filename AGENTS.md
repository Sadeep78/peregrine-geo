# AGENTS.md: Rules and Commands for AI Agents

Welcome to **Peregrine** (`peregrine-geo`), an Android mock-location client, kinematic simulation engine, and cloud fleet console.

This document defines the strict operating rules, commands, architectural boundaries, and guidelines for AI coding agents working on this codebase.

---

## 1. Operating Rules for AI Agents

1. **Strict Phase Progression**: Work strictly phase by phase according to Section 5 of the specification. Do not start a phase until the previous phase gate passes. At each gate, provide a comprehensive report detailing:
   - What was built and verified
   - Exact commands executed
   - Test and verification results
   - Open risks or follow-up items
2. **Phase Plan First**: Before writing any implementation code in a phase, post a concise plan of at most one page. Then implement.
3. **ADR-Driven Decisions**: When the specification leaves a design or implementation detail open, choose the simplest option that satisfies acceptance criteria, and record it as an Architecture Decision Record (ADR) in `docs/decisions/NNNN-title.md`. Do not prompt the user with questions unless completely blocked.
4. **Clean-Room Rule (Strict)**: Peregrine is inspired by the open-source app Kestrel (`narumiruna/kestrel`, AGPL-3.0). **Do not copy or paraphrase its source code.** Implement everything strictly from the Peregrine specification and clean-room first principles. Peregrine is licensed Apache-2.0.
5. **Phase Gate Verification**: Every phase must end with `just verify` passing (lint, typecheck, unit tests, contract tests, and builds for all touched workspaces). No phase is complete with failing or skipped tests.
6. **No API Invention**: Never invent API behavior. If an Android framework or external library API is uncertain, write a small spike test or inspect official documentation, then document the result in an ADR.
7. **Domain Purity**: Keep the domain layer strictly pure. No Android framework, network, or external UI/database imports in `core/kinematics/` or pure domain modules.

---

## 2. Product Positioning & Non-Goals

Peregrine is an engineering testing tool for developers and QA engineers.
Peregrine uses **only official Android mock-location mechanisms**.

### Non-Goals (Hard Rules):
- **NO** hiding of the mock flag.
- **NO** root or Xposed hooks.
- **NO** Play Integrity or SafetyNet bypass.
- **NO** features aimed at specific games or consumer apps.
- The UI, manifest, and documentation must clearly state that Peregrine is intended for development and testing, and that third-party apps may detect mock locations.

---

## 3. Technology Stack Locks

| Area | Decision |
|---|---|
| **Android** | Kotlin 2.x, Jetpack Compose + Material 3 (dynamic color, light, dark, OLED), minSdk 29, targetSdk = latest stable, coroutines + Flow, Hilt, Room, DataStore, kotlinx.serialization, OkHttp (REST + WebSocket), MapLibre Native Android |
| **Android Quality** | detekt, spotless (ktlint), JUnit 4, MockK, Turbine, Robolectric |
| **Architecture** | Clean Architecture (presentation, domain, data) + MVI (State, Intent, Effect) |
| **Backend** | Node.js 22+, TypeScript strict, Hono (`@hono/node-server`, `@hono/node-ws`), Zod validation, OpenAPI generated from Zod |
| **Data** | PostgreSQL 16 + PostGIS 3, Prisma (geometry columns as `Unsupported` + raw SQL migrations), Redis 7 (pub/sub, BullMQ, rate limiting, cache) |
| **Routing / Elevation** | Self-hosted OSRM (`foot`, `bike`, `car`) in Docker Compose; `ElevationProvider` interface (self-hosted SRTM default, flat fallback) |
| **Web Console** | Next.js App Router, TypeScript strict, Tailwind CSS, shadcn/ui, MapLibre GL JS, TanStack Query, Recharts |
| **Testing** | Vitest, Testcontainers, Playwright, k6 |
| **Infra & Tooling** | Docker Compose, `justfile` single command surface, GitHub Actions |

Namespace rule: `dev.peregrinegeo.peregrine` is the fixed Android namespace and root package. Do not rename.

---

## 4. Single Command Surface (`justfile`)

All agent commands must execute through or be represented in `justfile`:

- `just verify`: Run linting, typechecks, and tests across all initialized workspaces.
- `just lint`: Check formatting and linting (Node, TypeScript, Android configs).
- `just test`: Run unit, property, and contract tests.
- `just build`: Compile all targets (protocol, backend, web, Android).
- `just cloud-up`: Spin up local Docker development dependencies (Postgres+PostGIS, Redis, OSRM).
- `just cloud-down`: Stop local development containers.
- `just release`: Generate release artifacts with unified version bumping.

---

## 5. Directory Layout

```
peregrine/
├── AGENTS.md                  # This file
├── justfile                   # Developer command surface
├── compose.dev.yaml           # Dev services (PostGIS, Redis, OSRM, Backend, Web)
├── .env.example               # Canonical environment template
├── protocol/                  # Shared protocol schemas, contract fixtures, golden vectors
│   ├── schemas/
│   └── fixtures/
├── app/                       # Android application (dev.peregrinegeo.peregrine)
│   └── src/main/java/dev/peregrinegeo/peregrine/
│       ├── core/
│       │   ├── kinematics/    # Pure Kotlin kinematic movement engine
│       │   ├── location/      # Mock engine & state machine
│       │   ├── routing/       # OSRM & route snapping
│       │   ├── geo/           # Geodesic math & coordinate parsers
│       │   ├── fileio/        # GPX, KML, GeoJSON
│       │   ├── database/      # Room database
│       │   ├── datastore/     # Preferences datastore
│       │   ├── network/       # REST & WebSocket transport
│       │   └── map/           # MapLibre Compose wrapper
│       └── feature/           # MVI feature screens
├── backend/                   # Node/TypeScript Hono backend services
├── web/                       # Next.js web fleet console
├── tools/device-sim/          # Node.js high-scale device simulator CLI
└── docs/                      # Architecture, ADRs, protocol, guides, threat model
    └── decisions/             # Architecture Decision Records
```
