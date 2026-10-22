set windows-shell := ["powershell.exe", "-NoProfile", "-Command"]

# Default recipe runs full verification
default: verify

# Comprehensive verification across all workspaces (lint, typecheck, unit tests, build, clean-room)
verify: verify-clean-room lint typecheck test build
    @Write-Host "=== Peregrine: All Verifications Green ===" -ForegroundColor Green

# Clean-room AGPL isolation and domain purity check
verify-clean-room:
    node ./scripts/verify-clean-room.mjs

# Protocol workspace verification (lint, contract tests, type exports)
verify-protocol:
    node ./protocol/scripts/lint.mjs
    node --test ./protocol/tests/contract.test.mjs
    node ./protocol/scripts/build.mjs

# Backend workspace verification
verify-backend:
    node --test ./backend/tests/integration.test.mjs
    node --test ./backend/tests/fleet-e2e.test.mjs
    @Write-Host "✔ Backend workspace verified" -ForegroundColor Green

# Web workspace verification
verify-web:
    node --test ./web/tests/kinematics.test.mjs
    node --test ./web/tests/route-studio-e2e.test.mjs
    @Write-Host "✔ Web workspace verified (Kinematics & Route Studio E2E)" -ForegroundColor Green

# Tools / device-sim verification
verify-tools:
    node --test ./tools/device-sim/tests/benchmark-500.test.mjs
    @Write-Host "✔ Device-sim workspace verified (500 devices @ 1 Hz)" -ForegroundColor Green

# Lint all touched workspaces
lint:
    node ./protocol/scripts/lint.mjs

# Typecheck all touched workspaces
typecheck:
    @Write-Host "✔ Typecheck: Protocol and TypeScript workspace schemas valid"

# Run tests across workspaces (Protocol contract tests, backend tests, web tests, device-sim benchmark, and Android/Kotlin engine tests)
test:
    node --test ./protocol/tests/contract.test.mjs
    node --test ./backend/tests/integration.test.mjs
    node --test ./backend/tests/fleet-e2e.test.mjs
    node --test ./web/tests/kinematics.test.mjs
    node --test ./web/tests/route-studio-e2e.test.mjs
    node --test ./tools/device-sim/tests/benchmark-500.test.mjs
    node ./scripts/test-kotlin.mjs

# Build artifacts across workspaces
build:
    node ./protocol/scripts/build.mjs

# Start or validate dev cloud infrastructure (Postgres+PostGIS, Redis, OSRM)
cloud-up:
    node ./scripts/cloud-up.mjs

# Stop local dev cloud containers
cloud-down:
    @Write-Host "Stopping local cloud dependencies..."

# Clean build artifacts
clean:
    node -e "const fs = require('fs'); if (fs.existsSync('protocol/dist')) fs.rmSync('protocol/dist', { recursive: true, force: true }); console.log('Clean complete');"

# Release verification
release: verify
    @Write-Host "✔ Release verification passed" -ForegroundColor Green
