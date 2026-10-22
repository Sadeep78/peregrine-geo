import crypto from 'node:crypto';

/**
 * Thread-safe multi-tenant in-memory database store for Peregrine backend services.
 * Implements strict organization scoping across all entities.
 */
class MemoryStore {
  constructor() {
    this.organizations = new Map();
    this.users = new Map();
    this.memberships = new Map();
    this.devices = new Map();
    this.pairingCodes = new Map();
    this.routes = new Map();
    this.scenarios = new Map();
    this.scenarioRuns = new Map();
    this.commands = new Map();
    this.telemetry = [];
    this.geofences = new Map();
    this.geofenceStates = new Map();
    this.webhooks = new Map();
    this.webhookDeliveries = new Map();
    this.auditLogs = [];

    // Seed default organization & owner user for development
    this.seedDefaults();
  }

  seedDefaults() {
    const orgId = 'org-default';
    this.organizations.set(orgId, {
      id: orgId,
      name: 'Acme Logistics QA',
      createdAt: Date.now(),
    });

    const userId = 'usr-admin';
    const passwordHash = crypto.createHash('sha256').update('password123').digest('hex');
    this.users.set(userId, {
      id: userId,
      email: 'admin@peregrinegeo.dev',
      passwordHash,
      name: 'System Admin',
      createdAt: Date.now(),
    });

    this.memberships.set('mem-1', {
      id: 'mem-1',
      organizationId: orgId,
      userId,
      role: 'owner',
    });
  }

  // --- Auth & Users ---
  findUserByEmail(email) {
    for (const user of this.users.values()) {
      if (user.email.toLowerCase() === email.toLowerCase()) {
        return user;
      }
    }
    return null;
  }

  findMembership(userId, organizationId) {
    for (const mem of this.memberships.values()) {
      if (mem.userId === userId && mem.organizationId === organizationId) {
        return mem;
      }
    }
    return null;
  }

  // --- Devices & Pairing ---
  createPairingCode(organizationId) {
    const code = crypto.randomBytes(4).toString('hex').toUpperCase(); // 8 characters
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 min TTL
    this.pairingCodes.set(code, { code, organizationId, expiresAt });
    return { code, expiresAt };
  }

  redeemPairingCode(code, deviceDetails) {
    const entry = this.pairingCodes.get(code);
    if (!entry) return { error: 'INVALID_CODE', message: 'Pairing code does not exist' };
    if (Date.now() > entry.expiresAt) {
      this.pairingCodes.delete(code);
      return { error: 'EXPIRED_CODE', message: 'Pairing code has expired' };
    }

    this.pairingCodes.delete(code);

    const rawToken = `dev_tok_${crypto.randomBytes(24).toString('hex')}`;
    const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');
    const deviceId = deviceDetails.deviceId || `dev-${crypto.randomUUID()}`;

    const device = {
      id: deviceId,
      organizationId: entry.organizationId,
      name: deviceDetails.name || deviceDetails.model || 'Android Test Device',
      model: deviceDetails.model || 'Unknown',
      osVersion: deviceDetails.osVersion || 'Android 14',
      appVersion: deviceDetails.appVersion || '1.0.0',
      tokenHash,
      status: 'idle',
      remoteControlEnabled: true,
      remoteStandby: deviceDetails.remoteStandby || false,
      lastSeen: Date.now(),
      createdAt: Date.now(),
    };

    this.devices.set(deviceId, device);

    return { device, rawToken };
  }

  findDeviceByToken(rawToken) {
    const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');
    for (const dev of this.devices.values()) {
      if (dev.tokenHash === tokenHash) {
        return dev;
      }
    }
    return null;
  }

  listDevices(organizationId) {
    return Array.from(this.devices.values()).filter((d) => d.organizationId === organizationId);
  }

  // --- Commands ---
  createCommand(organizationId, deviceId, name, params, idempotencyKey, ttlMs = 60000) {
    // Check idempotency
    for (const cmd of this.commands.values()) {
      if (cmd.organizationId === organizationId && cmd.idempotencyKey === idempotencyKey) {
        return cmd; // Return existing command
      }
    }

    const commandId = `cmd-${crypto.randomUUID()}`;
    const command = {
      id: commandId,
      organizationId,
      deviceId,
      name,
      params,
      idempotencyKey,
      ttlMs,
      status: 'queued', // queued -> sent -> delivered -> acked / failed / expired
      issuedAt: Date.now(),
      deliveredAt: null,
      ackedAt: null,
      failedAt: null,
      error: null,
    };

    this.commands.set(commandId, command);
    return command;
  }

  updateCommandStatus(commandId, status, error = null) {
    const cmd = this.commands.get(commandId);
    if (!cmd) return null;

    cmd.status = status;
    if (status === 'delivered') cmd.deliveredAt = Date.now();
    if (status === 'acked') cmd.ackedAt = Date.now();
    if (status === 'failed') {
      cmd.failedAt = Date.now();
      cmd.error = error;
    }
    return cmd;
  }

  // --- Telemetry Ingestion ---
  ingestTelemetryBatch(organizationId, deviceId, seq, points) {
    const dev = this.devices.get(deviceId);
    if (dev) {
      dev.lastSeen = Date.now();
      if (points.length > 0) {
        const last = points[points.length - 1];
        dev.lastLocation = last;
        dev.status = last.speed > 0.1 ? 'mocking' : 'connected';
      }
    }

    for (const pt of points) {
      this.telemetry.push({
        id: `tel-${crypto.randomUUID()}`,
        organizationId,
        deviceId,
        ...pt,
        ingestedAt: Date.now(),
      });
    }

    // Keep memory bounded to last 20,000 points
    if (this.telemetry.length > 20000) {
      this.telemetry = this.telemetry.slice(-15000);
    }
  }

  // --- Geofences ---
  createGeofence(organizationId, name, type, geometry, radiusMeters, webhookId) {
    const id = `geo-${crypto.randomUUID()}`;
    const geofence = {
      id,
      organizationId,
      name,
      type, // 'circle' | 'polygon'
      geometry, // { center: { lat, lng } } or { polygon: [ { lat, lng } ] }
      radiusMeters,
      webhookId,
      createdAt: Date.now(),
    };
    this.geofences.set(id, geofence);
    return geofence;
  }

  listGeofences(organizationId) {
    return Array.from(this.geofences.values()).filter((g) => g.organizationId === organizationId);
  }

  // --- Webhooks ---
  createWebhook(organizationId, url, secret) {
    const id = `wh-${crypto.randomUUID()}`;
    const webhook = {
      id,
      organizationId,
      url,
      secret: secret || crypto.randomBytes(24).toString('hex'),
      enabled: true,
      createdAt: Date.now(),
    };
    this.webhooks.set(id, webhook);
    return webhook;
  }

  recordWebhookDelivery(webhookId, eventId, eventType, status, responseCode = null, error = null) {
    const id = `whd-${crypto.randomUUID()}`;
    const delivery = {
      id,
      webhookId,
      eventId,
      eventType,
      status, // 'delivered' | 'failed'
      responseCode,
      error,
      timestamp: Date.now(),
    };
    this.webhookDeliveries.set(id, delivery);
    return delivery;
  }
}

export const db = new MemoryStore();
