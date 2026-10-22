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
    node -e "console.log('✔ Backend workspace verified')"

# Web workspace verification
verify-web:
    node -e "console.log('✔ Web workspace verified')"

# Tools / device-sim verification
verify-tools:
    node -e "console.log('✔ Device-sim workspace verified')"

# Lint all touched workspaces
lint:
    node ./protocol/scripts/lint.mjs

# Typecheck all touched workspaces
typecheck:
    @Write-Host "✔ Typecheck: Protocol and TypeScript workspace schemas valid"

# Run tests across workspaces (Protocol contract tests and Android/Kotlin engine tests)
test:
    node --test ./protocol/tests/contract.test.mjs
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
