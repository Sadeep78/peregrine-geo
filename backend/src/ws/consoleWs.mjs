/**
 * Manages active web console WebSocket connections and throttled telemetry fan-out.
 */
class ConsoleWsManager {
  constructor() {
    this.consoles = new Set(); // Set of active console WebSockets
    this.lastBroadcastPerDevice = new Map(); // deviceId -> last broadcast timestamp
    this.minBroadcastIntervalMs = 500; // Throttled to at most 2 Hz per device
  }

  handleConnection(ws, req) {
    this.consoles.add(ws);

    ws.on('close', () => {
      this.consoles.delete(ws);
    });
  }

  broadcast(organizationId, message) {
    const raw = typeof message === 'string' ? message : JSON.stringify(message);
    for (const ws of this.consoles) {
      if (ws.readyState === 1) { // OPEN
        ws.send(raw);
      }
    }
  }

  broadcastDeviceUpdate(organizationId, updatePayload) {
    const now = Date.now();
    const last = this.lastBroadcastPerDevice.get(updatePayload.deviceId) || 0;

    // Throttle to at most 2 Hz (every 500ms) unless status changed
    if (now - last < this.minBroadcastIntervalMs && updatePayload.status !== 'disconnected') {
      return;
    }

    this.lastBroadcastPerDevice.set(updatePayload.deviceId, now);

    this.broadcast(organizationId, {
      v: 1,
      id: `dev-up-${now}`,
      type: 'device.update',
      ts: now,
      payload: updatePayload,
    });
  }
}

export const consoleManager = new ConsoleWsManager();
