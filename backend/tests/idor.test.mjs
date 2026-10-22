import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../src/server.mjs';
import { db } from '../src/db/memoryStore.mjs';
import { signToken } from '../src/auth/jwt.mjs';

test('Security Audit: Multi-Tenant Authorization & IDOR Matrix Enforcement', async () => {
  const port = 4085;
  const backend = createServer({ port });
  await backend.listen();

  try {
    // Setup Org A and Org B
    const orgA = 'org-alpha';
    const orgB = 'org-beta';

    db.organizations.set(orgA, { id: orgA, name: 'Alpha Org', createdAt: Date.now() });
    db.organizations.set(orgB, { id: orgB, name: 'Beta Org', createdAt: Date.now() });

    // Device A belongs to Org A
    const devA = {
      id: 'dev-alpha-001',
      organizationId: orgA,
      name: 'Alpha Device',
      status: 'connected',
      remoteControlEnabled: true,
      lastSeen: Date.now(),
      createdAt: Date.now(),
    };
    db.devices.set(devA.id, devA);

    // Create a command belonging to Org A
    const cmdA = db.createCommand(orgA, devA.id, 'start_mock', {}, 'idem-alpha-1');

    // Token for User B in Org B
    const tokenB = signToken({
      sub: 'usr-beta',
      email: 'user@beta.com',
      orgId: orgB,
      role: 'owner',
    });

    const headersB = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${tokenB}`,
    };

    // 1. IDOR Attack: User B attempts to dispatch command to Device A
    const resDispatch = await fetch(`http://localhost:${port}/api/v1/commands`, {
      method: 'POST',
      headers: headersB,
      body: JSON.stringify({
        deviceId: devA.id,
        name: 'stop_mock',
      }),
    });
    assert.equal(
      resDispatch.status,
      403,
      `IDOR VIOLATION: User from Org B was allowed to dispatch commands to Org A device! Got status ${resDispatch.status}`
    );

    // 2. IDOR Attack: User B attempts to read Command A from Org A
    const resGetCmd = await fetch(`http://localhost:${port}/api/v1/commands/${cmdA.id}`, {
      method: 'GET',
      headers: headersB,
    });
    assert.ok(
      resGetCmd.status === 403 || resGetCmd.status === 404,
      `IDOR VIOLATION: User from Org B was allowed to read Org A command! Got status ${resGetCmd.status}`
    );

    // 3. IDOR Attack: User B lists devices, must not see Org A devices
    const resListDevs = await fetch(`http://localhost:${port}/api/v1/devices`, {
      method: 'GET',
      headers: headersB,
    });
    assert.equal(resListDevs.status, 200);
    const listBody = await resListDevs.json();
    const leakedDev = listBody.items.find((d) => d.id === devA.id);
    assert.equal(
      leakedDev,
      undefined,
      'IDOR VIOLATION: Device from Org A was leaked in Org B device listing!'
    );

    console.log('✔ IDOR matrix passed: cross-organization access strictly denied with 403/404');
  } finally {
    await backend.close();
  }
});
