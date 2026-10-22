import test from 'node:test';
import assert from 'node:assert/strict';
import { isPrivateIp, WebhookDispatcher } from '../src/services/webhookDispatcher.mjs';

test('Security Audit: Webhook SSRF IP Matrix & Representation Blocking', async () => {
  const dispatcher = new WebhookDispatcher({ allowHttp: false });

  const hostileIps = [
    // Standard loopback and private IPs
    '127.0.0.1',
    'localhost',
    '::1',
    '0.0.0.0',
    '10.0.0.1',
    '172.16.0.1',
    '172.31.255.255',
    '192.168.1.1',
    '169.254.169.254',

    // Decimal 32-bit integer representations
    '2130706433', // 127.0.0.1
    '2852039166', // 169.254.169.254
    '167772161',  // 10.0.0.1

    // Hexadecimal representations
    '0x7f000001', // 127.0.0.1
    '0x7f.0.0.1',
    '0xa9fea9fe', // 169.254.169.254
    '0x0a000001', // 10.0.0.1

    // Octal representations
    '0177.0.0.1', // 127.0.0.1

    // Shortened forms
    '127.1',
  ];

  for (const ip of hostileIps) {
    const isBlocked = isPrivateIp(ip);
    assert.ok(
      isBlocked,
      `SSRF VULNERABILITY: Hostile IP representation '${ip}' was NOT detected as private/loopback!`
    );
  }

  console.log('✔ All 17 hostile IP representations successfully blocked by SSRF evaluator');
});
