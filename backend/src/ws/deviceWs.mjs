import { db } from '../db/memoryStore.mjs';
import { GeofenceEvaluator } from '../services/geofenceService.mjs';
import { consoleManager } from './consoleWs.mjs';

/**
 * Manages connected device WebSocket connections.
 */
class DeviceWsManager {
  constructor() {
    this.connections = new Map(); // deviceId -> WebSocket
    this.geofenceEvaluator = new GeofenceEvaluator();
  }

  handleConnection(ws, req) {
    let authenticatedDeviceId = null;
    let organizationId = null;

    ws.on('message', (data) => {
      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch {
        ws.send(JSON.stringify({ v: 1, id: 'err', type: 'error', ts: Date.now(), payload: { code: 'INVALID_JSON', message: 'Payload is not valid JSON' } }));
        return;
      }

      const { v, id, type, ts, payload } = msg;

      switch (type) {
        case 'hello': {
          authenticatedDeviceId = payload.deviceId;
          // Look up device in database or register virtual device
          let device = db.devices.get(authenticatedDeviceId);
          if (!device) {
            organizationId = 'org-default';
            device = {
              id: authenticatedDeviceId,
              organizationId,
              name: payload.model || 'Device',
              model: payload.model,
              osVersion: payload.osVersion,
              appVersion: payload.appVersion,
              status: 'connected',
              remoteControlEnabled: true,
              remoteStandby: payload.remoteStandby || false,
              lastSeen: Date.now(),
              createdAt: Date.now(),
            };
            db.devices.set(authenticatedDeviceId, device);
          } else {
            organizationId = device.organizationId;
            device.status = 'connected';
            device.lastSeen = Date.now();
          }

          this.connections.set(authenticatedDeviceId, ws);

          // Reply with time.sync
          ws.send(JSON.stringify({
            v: 1,
            id: `ts-${Date.now()}`,
            type: 'time.sync',
            ts: Date.now(),
            payload: {
              clientSendTs: ts,
              serverReceiveTs: Date.now(),
              serverSendTs: Date.now(),
            },
          }));
          break;
        }

        case 'telemetry.batch': {
          if (!authenticatedDeviceId) {
            authenticatedDeviceId = payload.deviceId || 'dev-unknown';
            organizationId = organizationId || 'org-default';
          }

          const startIngestMs = Date.now();
          db.ingestTelemetryBatch(organizationId, authenticatedDeviceId, payload.seq, payload.points);

          // Evaluate geofences
          for (const pt of payload.points) {
            const geofenceEvents = this.geofenceEvaluator.evaluatePoint(authenticatedDeviceId, pt);
            for (const geoEvt of geofenceEvents) {
              consoleManager.broadcast(organizationId, {
                v: 1,
                id: `geo-${Date.now()}`,
                type: 'geofence.event',
                ts: Date.now(),
                payload: geoEvt,
              });
            }
          }

          // Fan-out device.update to console
          if (payload.points.length > 0) {
            const lastPt = payload.points[payload.points.length - 1];
            consoleManager.broadcastDeviceUpdate(organizationId, {
              deviceId: authenticatedDeviceId,
              status: lastPt.speed > 0.1 ? 'mocking' : 'connected',
              lastSeen: Date.now(),
              lat: lastPt.lat,
              lng: lastPt.lng,
              altitude: lastPt.altitude,
              speed: lastPt.speed,
              bearing: lastPt.bearing,
              accuracy: lastPt.accuracy,
              batteryPct: 85,
              remoteControlEnabled: true,
              remoteStandby: true,
              ingestLatencyMs: Date.now() - startIngestMs,
            });
          }

          // Send immediate ack with latency measurement back to simulator
          ws.send(JSON.stringify({
            v: 1,
            id: id || `ack-${payload.seq}`,
            type: 'telemetry.ack',
            ts: Date.now(),
            payload: {
              seq: payload.seq,
              serverTs: Date.now(),
              sendTs: payload.sendTs,
              ingestDurationMs: Date.now() - startIngestMs,
            },
          }));
          break;
        }

        case 'heartbeat': {
          const dev = db.devices.get(authenticatedDeviceId);
          if (dev) {
            dev.lastSeen = Date.now();
            dev.batteryPct = payload.batteryPct;
            dev.charging = payload.charging;
          }
          break;
        }

        case 'command.ack': {
          db.updateCommandStatus(payload.commandId, payload.status, payload.error);
          consoleManager.broadcast(organizationId, {
            v: 1,
            id: `cmd-up-${Date.now()}`,
            type: 'command.update',
            ts: Date.now(),
            payload: {
              commandId: payload.commandId,
              deviceId: authenticatedDeviceId,
              status: payload.status,
              issuedAt: ts,
              completedAt: payload.executedAt,
              error: payload.error,
            },
          });
          break;
        }

        case 'time.sync': {
          ws.send(JSON.stringify({
            v: 1,
            id: id,
            type: 'time.sync',
            ts: Date.now(),
            payload: {
              clientSendTs: payload.clientSendTs,
              serverReceiveTs: Date.now(),
              serverSendTs: Date.now(),
            },
          }));
          break;
        }

        default:
          break;
      }
    });

    ws.on('close', () => {
      if (authenticatedDeviceId) {
        this.connections.delete(authenticatedDeviceId);
        const dev = db.devices.get(authenticatedDeviceId);
        if (dev) {
          dev.status = 'disconnected';
          dev.lastSeen = Date.now();
          consoleManager.broadcastDeviceUpdate(organizationId, {
            deviceId: authenticatedDeviceId,
            status: 'disconnected',
            lastSeen: Date.now(),
            lat: dev.lastLocation?.lat || 0,
            lng: dev.lastLocation?.lng || 0,
            speed: 0,
            bearing: 0,
            accuracy: 0,
            batteryPct: dev.batteryPct || 0,
            remoteControlEnabled: dev.remoteControlEnabled,
            remoteStandby: dev.remoteStandby,
          });
        }
      }
    });
  }

  sendCommand(deviceId, command) {
    const ws = this.connections.get(deviceId);
    if (!ws || ws.readyState !== 1) { // 1 = OPEN
      return false;
    }

    ws.send(JSON.stringify({
      v: 1,
      id: `env-${command.id}`,
      type: 'command',
      ts: Date.now(),
      payload: {
        commandId: command.id,
        name: command.name,
        idempotencyKey: command.idempotencyKey,
        ttlMs: command.ttlMs,
        issuedAt: command.issuedAt,
        params: command.params,
      },
    }));

    db.updateCommandStatus(command.id, 'sent');
    return true;
  }
}

export const deviceManager = new DeviceWsManager();
