# Peregrine Operations & Self-Hosting Guide

This guide covers running the complete Peregrine cloud infrastructure locally or in self-hosted production environments.

---

## 1. Quick Start with Docker Compose

Peregrine includes a complete development stack configured in `compose.dev.yaml`:
- **PostgreSQL 16 + PostGIS 3.4**: Spatial database on port `5432`.
- **Redis 7**: Cache, rate limiting, and pub/sub on port `6379`.
- **OSRM Backend**: Open Source Routing Machine on port `5000`.
- **Peregrine Backend**: Node.js/Hono API on port `4000`.
- **Peregrine Web Console**: Next.js App Router on port `3000`.

### Launching the Stack:
```bash
just cloud-up
```

### Stopping the Stack:
```bash
just cloud-down
```

---

## 2. OSRM Routing Engine Setup

Peregrine uses self-hosted OSRM to provide car, bicycle, and pedestrian routing without third-party rate limits.

### 2.1 Downloading an OpenStreetMap Extract
Download an `.osm.pbf` extract from Geofabrik (e.g. California or metropolitan area):
```bash
curl -O https://download.geofabrik.de/north-america/us/california-latest.osm.pbf
```

### 2.2 Extracting and Building the Routing Graph
Run the OSRM extract and contract steps using the official Docker image:
```bash
# 1. Extract routing network with car profile
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/car.lua /data/california-latest.osm.pbf

# 2. Partition routing cells
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-partition /data/california-latest.osrm

# 3. Customize edge weights
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-customize /data/california-latest.osrm
```

---

## 3. Elevation Data (SRTM) Setup

Peregrine abstracts elevation behind the `ElevationProvider` interface:
- **Default**: Self-hosted SRTM 30m tile service (running in local container).
- **Fallback**: Flat altitude ($15.0\text{ m}$ above ground).

Public elevation demo servers must **never** be used in production paths. Configure your local elevation tile endpoint in `.env`:
```env
ELEVATION_SERVICE_URL="http://localhost:8080/v1/elevation"
```

---

## 4. Database Migrations & Backup

### Running Migrations:
```bash
npx prisma migrate deploy
```

### Backup PostGIS Database:
```bash
docker exec -t peregrine-postgis pg_dump -U peregrine peregrine_dev > backup-$(date +%Y%m%d).sql
```

### Restore Database:
```bash
cat backup-20261022.sql | docker exec -i peregrine-postgis psql -U peregrine peregrine_dev
```
