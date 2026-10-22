import crypto from 'node:crypto';
import http from 'node:http';
import https from 'node:https';
import { URL } from 'node:url';

/**
 * Parses and canonicalizes any standard, hex, octal, or integer IPv4 address into a 32-bit unsigned number.
 * Returns null if not an IPv4 address.
 */
function parseIpv4ToUint32(input) {
  if (!input || typeof input !== 'string') return null;
  const str = input.trim();

  // Single integer form: decimal or hex (e.g. 2130706433 or 0x7f000001)
  if (/^(?:0x[0-9a-fA-F]+|\d+)$/.test(str)) {
    const val = str.startsWith('0x') ? parseInt(str, 16) : parseInt(str, 10);
    if (!isNaN(val) && val >= 0 && val <= 0xffffffff) {
      return val >>> 0;
    }
  }

  // Dotted notation with 1 to 4 parts (supports decimal, hex 0x, octal 0)
  const parts = str.split('.');
  if (parts.length >= 1 && parts.length <= 4) {
    const parsedParts = [];
    for (const p of parts) {
      let num;
      if (p.startsWith('0x') || p.startsWith('0X')) {
        num = parseInt(p, 16);
      } else if (p.length > 1 && p.startsWith('0')) {
        num = parseInt(p, 8);
      } else if (/^\d+$/.test(p)) {
        num = parseInt(p, 10);
      } else {
        return null;
      }
      if (isNaN(num) || num < 0) return null;
      parsedParts.push(num);
    }

    if (parsedParts.length === 4) {
      if (parsedParts.some((p) => p > 255)) return null;
      return (((parsedParts[0] << 24) | (parsedParts[1] << 16) | (parsedParts[2] << 8) | parsedParts[3]) >>> 0);
    } else if (parsedParts.length === 2) {
      // e.g. 127.1 -> 127.0.0.1
      if (parsedParts[0] > 255 || parsedParts[1] > 0xffffff) return null;
      return (((parsedParts[0] << 24) | parsedParts[1]) >>> 0);
    }
  }

  return null;
}

export function isPrivateIp(ip) {
  if (!ip) return true;
  const str = ip.trim().toLowerCase();

  // Hostnames
  if (str === 'localhost' || str === 'localtest.me') return true;

  // IPv6 checks
  if (str === '::1' || str === '::' || str.startsWith('fe80:') || str.startsWith('fc00:') || str.startsWith('fd00:')) {
    return true;
  }

  // Canonicalize IPv4 numeric and dotted forms
  const num = parseIpv4ToUint32(str);
  if (num !== null) {
    // 0.0.0.0/8
    if ((num >>> 24) === 0) return true;
    // 127.0.0.0/8 (Loopback)
    if ((num >>> 24) === 127) return true;
    // 10.0.0.0/8 (RFC 1918)
    if ((num >>> 24) === 10) return true;
    // 172.16.0.0/12 (RFC 1918: 172.16.0.0 to 172.31.255.255)
    if ((num >>> 20) === 0xac1) return true;
    // 192.168.0.0/16 (RFC 1918)
    if ((num >>> 16) === 0xc0a8) return true;
    // 169.254.0.0/16 (Link-Local & Cloud Metadata 169.254.169.254)
    if ((num >>> 16) === 0xa9fe) return true;
    // Multicast & Reserved (224.0.0.0/4 and above)
    if ((num >>> 28) >= 14) return true;
  }

  // Prefix fallbacks
  if (str.startsWith('10.') || str.startsWith('192.168.') || str.startsWith('169.254.')) return true;
  if (/^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(str)) return true;

  return false;
}

export function generateWebhookSignature(secret, timestamp, payloadString) {
  const dataToSign = `${timestamp}.${payloadString}`;
  const hmac = crypto.createHmac('sha256', secret).update(dataToSign).digest('hex');
  return `t=${timestamp},v1=${hmac}`;
}

export class WebhookDispatcher {
  constructor(options = {}) {
    this.allowHttp = options.allowHttp ?? process.env.NODE_ENV !== 'production';
    this.timeoutMs = options.timeoutMs || 5000;
  }

  async dispatch(webhookUrl, secret, event, eventId) {
    const parsed = new URL(webhookUrl);

    // Protocol enforcement
    if (parsed.protocol === 'http:' && !this.allowHttp) {
      return { success: false, error: 'SSRF_BLOCKED: HTTPS required in production' };
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return { success: false, error: 'SSRF_BLOCKED: Invalid protocol' };
    }

    // SSRF IP checks
    const hostname = parsed.hostname;
    if (isPrivateIp(hostname)) {
      return { success: false, error: `SSRF_BLOCKED: Private/loopback target '${hostname}' forbidden` };
    }

    const timestamp = Math.floor(Date.now() / 1000);
    const bodyString = JSON.stringify({
      id: eventId || `evt-${crypto.randomUUID()}`,
      event: event.event,
      data: event,
      timestamp: Date.now(),
    });

    const signature = generateWebhookSignature(secret, timestamp, bodyString);

    const headers = {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(bodyString),
      'User-Agent': 'Peregrine-Webhook-Dispatcher/1.0',
      'X-Peregrine-Signature': signature,
      'X-Peregrine-Event-Id': eventId || '',
    };

    return new Promise((resolve) => {
      const client = parsed.protocol === 'https:' ? https : http;
      const req = client.request(
        parsed,
        {
          method: 'POST',
          headers,
          timeout: this.timeoutMs,
        },
        (res) => {
          let responseBody = '';
          res.on('data', (chunk) => { responseBody += chunk; });
          res.on('end', () => {
            const success = res.statusCode >= 200 && res.statusCode < 300;
            resolve({
              success,
              statusCode: res.statusCode,
              body: responseBody,
              error: success ? null : `HTTP status ${res.statusCode}`,
            });
          });
        }
      );

      req.on('timeout', () => {
        req.destroy();
        resolve({ success: false, error: 'TIMEOUT: Request exceeded 5000ms' });
      });

      req.on('error', (err) => {
        resolve({ success: false, error: err.message });
      });

      req.write(bodyString);
      req.end();
    });
  }
}
