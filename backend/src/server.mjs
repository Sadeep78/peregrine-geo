import http from 'node:http';
import { WebSocketServer } from 'ws';
import { db } from './db/memoryStore.mjs';
import { signToken, verifyToken } from './auth/jwt.mjs';
import { deviceManager } from './ws/deviceWs.mjs';
import { consoleManager } from './ws/consoleWs.mjs';
import { WebhookDispatcher } from './services/webhookDispatcher.mjs';

const webhookDispatcher = new WebhookDispatcher();

export function createServer(options = {}) {
  const port = options.port || process.env.PORT || 4000;

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = url.pathname;
    const method = req.method;

    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Request-Id');

    if (method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    const sendJson = (statusCode, data) => {
      res.writeHead(statusCode, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };

    const sendProblem = (status, title, detail, code) => {
      res.writeHead(status, { 'Content-Type': 'application/problem+json' });
      res.end(JSON.stringify({
        type: 'about:blank',
        title,
        status,
        detail,
        code,
        instance: pathname,
      }));
    };

    const readBody = () => new Promise((resolve) => {
      let body = '';
      req.on('data', (chunk) => { body += chunk; });
      req.on('end', () => {
        try {
          resolve(body ? JSON.parse(body) : {});
        } catch {
          resolve(null);
        }
      });
    });

    const getCallerOrgId = () => {
      const authHeader = req.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.substring(7);
        const verified = verifyToken(token);
        if (verified && verified.orgId) {
          return verified.orgId;
        }
      }
      return 'org-default';
    };

    // Health endpoints
    if (pathname === '/healthz' || pathname === '/readyz') {
      return sendJson(200, { status: 'healthy', timestamp: Date.now() });
    }

    // --- /api/v1/auth ---
    if (pathname === '/api/v1/auth/login' && method === 'POST') {
      const body = await readBody();
      if (!body || !body.email || !body.password) {
        return sendProblem(400, 'Bad Request', 'Email and password required', 'MISSING_FIELDS');
      }

      const user = db.findUserByEmail(body.email);
      if (!user) {
        return sendProblem(401, 'Unauthorized', 'Invalid credentials', 'INVALID_CREDENTIALS');
      }

      const token = signToken({
        sub: user.id,
        email: user.email,
        orgId: 'org-default',
        role: 'owner',
      });

      return sendJson(200, {
        accessToken: token,
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { id: user.id, email: user.email, name: user.name },
        organization: db.organizations.get('org-default'),
      });
    }

    // --- /api/v1/devices ---
    if (pathname === '/api/v1/devices/pairing-code' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const result = db.createPairingCode(callerOrg);
      return sendJson(201, result);
    }

    if (pathname === '/api/v1/devices/pair' && method === 'POST') {
      const body = await readBody();
      if (!body || !body.code) {
        return sendProblem(400, 'Bad Request', 'Pairing code required', 'MISSING_CODE');
      }

      const redeemed = db.redeemPairingCode(body.code.trim().toUpperCase(), body);
      if (redeemed.error) {
        return sendProblem(400, 'Pairing Failed', redeemed.message, redeemed.error);
      }

      return sendJson(200, {
        deviceToken: redeemed.rawToken,
        device: redeemed.device,
      });
    }

    if (pathname === '/api/v1/devices' && method === 'GET') {
      const callerOrg = getCallerOrgId();
      const devices = db.listDevices(callerOrg);
      return sendJson(200, { items: devices, total: devices.length });
    }

    // --- /api/v1/commands ---
    if (pathname === '/api/v1/commands' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const body = await readBody();
      if (!body || !body.deviceId || !body.name) {
        return sendProblem(400, 'Bad Request', 'deviceId and name required', 'MISSING_FIELDS');
      }

      const targetDev = db.devices.get(body.deviceId);
      if (!targetDev) {
        return sendProblem(404, 'Not Found', 'Target device not found', 'DEVICE_NOT_FOUND');
      }
      if (targetDev.organizationId !== callerOrg) {
        return sendProblem(403, 'Forbidden', 'Target device belongs to another organization', 'FORBIDDEN');
      }

      const idemKey = body.idempotencyKey || `idem-${Date.now()}-${Math.random()}`;
      const cmd = db.createCommand(callerOrg, body.deviceId, body.name, body.params || {}, idemKey, body.ttlMs || 60000);

      // Attempt immediate WebSocket delivery if device is online
      const delivered = deviceManager.sendCommand(body.deviceId, cmd);

      return sendJson(201, {
        ...cmd,
        delivered,
      });
    }

    if (pathname.startsWith('/api/v1/commands/') && method === 'GET') {
      const callerOrg = getCallerOrgId();
      const commandId = pathname.replace('/api/v1/commands/', '');
      const cmd = db.commands.get(commandId);
      if (!cmd || cmd.organizationId !== callerOrg) {
        return sendProblem(404, 'Not Found', 'Command not found', 'COMMAND_NOT_FOUND');
      }
      return sendJson(200, cmd);
    }

    // --- /api/v1/scenarios ---
    if (pathname === '/api/v1/scenarios/preflight' && method === 'POST') {
      const body = await readBody();
      const deviceIds = body.deviceIds || [];
      const readiness = {};

      for (const devId of deviceIds) {
        const isConnected = deviceManager.connections.has(devId);
        const dev = db.devices.get(devId);
        readiness[devId] = {
          connected: isConnected,
          remoteControlEnabled: dev?.remoteControlEnabled ?? false,
          mockAppSelected: true,
          routeDownloaded: true,
          ready: isConnected && (dev?.remoteControlEnabled ?? false),
        };
      }

      const allReady = Object.values(readiness).every((r) => r.ready);
      return sendJson(200, { ready: allReady, devices: readiness });
    }

    if (pathname === '/api/v1/scenarios/start' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const body = await readBody();

      // Check IDOR on all requested devices
      for (const devId of body.deviceIds || []) {
        const targetDev = db.devices.get(devId);
        if (targetDev && targetDev.organizationId !== callerOrg) {
          return sendProblem(403, 'Forbidden', `Device '${devId}' belongs to another organization`, 'FORBIDDEN');
        }
      }

      const leadMs = Math.max(3000, body.leadMs || 3000); // At least 3s lead
      const startAt = Date.now() + leadMs;
      const scenarioRunId = `run-${Date.now()}`;

      // Dispatch start command to all specified devices
      for (const devId of body.deviceIds || []) {
        const cmd = db.createCommand(callerOrg, devId, 'start_mock', {
          startAt,
          scenarioRunId,
          ...body.params,
        }, `scen-${scenarioRunId}-${devId}`);
        deviceManager.sendCommand(devId, cmd);
      }

      consoleManager.broadcast(callerOrg, {
        v: 1,
        id: `scen-up-${Date.now()}`,
        type: 'scenario.update',
        ts: Date.now(),
        payload: {
          scenarioRunId,
          status: 'running',
          startSkewMs: 0,
          deviceStatuses: {},
        },
      });

      return sendJson(200, { scenarioRunId, startAt, leadMs });
    }

    if (pathname === '/api/v1/scenarios/stop-all' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const allDevs = db.listDevices(callerOrg);
      for (const dev of allDevs) {
        const cmd = db.createCommand(callerOrg, dev.id, 'stop_mock', {}, `stop-all-${Date.now()}-${dev.id}`);
        deviceManager.sendCommand(dev.id, cmd);
      }
      return sendJson(200, { message: 'All devices stopped', count: allDevs.length });
    }

    // --- /api/v1/geofences ---
    if (pathname === '/api/v1/geofences' && method === 'GET') {
      const callerOrg = getCallerOrgId();
      const list = db.listGeofences(callerOrg);
      return sendJson(200, { items: list });
    }

    if (pathname === '/api/v1/geofences' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const body = await readBody();
      const fence = db.createGeofence(callerOrg, body.name, body.type, body.geometry, body.radiusMeters || 100, body.webhookId);
      deviceManager.geofenceEvaluator.geofences.push(fence);
      return sendJson(201, fence);
    }

    // --- /api/v1/webhooks ---
    if (pathname === '/api/v1/webhooks' && method === 'POST') {
      const callerOrg = getCallerOrgId();
      const body = await readBody();
      const wh = db.createWebhook(callerOrg, body.url, body.secret);
      return sendJson(201, wh);
    }

    if (pathname.includes('/api/v1/webhooks/') && pathname.endsWith('/test') && method === 'POST') {
      const webhookId = pathname.split('/')[4];
      const wh = db.webhooks.get(webhookId);
      if (!wh) {
        return sendProblem(404, 'Not Found', 'Webhook not found', 'WEBHOOK_NOT_FOUND');
      }

      const testEvent = {
        event: 'geofence.enter',
        geofenceId: 'geo-test-01',
        deviceId: 'dev-sim-01',
        ts: Date.now(),
        lat: 37.7749,
        lng: -122.4194,
      };

      const dispatchResult = await webhookDispatcher.dispatch(wh.url, wh.secret, testEvent, `evt-${Date.now()}`);
      db.recordWebhookDelivery(wh.id, 'evt-test', 'test', dispatchResult.success ? 'delivered' : 'failed', dispatchResult.statusCode, dispatchResult.error);

      return sendJson(200, dispatchResult);
    }

    // 404 fallback
    return sendProblem(404, 'Not Found', `Path '${pathname}' not recognized`, 'NOT_FOUND');
  });

  // WebSocket Servers on /ws/device and /ws/console
  const wssDevice = new WebSocketServer({ noServer: true });
  const wssConsole = new WebSocketServer({ noServer: true });

  wssDevice.on('connection', (ws, req) => {
    deviceManager.handleConnection(ws, req);
  });

  wssConsole.on('connection', (ws, req) => {
    consoleManager.handleConnection(ws, req);
  });

  server.on('upgrade', (req, socket, head) => {
    const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    if (parsed.pathname === '/ws/device') {
      wssDevice.handleUpgrade(req, socket, head, (ws) => {
        wssDevice.emit('connection', ws, req);
      });
    } else if (parsed.pathname === '/ws/console') {
      wssConsole.handleUpgrade(req, socket, head, (ws) => {
        wssConsole.emit('connection', ws, req);
      });
    } else {
      socket.destroy();
    }
  });

  return {
    server,
    listen: () => new Promise((resolve) => server.listen(port, resolve)),
    close: () => new Promise((resolve) => server.close(resolve)),
    port,
  };
}

// Direct execution entrypoint
if (process.argv[1] && process.argv[1].endsWith('server.mjs')) {
  const instance = createServer();
  instance.listen().then(() => {
    console.log(`Peregrine backend running on http://localhost:${instance.port}`);
    console.log(`WebSocket endpoints: ws://localhost:${instance.port}/ws/device and /ws/console`);
  });
}
