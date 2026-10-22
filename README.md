# 🦅 Peregrine (`peregrine-geo`)

<div align="center">

**Enterprise-Grade Android Mock-Location Client, Kinematic Simulation Engine & Cloud Fleet Console**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Android-API_29%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Node](https://img.shields.io/badge/Node.js-22%2B-339933.svg?logo=nodedotjs&logoColor=white)](https://nodejs.org)
[![Tests](https://img.shields.io/badge/Tests-102_Passed-success.svg)](docs/audit/2026-10-22/report.md)
[![Security](https://img.shields.io/badge/Security-SSRF%20%7C%20IDOR%20Hardened-green.svg)](docs/threat-model.md)
[![Architecture](https://img.shields.io/badge/Architecture-Clean_%2B_MVI-informational.svg)](AGENTS.md)

[Features](#-key-features) • [Architecture](#-system-architecture) • [Quick Start](#-quick-start) • [Audit & Verification](#-audit--verification) • [Non-Goals](#-strict-non-goals--security) • [License](#-license)

---

</div>

## 📖 Overview

**Peregrine** is a specialized engineering and QA testing platform designed for mobile developers, field testers, and logistics engineering teams. It allows developers to simulate realistic device motion along complex road networks, reproduce sensor-level edge cases (tunnels, multipath GNSS noise, urban canyons), and orchestrate synchronized multi-device fleet scenarios in real time—**using 100% official Android mock-location APIs without requiring root privileges.**

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 🚗 **Physics-Based Kinematics** | Realistic velocity profiles, turn-based corner deceleration ($v_{\text{corner}} = \min(v_{\text{target}}, \sqrt{a_{\text{lat,max}} \cdot R})$), acceleration/jerk limits across Walk, Bicycle, and Drive modes. |
| 📍 **Dual-Provider Mock Engine** | Injects identical timestamps, bearings, altitudes, and dynamic accuracy into both `gps` and `network` system providers via Android's `LocationManager`. |
| 📡 **Realistic GNSS Noise Models** | Continuous-time Ornstein-Uhlenbeck drift, white measurement jitter, multipath bursts, and environmental presets (Open Sky, Urban Canyon, Deep Urban, Tunnel attenuation). |
| 🌐 **Real-Time Fleet Console** | Web console (Next.js + MapLibre GL JS) for tracking live device telemetry ($\le 2\text{ Hz}$ throttle, sub-10ms latency) and triggering synchronized multi-vehicle scenarios. |
| 🕹️ **Floating Overlay & Joystick** | On-screen PIP/bubble HUD with deflection-sensitive joystick for interactive manual steering and speed modulation. |
| 📁 **Universal File I/O** | Native GPX, KML, and GeoJSON track import and export with built-in Billion-Laughs XML entity defense and 0.1% round-trip fidelity. |
| 🛡️ **Hardened Multi-Tenancy** | Strict organization-scoped RBAC, IDOR-protected command pipelines, and SSRF-hardened webhook delivery with HMAC-SHA256 signatures. |

---

## 🏗️ System Architecture

Peregrine is structured as a modular clean-room monorepo:

```
peregrine/
├── 📱 app/                    # Android application (dev.peregrinegeo.peregrine)
│   ├── core/
│   │   ├── kinematics/        # Pure Kotlin motion & noise simulation engine (zero Android deps)
│   │   ├── location/          # Dual mock provider manager & state machine
│   │   ├── routing/           # OSRM client & route snapping
│   │   ├── geo/               # Geodesic math, CoordParser & MapsLinkResolver
│   │   ├── fileio/            # GPX, KML & GeoJSON parsers
│   │   ├── network/           # TLS WebSocket transport & offline telemetry FIFO queue
│   │   └── database/          # Room DB & DataStore preferences
│   └── feature/               # Jetpack Compose MVI feature screens & Floating Overlay HUD
├── 📜 protocol/               # Single source of truth JSON schemas & golden test vectors
├── 🖥️ backend/                # Node.js 22 + TypeScript Hono REST & WebSocket microservices
├── 🗺️ web/                    # Next.js App Router fleet console & Route Studio preview
├── 🚀 tools/device-sim/       # High-scale simulator benchmarking 500+ virtual devices @ 1 Hz
└── 📚 docs/                   # Architecture Decision Records (ADRs) & Audit Reports
    ├── decisions/             # ADR-0001 through ADR-0007
    └── audit/                 # Traceability matrix, baseline, and audit reports
```

---

## ⚡ Quick Start

Peregrine uses **`just`** as the unified command surface across all workspaces:

### Prerequisites

- **Node.js**: v22.x or later
- **Java Development Kit**: JDK 17 or later (for Android & Kotlin compilation)
- **Just**: Command runner (`cargo install just` or `choco install just`)
- **Docker** *(optional)*: For running local Postgres/PostGIS, Redis, and OSRM containers

### Common Developer Commands

```powershell
# 1. Verify everything (lint, typecheck, unit tests, E2E benchmarks, clean-room checks)
just verify

# 2. Run all unit & integration test suites
just test

# 3. Lint JSON protocol schemas
just lint

# 4. Compile distribution targets & type declarations
just build

# 5. Spin up local cloud dependencies (Postgres + PostGIS, Redis, OSRM)
just cloud-up

# 6. Generate production release bundle with unified semver bumping
just release
```

---

## 🔬 Audit & Verification

Peregrine underwent a comprehensive, clean-room quality and security audit:

- **Automated Test Results**: **102 tests passing** (86 Kotlin engine/service tests, 16 Node integration/E2E/contract tests).
- **Flakiness Verification**: 0.0% flakiness over 3 consecutive full-suite test cycles.
- **Performance Benchmark**: Kinematic movement engine completes 1,000 steps in **0.0051 ms per step** (39x faster than the 0.2 ms budget).
- **High-Scale Ingest**: Device simulator benchmark sustained 500 concurrent virtual devices streaming 1 Hz telemetry with **p95 latency of 3 ms** and 0 dropped packets.
- **Dependency Audit**: `npm audit` reported **0 vulnerabilities**.

Detailed reports:
- 📊 [Full Audit Report (2026-10-22)](docs/audit/2026-10-22/report.md)
- 🗺️ [User Story Traceability Matrix (US-01 to US-09)](docs/audit/traceability.md)
- 📲 [Manual Hardware & ADB Smoke Testing Guide](docs/audit/manual-smoke.md)

---

## 🛡️ Strict Non-Goals & Security

Peregrine is strictly engineered as a professional development and testing tool.
The following are hard architectural non-goals:

- ❌ **NO Mock-Flag Hiding**: Peregrine never conceals Android's official mock location status.
- ❌ **NO Root / Xposed Hooks**: Operates entirely within standard Android OS user-space permissions.
- ❌ **NO Play Integrity / SafetyNet Bypasses**: Third-party applications and attestation APIs can detect mock locations as designed by Android.
- ❌ **NO Game / Consumer Exploits**: Features are exclusively designed for enterprise QA, logistics routing, and software verification.

---

## 📄 License

Peregrine is distributed under the **Apache License, Version 2.0**.  
See the [LICENSE](LICENSE) file for complete terms and copyright notices.

```
Copyright 2026 Peregrine Geo Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
