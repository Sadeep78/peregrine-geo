import test from 'node:test';
import assert from 'node:assert/strict';
import WebSocket from 'ws';
import { createServer } from '../src/server.mjs';
import { GeofenceEvaluator } from '../src/services/geofenceService.mjs';
import { WebhookDispatcher, isPrivateIp, generateWebhookSignature } from '../src/services/webhookDispatcher.mjs';

const TEST_PORT = 4123;
let backendInstance;

test.before(async () => {
  backendInstance = createServer({ port: TEST_PORT });
  await backendInstance.listen();
});

test.after(async () => {
  if (backendInstance) {
    await backendInstance.close();
  }
});

test('REST API: Auth Login and JWT token issuance', async () => {
  const res = await fetch(`http://localhost:${TEST_PORT}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'admin@peregrinegeo.dev',
      password: 'password123',
    }),
  });

  assert.equal(res.status, 200);
  const data = await res.json();
  assert(data.accessToken, 'Access token must be returned');
  assert.equal(data.user.email, 'admin@peregrinegeo.dev');
});

test('REST API: Device Pairing code lifecycle', async () => {
  // 1. Generate code
  const codeRes = await fetch(`http://localhost:${TEST_PORT}/api/v1/devices/pairing-code`, {
    method: 'POST',
  });
  assert.equal(codeRes.status, 201);
  const { code, expiresAt } = await codeRes.json();
  assert.equal(code.length, 8);
  assert(expiresAt > Date.now());

  // 2. Redeem code
  const pairRes = await fetch(`http://localhost:${TEST_PORT}/api/v1/devices/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      code,
      deviceId: 'pixel7-test-unit',
      model: 'Pixel 7 Pro',
      osVersion: 'Android 14',
      remoteStandby: true,
    }),
  });

  assert.equal(pairRes.status, 200);
  const pairData = await pairRes.json();
  assert(pairData.deviceToken.startsWith('dev_tok_'));
  assert.equal(pairData.device.id, 'pixel7-test-unit');
  assert.equal(pairData.device.remoteStandby, true);
});

test('REST API: Commands lifecycle and Idempotency', async () => {
  const payload = {
    deviceId: 'pixel7-test-unit',
    name: 'start_mock',
    idempotencyKey: 'idem-test-999',
    params: { routeId: 'route-sf-01' },
    ttlMs: 60000,
  };

  const res1 = await fetch(`http://localhost:${TEST_PORT}/api/v1/commands`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  assert.equal(res1.status, 201);
  const cmd1 = await res1.json();
  assert.equal(cmd1.idempotencyKey, 'idem-test-999');
  assert.equal(cmd1.status, 'queued');

  // Send identical idempotency key -> must return exact same command
  const res2 = await fetch(`http://localhost:${TEST_PORT}/api/v1/commands`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  assert.equal(res2.status, 201);
  const cmd2 = await res2.json();
  assert.equal(cmd1.id, cmd2.id);
});

test('REST API: Scenario preflight, synchronized start, and stop-all', async () => {
  // Preflight
  const preflightRes = await fetch(`http://localhost:${TEST_PORT}/api/v1/scenarios/preflight`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceIds: ['pixel7-test-unit'] }),
  });
  assert.equal(preflightRes.status, 200);
  const preflight = await preflightRes.json();
  assert(preflight.devices['pixel7-test-unit'] !== undefined);

  // Synchronized start with >= 3s lead
  const startRes = await fetch(`http://localhost:${TEST_PORT}/api/v1/scenarios/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      deviceIds: ['pixel7-test-unit'],
      leadMs: 4000,
    }),
  });
  assert.equal(startRes.status, 200);
  const startData = await startRes.json();
  assert(startData.startAt >= Date.now() + 3000);

  // Single-call stop-all
  const stopRes = await fetch(`http://localhost:${TEST_PORT}/api/v1/scenarios/stop-all`, {
    method: 'POST',
  });
  assert.equal(stopRes.status, 200);
});

