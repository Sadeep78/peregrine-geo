import test from 'node:test';
import assert from 'node:assert/strict';
import WebSocket from 'ws';
import { createServer } from '../src/server.mjs';

test('Phase 4 Gate E2E: App pairs, appears on console, executes start_mock, streams telemetry', async () => {
  const port = 4060;
  const backend = createServer({ port });
  await backend.listen();

  let wsDevice = null;
  let wsConsole = null;

  try {
    // 1. Backend generates pairing code
    const resPairCode = await fetch(`http://localhost:${port}/api/v1/devices/pairing-code`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ organizationId: 'org-default' }),
    });
    assert.equal(resPairCode.status, 201);
    const { code } = await resPairCode.json();
    assert.equal(typeof code, 'string');
    assert.equal(code.length, 8);

    // 2. Android device redeems pairing code
    const resPair = await fetch(`http://localhost:${port}/api/v1/devices/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code,
        model: 'Android Pixel 8',
        osVersion: 'Android 15',
        appVersion: '0.1.0',
      }),
    });
    assert.equal(resPair.status, 200);
    const { deviceToken, device } = await resPair.json();
    assert.ok(deviceToken);
    assert.ok(device.id);
    const deviceId = device.id;

    // 3. Connect Web Console to /ws/console
    const consoleUpdates = [];
    await new Promise((resolve) => {
      wsConsole = new WebSocket(`ws://localhost:${port}/ws/console`);
      wsConsole.on('open', resolve);
      wsConsole.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        consoleUpdates.push(msg);
      });
    });

    // 4. Android device connects to /ws/device and sends hello
    let commandReceived = null;
    let telemetryAckReceived = null;

    await new Promise((resolve) => {
      wsDevice = new WebSocket(`ws://localhost:${port}/ws/device`);
      wsDevice.on('open', () => {
        // Send hello
        wsDevice.send(JSON.stringify({
          v: 1,
          id: `hello-${deviceId}`,
          type: 'hello',
          ts: Date.now(),
          payload: {
            deviceId,
            model: 'Android Pixel 8',
            osVersion: 'Android 15',
            appVersion: '0.1.0',
            capabilities: ['mock_location', 'overlay'],
            remoteStandby: true,
          },
        }));
        resolve();
      });

      wsDevice.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'command') {
          commandReceived = msg;
          // Auto ack the command
          wsDevice.send(JSON.stringify({
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
        } else if (msg.type === 'telemetry.ack') {
          telemetryAckReceived = msg;
        }
      });
    });

    // Give 100ms for connection registration
    await new Promise((r) => setTimeout(r, 100));

    // 5. Operator dispatches start_mock command via REST API
    const resCommand = await fetch(`http://localhost:${port}/api/v1/commands`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId,
        name: 'start_mock',
        params: {
          lat: 37.7749,
          lng: -122.4194,
          profile: 'drive',
          speedMultiplier: 1.0,
        },
      }),
    });
    assert.equal(resCommand.status, 201);
    const cmdBody = await resCommand.json();
    assert.equal(cmdBody.delivered, true);

    // Wait for device to receive command and ack it
    await new Promise((r) => setTimeout(r, 150));
    assert.ok(commandReceived, 'Device should have received the start_mock command');
    assert.equal(commandReceived.payload.name, 'start_mock');

    // 6. Device streams telemetry batch
    const now = Date.now();
    wsDevice.send(JSON.stringify({
      v: 1,
      id: `batch-${deviceId}-1`,
      type: 'telemetry.batch',
      ts: now,
      payload: {
        deviceId,
        seq: 1,
        sendTs: now,
        points: [
          {
            lat: 37.7749,
            lng: -122.4194,
            altitude: 15.0,
            speed: 5.5,
            bearing: 90.0,
            accuracy: 2.0,
            ts: now,
          },
        ],
      },
    }));

    // Wait for telemetry ack and console broadcast
    await new Promise((r) => setTimeout(r, 200));
    assert.ok(telemetryAckReceived, 'Device should receive telemetry.ack');
    assert.equal(telemetryAckReceived.payload.seq, 1);

    // Verify console received device update
    const devUpdates = consoleUpdates.filter((m) => m.type === 'device.update' && m.payload.deviceId === deviceId);
    assert.ok(devUpdates.length > 0, 'Console must receive real-time device.update');
    const lastUpdate = devUpdates[devUpdates.length - 1];
    assert.equal(lastUpdate.payload.lat, 37.7749);
    assert.equal(lastUpdate.payload.lng, -122.4194);
    assert.equal(lastUpdate.payload.status, 'mocking');

    // 7. Stop mock command
    const resStop = await fetch(`http://localhost:${port}/api/v1/commands`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId,
        name: 'stop_mock',
      }),
    });
    assert.equal(resStop.status, 201);

    console.log('✔ Phase 4 End-to-End Fleet Integration test passed completely!');
  } finally {
    if (wsDevice) wsDevice.close();
    if (wsConsole) wsConsole.close();
    await backend.close();
  }
});
