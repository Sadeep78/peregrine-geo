import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../../../backend/src/server.mjs';
import { DeviceSimulator } from '../src/simulator.mjs';

test('Device Simulator: 500 virtual devices @ 1 Hz high-scale ingest benchmark', async (t) => {
  const testPort = 4050;
  const backend = createServer({ port: testPort });
  await backend.listen();

  try {
    const simulator = new DeviceSimulator({
      count: 500,
      rateHz: 1,
      durationSec: 5,
      serverUrl: `ws://localhost:${testPort}/ws/device`,
      staggerMs: 2,
    });

    await simulator.start();
    const metrics = simulator.getMetrics();

    console.log('\n--- 500-Device Ingest Benchmark Results ---');
    console.log(`Connected: ${metrics.connectedDevices}`);
    console.log(`Total Sent: ${metrics.totalSent}, Total Acked: ${metrics.totalAcked}`);
    console.log(`Errors: ${metrics.errors}`);
    console.log(`RTT (ms) - p50: ${metrics.rtt.p50}, p90: ${metrics.rtt.p90}, p95: ${metrics.rtt.p95}, p99: ${metrics.rtt.p99}, max: ${metrics.rtt.max}`);
    console.log(`Server Ingest (ms) - p50: ${metrics.serverIngest.p50}, p90: ${metrics.serverIngest.p90}, p95: ${metrics.serverIngest.p95}, p99: ${metrics.serverIngest.p99}`);

    assert.equal(metrics.connectedDevices, 500, 'All 500 devices must connect successfully');
    assert.equal(metrics.errors, 0, 'Zero network or parse errors allowed');
    assert.ok(metrics.totalAcked >= 2000, `Expected at least 2000 acks, got ${metrics.totalAcked}`);
    assert.ok(metrics.rtt.p95 < 100, `Acceptance criterion: p95 RTT must be < 100ms, got ${metrics.rtt.p95}ms`);
    assert.ok(metrics.serverIngest.p95 < 50, `Server internal ingest p95 must be < 50ms, got ${metrics.serverIngest.p95}ms`);
  } finally {
    await backend.close();
  }
});