test('Services: Geofence Evaluator with Hysteresis and Dwell detection', () => {
  const geofences = [
    {
      id: 'geo-warehouse',
      name: 'Central Warehouse',
      type: 'circle',
      geometry: { center: { lat: 37.7749, lng: -122.4194 } },
      radiusMeters: 50.0,
    },
  ];

  const evaluator = new GeofenceEvaluator(geofences, 10.0, 3); // 10m hysteresis, 3 fixes dwell

  // Point outside geofence (100m away)
  const pOutside = { lat: 37.7758, lng: -122.4194, ts: 1000 };
  const evts1 = evaluator.evaluatePoint('dev-1', pOutside);
  assert.equal(evts1.length, 0);

  // Point inside geofence (center) -> ENTER event
  const pInside = { lat: 37.7749, lng: -122.4194, ts: 2000 };
  const evts2 = evaluator.evaluatePoint('dev-1', pInside);
  assert.equal(evts2.length, 1);
  assert.equal(evts2[0].event, 'enter');

  // Point 2 inside -> dwell count 2
  const evts3 = evaluator.evaluatePoint('dev-1', pInside);
  assert.equal(evts3.length, 0);

  // Point 3 inside -> DWELL event
  const evts4 = evaluator.evaluatePoint('dev-1', pInside);
  assert.equal(evts4.length, 1);
  assert.equal(evts4[0].event, 'dwell');

  // Point at 55m (inside hysteresis zone 50m to 60m): should NOT trigger exit yet
  const pHysteresis = { lat: 37.77539, lng: -122.4194, ts: 5000 };
  const evts5 = evaluator.evaluatePoint('dev-1', pHysteresis);
  assert.equal(evts5.length, 0);

  // Point outside hysteresis zone (100m) -> EXIT event
  const evts6 = evaluator.evaluatePoint('dev-1', pOutside);
  assert.equal(evts6.length, 1);
  assert.equal(evts6[0].event, 'exit');
});

test('Services: Webhook SSRF validation and HMAC signature generation', () => {
  // Private IP checks
  assert.equal(isPrivateIp('127.0.0.1'), true);
  assert.equal(isPrivateIp('localhost'), true);
  assert.equal(isPrivateIp('10.0.1.20'), true);
  assert.equal(isPrivateIp('192.168.1.1'), true);
  assert.equal(isPrivateIp('172.20.0.5'), true);
  assert.equal(isPrivateIp('169.254.169.254'), true);
  assert.equal(isPrivateIp('api.example.com'), false);

  // Signature generation
  const secret = 'whsec_test_secret_key';
  const timestamp = 1774000000;
  const payload = JSON.stringify({ event: 'test' });
  const sig = generateWebhookSignature(secret, timestamp, payload);
  assert(sig.startsWith(`t=${timestamp},v1=`));

  // SSRF block in production mode
  const prodDispatcher = new WebhookDispatcher({ allowHttp: false });
  prodDispatcher.dispatch('http://169.254.169.254/latest/meta-data', secret, {}, 'evt-1')
    .then((res) => {
      assert.equal(res.success, false);
      assert(res.error.includes('SSRF_BLOCKED'));
    });
});

test('WebSocket: Device and Console bidirectional communication', async () => {
  // 1. Connect Console WebSocket
  const consoleWs = new WebSocket(`ws://localhost:${TEST_PORT}/ws/console`);
  const consoleEvents = [];
  consoleWs.on('message', (data) => {
    consoleEvents.push(JSON.parse(data.toString()));
  });

  await new Promise((resolve) => consoleWs.on('open', resolve));

  // 2. Connect Device WebSocket
  const deviceWs = new WebSocket(`ws://localhost:${TEST_PORT}/ws/device`);
  const deviceMessages = [];
  deviceWs.on('message', (data) => {
    deviceMessages.push(JSON.parse(data.toString()));
  });

  await new Promise((resolve) => deviceWs.on('open', resolve));

  // 3. Send hello message
  deviceWs.send(JSON.stringify({
    v: 1,
    id: 'hello-01',
    type: 'hello',
    ts: Date.now(),
    payload: {
      deviceId: 'dev-ws-test-01',
      model: 'Pixel 8',
      osVersion: 'Android 15',
      appVersion: '1.0.0',
      capabilities: ['mock_location'],
      remoteStandby: true,
    },
  }));

  // Wait for time.sync response from server
  await new Promise((resolve) => setTimeout(resolve, 100));
  const timeSyncMsg = deviceMessages.find((m) => m.type === 'time.sync');
  assert(timeSyncMsg, 'Server must respond with time.sync');

  // 4. Stream telemetry.batch
  deviceWs.send(JSON.stringify({
    v: 1,
    id: 'batch-01',
    type: 'telemetry.batch',
    ts: Date.now(),
    payload: {
      deviceId: 'dev-ws-test-01',
      seq: 1,
      points: [
        {
          lat: 37.7749,
          lng: -122.4194,
          altitude: 10.0,
          speed: 5.5,
          bearing: 90.0,
          accuracy: 2.0,
          ts: Date.now(),
        },
      ],
    },
  }));

  // Wait for telemetry.ack from server
  await new Promise((resolve) => setTimeout(resolve, 150));
  const ackMsg = deviceMessages.find((m) => m.type === 'telemetry.ack');
  assert(ackMsg, 'Server must return telemetry.ack');
  assert.equal(ackMsg.payload.seq, 1);
  assert(ackMsg.payload.ingestDurationMs >= 0);

  // Verify console received device.update
  const devUpdate = consoleEvents.find((e) => e.type === 'device.update');
  assert(devUpdate, 'Console must receive device.update');
  assert.equal(devUpdate.payload.deviceId, 'dev-ws-test-01');
  assert.equal(devUpdate.payload.speed, 5.5);

  consoleWs.close();
  deviceWs.close();
});
