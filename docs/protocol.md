# Peregrine Protocol Specification

**Version**: 1  
**Format**: JSON over WebSocket (`ws://` / `wss://`)  
**Shared Schemas**: `protocol/schemas/`  
**Test Fixtures**: `protocol/fixtures/messages/`  

---

## 1. Top-Level Message Envelope

Every message exchanged across WebSocket channels (`/ws/device` and `/ws/console`) wraps a typed payload inside a standard envelope:

```json
{
  "v": 1,
  "id": "a0000000-0000-0000-0000-000000000001",
  "type": "hello",
  "ts": 1774000000000,
  "payload": { ... }
}
```

| Field | Type | Description |
|---|---|---|
| `v` | integer | Protocol major version (currently `1`). |
| `id` | string (UUIDv4) | Unique message identifier for tracing and idempotency. |
| `type` | string | Message type discriminator. |
| `ts` | integer (Unix ms) | Sender emission timestamp. |
| `payload` | object | Message-specific payload matching the JSON Schema for `type`. |

---

## 2. Core Message Types

### 2.1 `hello` (Device -> Server)
Sent immediately upon WebSocket connection establishment to register device capabilities and standby mode.

```json
{
  "v": 1,
  "id": "hello-dev-pixel7-01",
  "type": "hello",
  "ts": 1774000000000,
  "payload": {
    "deviceId": "dev-pixel7-01",
    "model": "Google Pixel 7",
    "osVersion": "Android 14",
    "appVersion": "0.1.0",
    "capabilities": ["mock_location", "overlay"],
    "remoteStandby": true
  }
}
```

### 2.2 `telemetry.batch` (Device -> Server)
Batch of consecutive kinematic fixes emitted at regular intervals ($1\text{ Hz}$ to $10\text{ Hz}$).

```json
{
  "v": 1,
  "id": "batch-dev-pixel7-01-42",
  "type": "telemetry.batch",
  "ts": 1774000002000,
  "payload": {
    "deviceId": "dev-pixel7-01",
    "seq": 42,
    "sendTs": 1774000002000,
    "points": [
      {
        "lat": 37.774929,
        "lng": -122.419416,
        "altitude": 15.2,
        "speed": 5.8,
        "bearing": 182.4,
        "accuracy": 2.1,
        "ts": 1774000001500,
        "verticalAccuracy": 3.0,
        "speedAccuracy": 0.2,
        "bearingAccuracy": 1.5,
        "zoneId": "open-sky-default"
      }
    ]
  }
}
```

### 2.3 `command` (Server -> Device)
Dispatched from Web Console to trigger device actions.

```json
{
  "v": 1,
  "id": "cmd-env-001",
  "type": "command",
  "ts": 1774000003000,
  "payload": {
    "commandId": "cmd-001",
    "name": "start_mock",
    "idempotencyKey": "idem-key-991823",
    "ttlMs": 60000,
    "issuedAt": 1774000003000,
    "params": {
      "mode": "once",
      "profile": "drive",
      "speedMultiplier": 1.0,
      "routeId": "route-market-st"
    }
  }
}
```

### 2.4 `command.ack` (Device -> Server)
Device response confirming execution, rejection, or error.

```json
{
  "v": 1,
  "id": "ack-cmd-001",
  "type": "command.ack",
  "ts": 1774000003250,
  "payload": {
    "commandId": "cmd-001",
    "deviceId": "dev-pixel7-01",
    "status": "acked",
    "executedAt": 1774000003240,
    "error": null
  }
}
```

### 2.5 `time.sync` (Bidirectional)
NTP-style clock offset calibration.

```json
{
  "v": 1,
  "id": "ts-sync-01",
  "type": "time.sync",
  "ts": 1774000000020,
  "payload": {
    "clientSendTs": 1774000000000,
    "serverReceiveTs": 1774000000010,
    "serverSendTs": 1774000000020
  }
}
```

### 2.6 `device.update` (Server -> Console)
Real-time fleet state fan-out to web console maps (throttled at $2\text{ Hz}$).

```json
{
  "v": 1,
  "id": "up-dev-01",
  "type": "device.update",
  "ts": 1774000002100,
  "payload": {
    "deviceId": "dev-pixel7-01",
    "status": "mocking",
    "lastSeen": 1774000002000,
    "lat": 37.774929,
    "lng": -122.419416,
    "altitude": 15.2,
    "speed": 5.8,
    "bearing": 182.4,
    "accuracy": 2.1,
    "batteryPct": 85,
    "remoteControlEnabled": true,
    "remoteStandby": true
  }
}
```
