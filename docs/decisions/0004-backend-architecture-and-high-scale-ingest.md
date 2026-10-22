# 0004. Backend Architecture, WebSocket Transport, and Ingest Pipeline

Date: 2026-10-22

## Status

Accepted

## Context

Peregrine backend services must coordinate three high-throughput, low-latency workloads:
1. Bidirectional real-time device communication over WebSocket (`/ws/device`) supporting hundreds to thousands of concurrent Android devices transmitting telemetry batches at 1 Hz.
2. Web console fan-out over WebSocket (`/ws/console`) with throttling ($\le 2\text{ Hz}$ per device) to avoid saturating browser rendering pipelines.
3. Multi-tenant REST API (`/api/v1`) providing authentication, device pairing, command queuing, multi-device scenario synchronization, and geofence evaluation.

The system must guarantee:
- p95 telemetry ingestion latency under $100\text{ ms}$ at 500+ concurrent devices at 1 Hz.
- Command idempotency and strict lifecycle states (`queued` → `sent` → `delivered` → `acked` / `failed` / `expired`).
- Secure webhook dispatching with SSRF protection and HMAC-SHA256 signatures.

## Decision

1. **Framework & Runtime**:
   - Node.js 22+ with strict TypeScript.
   - Hono (`@hono/node-server` and `@hono/node-ws`) for unified HTTP and WebSocket handling. Hono provides lightweight footprint, zero external bloat, and sub-millisecond route dispatching.
   - Zod validation for all API inputs and protocol envelopes.

2. **Data & Multi-Tenancy Architecture**:
   - Every database query is strictly scoped by `organizationId`.
   - Data models: `Organization`, `User`, `Membership` (roles: `owner`, `admin`, `member`, `viewer`), `Device`, `Route`, `Scenario`, `ScenarioRun`, `Command`, `TelemetryPoint`, `Geofence`, `Webhook`, `WebhookDelivery`, and `AuditLog`.
   - PostgreSQL 16 + PostGIS for spatial queries (`ST_DWithin`, `ST_Contains`).

3. **WebSocket Ingest & Console Fan-Out**:
   - `/ws/device`: Devices authenticate with opaque device tokens issued during 8-character pairing. Devices maintain heartbeat and send 1 Hz `telemetry.batch` messages.
   - `/ws/console`: Consoles subscribe to real-time organization updates. Ingested telemetry points are broadcast with throttling to at most 2 Hz per device to prevent client overhead.

4. **Geofence Hysteresis & SSRF Defense**:
   - Geofence evaluations use an exit hysteresis margin (default 10 meters) and minimum dwell time (3 consecutive fixes) to prevent flapping caused by GNSS noise.
   - Webhook dispatcher enforces SSRF protections: blocks private/loopback/link-local IPv4/IPv6 ranges (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`), disallows redirects, and validates resolved IP at connection time.

5. **Device Simulator CLI (`tools/device-sim`)**:
   - Independent Node.js CLI tool emulating $N$ Android devices streaming the real protocol.
   - Measures end-to-end telemetry ingestion round-trip latency and computes percentiles (p50, p90, p95, p99).

## Consequences

### Positive
- Sub-10ms baseline telemetry ingestion latency.
- Real-time synchronization between mobile fleet and web console.
- Robust security boundaries preventing cross-tenant leakage and SSRF exploits.

### Negative / Tradeoffs
- High-scale WebSocket connections require careful file descriptor and memory management when scaling to tens of thousands of devices.

## Compliance and Traceability

- Related User Stories: US-06, US-07, US-08, US-09
- Related Phase Gate: Phase 3 Backend
- Verification Method: Integration test suite and 500-device 1 Hz load test benchmark.
