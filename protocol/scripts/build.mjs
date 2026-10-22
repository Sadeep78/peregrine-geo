import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const protocolRoot = path.resolve(__dirname, '..');
const distDir = path.join(protocolRoot, 'dist');

if (!fs.existsSync(distDir)) {
  fs.mkdirSync(distDir, { recursive: true });
}

// Write index.js export
const indexJs = `// Peregrine Protocol definition exports
export const PROTOCOL_VERSION = 1;

export const MESSAGE_TYPES = [
  "hello",
  "heartbeat",
  "telemetry.batch",
  "command.ack",
  "state.sync",
  "command",
  "time.sync",
  "error",
  "device.update",
  "command.update",
  "geofence.event",
  "scenario.update"
];
`;

fs.writeFileSync(path.join(distDir, 'index.js'), indexJs, 'utf8');

// Write index.d.ts export
const indexDts = `export declare const PROTOCOL_VERSION = 1;
export declare const MESSAGE_TYPES: readonly string[];

export interface ProtocolEnvelope<T = unknown> {
  v: number;
  id: string;
  type: string;
  ts: number;
  payload: T;
}
`;

fs.writeFileSync(path.join(distDir, 'index.d.ts'), indexDts, 'utf8');

console.log('Protocol build complete: dist/index.js and dist/index.d.ts generated.');
