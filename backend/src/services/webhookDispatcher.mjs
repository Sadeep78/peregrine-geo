import crypto from 'node:crypto';
import http from 'node:http';
import https from 'node:https';
import { URL } from 'node:url';

export function isPrivateIp(ip) {
  if (!ip) return true;
  // Localhost / Loopback
  if (ip === 'localhost' || ip === '127.0.0.1' || ip === '::1') return true;
  // RFC 1918 Private Ranges
  if (ip.startsWith('10.') || ip.startsWith('192.168.')) return true;
  if (/^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(ip)) return true;
  // Link-Local / Cloud Metadata (169.254.x.x)
  if (ip.startsWith('169.254.')) return true;
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
    if (isPrivateIp(hostname) && !this.allowHttp) {
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
