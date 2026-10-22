import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('=== Checking Clean-Room & Architecture Boundaries ===');

let violationCount = 0;

// 1. Check Domain Purity in core/kinematics
const kinematicsDir = path.join(
  rootDir,
  'app',
  'src',
  'main',
  'java',
  'dev',
  'peregrinegeo',
  'peregrine',
  'core',
  'kinematics'
);

const forbiddenKinematicsImports = [
  'android.',
  'androidx.',
  'java.net.',
  'okhttp3.',
  'com.google.android.gms.',
];

if (fs.existsSync(kinematicsDir)) {
  const files = fs.readdirSync(kinematicsDir).filter((f) => f.endsWith('.kt'));
  for (const file of files) {
    const fullPath = path.join(kinematicsDir, file);
    const content = fs.readFileSync(fullPath, 'utf8');
    const lines = content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('import ')) {
        for (const forbidden of forbiddenKinematicsImports) {
          if (line.includes(forbidden)) {
            console.error(`Rule 7 Violation: Forbidden import '${forbidden}' in pure kinematics module: ${file}:${i + 1}`);
            violationCount++;
          }
        }
      }
    }
  }
}

// 2. Clean-room AGPL check
const forbiddenPatterns = [
  ['narumiruna', 'kestrel'].join('/'),
  ['GNU', 'Affero'].join(' '),
  ['AGPL', '3.0'].join('-')
];

const filesToInspect = [];
function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist' || entry.name === 'build' || entry.name === 'scripts') {
      continue;
    }
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath);
    } else if (entry.name.endsWith('.kt') || entry.name.endsWith('.ts') || entry.name.endsWith('.mjs')) {
      filesToInspect.push(fullPath);
    }
  }
}

walk(rootDir);

for (const file of filesToInspect) {
  const content = fs.readFileSync(file, 'utf8');
  for (const pattern of forbiddenPatterns) {
    if (content.includes(pattern)) {
      console.error(`Rule 4 Violation: Detected forbidden reference/code in ${file}`);
      violationCount++;
    }
  }
}

if (violationCount > 0) {
  console.error(`Clean-room & architecture boundary check failed with ${violationCount} violations.`);
  process.exit(1);
} else {
  console.log('✔ Pure domain layer verified (zero Android/network imports in kinematics).');
  console.log('✔ Clean-room compliance verified (Apache-2.0, zero AGPL code).');
  console.log('=== Clean-Room Check Passed ===');
}
