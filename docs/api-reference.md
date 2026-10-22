# Peregrine Backend API Reference

**Base URL**: `http://localhost:4000` (or configured environment `$PORT`)  
**Specification**: OpenAPI 3.1 compatible  

---

## 1. Authentication Endpoints

### `POST /api/v1/auth/login`
Authenticates console operators and issues JWT access tokens.

- **Request Body**:
  ```json
  {
    "email": "admin@peregrinegeo.dev",
    "password": "Password123!"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": { "id": "usr-01", "email": "admin@peregrinegeo.dev", "name": "Admin" },
    "organization": { "id": "org-default", "name": "Default Fleet Org" }
  }
  ```

---

## 2. Device Pairing Endpoints

### `POST /api/v1/devices/pairing-code`
Generates an 8-character uppercase pairing code expiring in 10 minutes.

- **Response `201 Created`**:
  ```json
  {
    "code": "PGRN9821",
    "expiresAt": 1774000600000,
    "qrDataUrl": "https://api.peregrinegeo.dev/pair?code=PGRN9821"
  }
  ```

### `POST /api/v1/devices/pair`
Redeems an 8-character pairing code from the Android app and exchanges it for a permanent device authentication token.

- **Request Body**:
  ```json
  {
    "code": "PGRN9821",
    "model": "Google Pixel 8",
    "osVersion": "Android 15",
    "appVersion": "0.1.0"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "deviceToken": "sec-tok-256bit-...",
    "device": {
      "id": "dev-001",
      "organizationId": "org-default",
      "model": "Google Pixel 8",
      "status": "connected"
    }
  }
  ```

---

## 3. Remote Commands Endpoints

### `POST /api/v1/commands`
Dispatches a remote command to an online device.

- **Request Body**:
  ```json
  {
    "deviceId": "dev-001",
    "name": "start_mock",
    "idempotencyKey": "idem-start-9912",
    "ttlMs": 60000,
    "params": {
      "lat": 37.7749,
      "lng": -122.4194,
      "profile": "drive",
      "speedMultiplier": 1.5
    }
  }
  ```
- **Response `201 Created`**:
  ```json
  {
    "id": "cmd-001",
    "deviceId": "dev-001",
    "name": "start_mock",
    "status": "sent",
    "delivered": true
  }
  ```

---

## 4. Multi-Device Scenarios

### `POST /api/v1/scenarios/preflight`
Verifies readiness of all candidate devices before initiating a multi-device scenario (US-07).

- **Request Body**:
  ```json
  {
    "deviceIds": ["dev-driver-01", "dev-rider-02"]
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "ready": true,
    "devices": {
      "dev-driver-01": { "connected": true, "remoteControlEnabled": true, "ready": true },
      "dev-rider-02": { "connected": true, "remoteControlEnabled": true, "ready": true }
    }
  }
  ```

### `POST /api/v1/scenarios/start`
Starts synchronized scenario execution with coordinated lead time.

- **Request Body**:
  ```json
  {
    "deviceIds": ["dev-driver-01", "dev-rider-02"],
    "leadMs": 3000,
    "params": { "scenarioName": "Airport Pickup Convergence" }
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "scenarioRunId": "run-1774000100",
    "startAt": 1774000103000,
    "leadMs": 3000
  }
  ```

---

## 5. Geofences & Webhooks

### `POST /api/v1/geofences`
Creates a radial circle or polygon geofence with hysteresis boundaries.

- **Request Body**:
  ```json
  {
    "name": "Warehouse Depot",
    "type": "circle",
    "geometry": { "lat": 37.7749, "lng": -122.4194 },
    "radiusMeters": 200,
    "webhookId": "wh-01"
  }
  ```

### `POST /api/v1/webhooks/{id}/test`
Triggers an immediate test webhook ping with HMAC-SHA256 signature verification.
