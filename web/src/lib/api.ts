/**
 * Web Console API client and WebSocket manager.
 */

export interface DeviceSummary {
  id: string;
  name: string;
  model: string;
  osVersion: string;
  status: 'idle' | 'mocking' | 'error' | 'disconnected';
  lastSeen: number;
  lat: number;
  lng: number;
  speed: number;
  bearing: number;
  altitude: number;
  accuracy: number;
  batteryPct: number;
  remoteControlEnabled: boolean;
}

export interface RouteWaypoint {
  lat: number;
  lng: number;
  ele?: number;
}

export class FleetApiClient {
  private baseUrl: string;
  private wsUrl: string;
  private ws: WebSocket | null = null;
  private deviceListeners: ((devices: DeviceSummary[]) => void)[] = [];
  private devicesMap: Map<string, DeviceSummary> = new Map();

  constructor(baseUrl = 'http://localhost:4000', wsUrl = 'ws://localhost:4000/ws/console') {
    this.baseUrl = baseUrl;
    this.wsUrl = wsUrl;
  }

  connectWs(onDevicesUpdate?: (devices: DeviceSummary[]) => void) {
    if (onDevicesUpdate) {
      this.deviceListeners.push(onDevicesUpdate);
    }

    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    try {
      this.ws = new WebSocket(this.wsUrl);

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'device.update') {
            const p = msg.payload;
            const updated: DeviceSummary = {
              id: p.deviceId,
              name: p.deviceId,
              model: 'Android Device',
              osVersion: 'Android 15',
              status: p.status === 'mocking' ? 'mocking' : p.status === 'disconnected' ? 'disconnected' : 'idle',
              lastSeen: p.lastSeen || Date.now(),
              lat: p.lat,
              lng: p.lng,
              speed: p.speed,
              bearing: p.bearing,
              altitude: p.altitude || 0,
              accuracy: p.accuracy || 2.0,
              batteryPct: p.batteryPct || 85,
              remoteControlEnabled: p.remoteControlEnabled ?? true,
            };

            this.devicesMap.set(p.deviceId, updated);
            const list = Array.from(this.devicesMap.values());
            for (const listener of this.deviceListeners) {
              listener(list);
            }
          }
        } catch { }
      };

      this.ws.onclose = () => {
        setTimeout(() => this.connectWs(), 3000);
      };
    } catch { }
  }

  async fetchDevices(): Promise<DeviceSummary[]> {
    try {
      const res = await fetch(`${this.baseUrl}/api/v1/devices`);
      if (!res.ok) return [];
      const data = await res.json();
      return (data.items || []).map((d: any) => ({
        id: d.id,
        name: d.name || d.id,
        model: d.model || 'Android Device',
        osVersion: d.osVersion || 'Android',
        status: d.status || 'disconnected',
        lastSeen: d.lastSeen || 0,
        lat: d.lastLocation?.lat || 0,
        lng: d.lastLocation?.lng || 0,
        speed: 0,
        bearing: 0,
        altitude: 0,
        accuracy: 2.0,
        batteryPct: d.batteryPct || 100,
        remoteControlEnabled: d.remoteControlEnabled ?? false,
      }));
    } catch {
      return [];
    }
  }

  async dispatchCommand(deviceId: string, name: string, params: Record<string, any> = {}) {
    const res = await fetch(`${this.baseUrl}/api/v1/commands`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId,
        name,
        params,
        idempotencyKey: `cmd-${Date.now()}-${Math.random().toString(36).substring(7)}`,
      }),
    });
    return res.json();
  }

  async startScenario(deviceIds: string[], route: RouteWaypoint[], motionProfile: any) {
    const res = await fetch(`${this.baseUrl}/api/v1/scenarios/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceIds,
        leadMs: 3000,
        params: {
          waypoints: route,
          motionProfile,
        },
      }),
    });
    return res.json();
  }

  async stopAll() {
    const res = await fetch(`${this.baseUrl}/api/v1/scenarios/stop-all`, {
      method: 'POST',
    });
    return res.json();
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.deviceListeners = [];
  }
}
