import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const protocolRoot = path.resolve(__dirname, '..');

const dirsToCheck = [
  path.join(protocolRoot, 'schemas'),
  path.join(protocolRoot, 'fixtures', 'messages'),
  path.join(protocolRoot, 'fixtures', 'kinematics'),
];

let errorCount = 0;

for (const dir of dirsToCheck) {
  if (!fs.existsSync(dir)) continue;
  const files = fs.readdirSync(dir);
  for (const file of files) {
    if (!file.endsWith('.json')) continue;
    const fullPath = path.join(dir, file);
    try {
      const content = fs.readFileSync(fullPath, 'utf8');
      JSON.parse(content);
    } catch (err) {
      console.error(`Lint error in ${fullPath}:`, err.message);
      errorCount++;
    }
  }
}

if (errorCount > 0) {
  console.error(`Found ${errorCount} lint errors.`);
  process.exit(1);
} else {
  console.log('Protocol JSON lint passed: All schemas and fixtures are valid JSON.');
}
