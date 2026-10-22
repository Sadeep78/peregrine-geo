import WebSocket from 'ws';

export class DeviceSimulator {
  constructor(options = {}) {
    this.deviceCount = options.count || 10;
    this.rateHz = options.rateHz || 1;
    this.serverUrl = options.serverUrl || 'ws://localhost:4000/ws/device';
    this.durationSec = options.durationSec || 10;
    this.staggerMs = options.staggerMs !== undefined ? options.staggerMs : 5;

    this.clients = [];
    this.latenciesMs = [];
    this.serverIngestMs = [];
    this.totalBatchesSent = 0;
    this.totalBatchesAcked = 0;
    this.errors = 0;
    this.isRunning = false;
    this.peakConnectedDevices = 0;
    this.timers = [];
  }

  async start() {
    this.isRunning = true;
    console.log(`Starting simulator with ${this.deviceCount} devices connecting to ${this.serverUrl}...`);

    // Connect devices in batches
    const batchSize = 50;
    for (let i = 0; i < this.deviceCount; i += batchSize) {
      if (!this.isRunning) break;
      const sliceEnd = Math.min(i + batchSize, this.deviceCount);
      const batchPromises = [];
      for (let j = i; j < sliceEnd; j++) {
        const deviceId = `sim-dev-${String(j + 1).padStart(4, '0')}`;
        batchPromises.push(this.connectDevice(deviceId, j));
      }
      await Promise.all(batchPromises);
      if (sliceEnd < this.deviceCount && this.staggerMs > 0) {
        await new Promise((r) => setTimeout(r, this.staggerMs * 2));
      }
    }

    this.peakConnectedDevices = this.clients.length;
    console.log(`All ${this.peakConnectedDevices} virtual devices connected successfully.`);

    // Realistic distributed device telemetry:
    // In real life, N devices ticking at rateHz do NOT synchronize at millisecond 0;
    // their clock phases are uniformly distributed across the period (1000 / rateHz ms).
    const periodMs = 1000 / this.rateHz;
    const startTime = Date.now();
    const endTime = startTime + this.durationSec * 1000;

    // Start independent tick loops for each device with phase offset
    for (let i = 0; i < this.clients.length; i++) {
      const client = this.clients[i];
      const phaseOffsetMs = (i * (periodMs / this.deviceCount)) % periodMs;

      let seq = 1;
      const scheduleNextTick = (delayMs) => {
        if (!this.isRunning || Date.now() >= endTime) return;
        const timer = setTimeout(() => {
          if (!this.isRunning) return;
          this.sendDeviceTick(client, seq++);
          scheduleNextTick(periodMs);
        }, delayMs);
        this.timers.push(timer);
      };

      scheduleNextTick(phaseOffsetMs);
    }

    // Wait until test duration finishes
    await new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        if (Date.now() >= endTime || !this.isRunning) {
          clearInterval(checkInterval);
          resolve();
        }
      }, 100);
    });

    // Wait 500ms grace period for remaining in-flight acks
    await new Promise((resolve) => setTimeout(resolve, 500));
    this.stop();
  }

  sendDeviceTick(client, seq) {
    if (client.ws.readyState !== 1) return;
    const now = Date.now();
    client.lat += 0.00005;

    const payload = {
      deviceId: client.deviceId,
      seq,
      sendTs: now,
      points: [
        {
          lat: client.lat,
          lng: client.lng,
          altitude: 15.0,
          speed: client.speed,
          bearing: client.bearing,
          accuracy: 2.0,
          ts: now,
        },
      ],
    };

    const envelope = {
      v: 1,
      id: `batch-${client.deviceId}-${seq}`,
      type: 'telemetry.batch',
      ts: now,
      payload,
    };

    client.ws.send(JSON.stringify(envelope));
    this.totalBatchesSent++;
  }

  connectDevice(deviceId, index) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(this.serverUrl);
      const deviceState = {
        deviceId,
        ws,
        connected: false,
        lat: 37.7749 + (index * 0.0001),
        lng: -122.4194 + (index * 0.0001),
        speed: 5.0,
        bearing: 90.0,
      };

      ws.on('open', () => {
        deviceState.connected = true;
        ws.send(JSON.stringify({
          v: 1,
          id: `hello-${deviceId}`,
          type: 'hello',
          ts: Date.now(),
          payload: {
            deviceId,
            model: `Virtual Android Pixel (Worker #${index})`,
            osVersion: 'Android 15',
            appVersion: '0.1.0',
            capabilities: ['mock_location', 'overlay'],
            remoteStandby: true,
          },
        }));
        this.clients.push(deviceState);
        resolve(deviceState);
      });

      ws.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());
          if (msg.type === 'telemetry.ack') {
            this.totalBatchesAcked++;
            if (msg.payload) {
              if (msg.payload.sendTs) {
                const rtt = Date.now() - msg.payload.sendTs;
                this.latenciesMs.push(rtt);
              }
              if (msg.payload.ingestDurationMs !== undefined) {
                this.serverIngestMs.push(msg.payload.ingestDurationMs);
              }
            }
          } else if (msg.type === 'command') {
            ws.send(JSON.stringify({
              v: 1,
              id: `ack-${msg.payload.commandId}`,
              type: 'command.ack',
              ts: Date.now(),
              payload: {
                commandId: msg.payload.commandId,
                deviceId,
                status: 'acked',
                executedAt: Date.now(),
              },
            }));
          }
        } catch {
          this.errors++;
        }
      });

      ws.on('error', (err) => {
        this.errors++;
        if (!deviceState.connected) {
          reject(err);
        }
      });
    });
  }

  stop() {
    this.isRunning = false;
    for (const timer of this.timers) {
      clearTimeout(timer);
    }
    this.timers = [];

    for (const client of this.clients) {
      try {
        client.ws.close();
      } catch { }
    }
  }

  getMetrics() {
    const computeStats = (arr) => {
      if (arr.length === 0) return { p50: 0, p90: 0, p95: 0, p99: 0, min: 0, max: 0, avg: 0 };
      const sorted = [...arr].sort((a, b) => a - b);
      const percentile = (p) => sorted[Math.floor((p / 100) * (sorted.length - 1))];
      const sum = sorted.reduce((a, b) => a + b, 0);
      return {
        p50: percentile(50),
        p90: percentile(90),
        p95: percentile(95),
        p99: percentile(99),
        min: sorted[0],
        max: sorted[sorted.length - 1],
        avg: Math.round((sum / sorted.length) * 100) / 100,
      };
    };

    const rttStats = computeStats(this.latenciesMs);
    const ingestStats = computeStats(this.serverIngestMs);

    return {
      connectedDevices: this.peakConnectedDevices,
      totalSent: this.totalBatchesSent,
      totalAcked: this.totalBatchesAcked,
      errors: this.errors,
      rtt: rttStats,
      serverIngest: ingestStats,
    };
  }
}
