import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../../backend/src/server.mjs';
import { DeviceSimulator } from '../../tools/device-sim/src/simulator.mjs';
import { SpeedProfilePlanner } from '../src/lib/kinematics/speedProfilePlanner.mjs';
import { KinematicMovementEngine } from '../src/lib/kinematics/kinematicMovementEngine.mjs';
import WebSocket from 'ws';

test('Phase 5 Gate: Route Studio kinematic preview, fleet scenario dispatch, and live map streaming', async () => {
  const port = 4070;
  const backend = createServer({ port });
  await backend.listen();

  let sim = null;
  let wsConsole = null;

  try {
    // 1. Route Studio: Define route waypoints and motion profile
    const waypoints = [
      { lat: 37.7749, lng: -122.4194 },
      { lat: 37.7790, lng: -122.4194 },
      { lat: 37.7790, lng: -122.4140 },
    ];
    const motionProfile = {
      vTarget: 18.0,
      aAcc: 2.5,
      aDec: 4.0,
      aLatMax: 3.0,
      vCorner: 5.0,
    };

    // 2. Route Studio: Local Kinematic Simulation Preview in browser engine
    const planned = SpeedProfilePlanner.plan(waypoints, motionProfile, 'Once');
    const localEngine = new KinematicMovementEngine(planned, { tickHz: 10 });

    const localPreviewSteps = [];
    for (let i = 0; i < 20; i++) {
      localPreviewSteps.push(localEngine.step());
    }
    assert.equal(localPreviewSteps.length, 20, 'Local browser simulation must step cleanly');
    assert.ok(localPreviewSteps[0].speed > 0, 'Vehicle must accelerate from start');
    console.log(`✔ Local Route Studio kinematic preview completed: final preview speed = ${localPreviewSteps[19].speed.toFixed(2)} m/s`);

    // 3. Connect tools/device-sim instance with 5 virtual devices
    sim = new DeviceSimulator({
      count: 5,
      rateHz: 1,
      durationSec: 5,
      serverUrl: `ws://localhost:${port}/ws/device`,
      staggerMs: 5,
    });

    // Start simulator in background
    const simPromise = sim.start();

    // 4. Connect Web Console WebSocket client
    const e2eLatenciesMs = [];
    const receivedConsoleUpdates = [];

    await new Promise((resolve) => {
      wsConsole = new WebSocket(`ws://localhost:${port}/ws/console`);
      wsConsole.on('open', resolve);
      wsConsole.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'device.update') {
          const receiveTs = Date.now();
          receivedConsoleUpdates.push(msg.payload);
          if (msg.payload.lastSeen) {
            const latency = receiveTs - msg.payload.lastSeen;
            e2eLatenciesMs.push(latency);
          }
        }
      });
    });

    // 5. Route Studio dispatches scenario to simulated devices via REST API
    const deviceIds = ['sim-dev-0001', 'sim-dev-0002', 'sim-dev-0003'];
    const resScenario = await fetch(`http://localhost:${port}/api/v1/scenarios/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceIds,
        leadMs: 1000,
        params: {
          waypoints,
          motionProfile,
        },
      }),
    });
    assert.equal(resScenario.status, 200);
    const scenBody = await resScenario.json();
    assert.ok(scenBody.scenarioRunId);

    // Wait for simulation to finish streaming
    await simPromise;

    console.log(`✔ Received ${receivedConsoleUpdates.length} live console telemetry updates`);
    assert.ok(receivedConsoleUpdates.length > 0, 'Console must receive live telemetry updates from devices');

    // 6. Assert end-to-end update latency (acceptance criterion: p95 < 500 ms)
    if (e2eLatenciesMs.length > 0) {
      const sorted = [...e2eLatenciesMs].sort((a, b) => a - b);
      const p95 = sorted[Math.floor(0.95 * (sorted.length - 1))];
      console.log(`✔ End-to-end update latency: p50 = ${sorted[Math.floor(0.5 * (sorted.length - 1))]}ms, p95 = ${p95}ms`);
      assert.ok(p95 < 500, `Acceptance SLA: p95 latency must be < 500 ms, got ${p95} ms`);
    }

    // Verify devices transitioned to 'mocking' status
    const mockingUpdates = receivedConsoleUpdates.filter((u) => u.status === 'mocking');
    assert.ok(mockingUpdates.length > 0, 'Devices must be verified live on map with mocking status');

    console.log('✔ Phase 5 Gate E2E test passed successfully!');
  } finally {
    if (sim) sim.stop();
    if (wsConsole) wsConsole.close();
    await backend.close();
  }
});
