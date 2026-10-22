import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const testClassesDir = path.join(rootDir, 'build', 'test-classes');
if (!fs.existsSync(testClassesDir)) {
  fs.mkdirSync(testClassesDir, { recursive: true });
}

console.log('=== Compiling and Running Peregrine Kotlin Tests ===');

// 1. Collect main source files
const srcDir = path.join(rootDir, 'app', 'src', 'main', 'java');
const testDir = path.join(rootDir, 'app', 'src', 'test', 'java');

function collectFiles(dir, filter) {
  const results = [];
  function walk(current) {
    if (!fs.existsSync(current)) return;
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (filter(entry.name)) {
        results.push(full);
      }
    }
  }
  walk(dir);
  return results;
}

const mainFiles = collectFiles(srcDir, (name) => name.endsWith('.kt') && !name.includes('PeregrineApplication'));
const testFiles = collectFiles(testDir, (name) => name.endsWith('Test.kt'));

console.log(`Found ${mainFiles.length} source files and ${testFiles.length} test files.`);

// 2. Build classpath
const testLibsDir = path.join(rootDir, 'tools', 'test-libs');
const jars = fs.readdirSync(testLibsDir)
  .filter((f) => f.endsWith('.jar'))
  .map((f) => path.join(testLibsDir, f));

const classpath = jars.join(';');

// Write kotlinc argfile to avoid CLI quoting issues on Windows
const argfilePath = path.join(rootDir, 'build', 'kotlinc-args.txt');
const argLines = [
  '-classpath',
  classpath,
  ...mainFiles,
  ...testFiles,
  '-d',
  testClassesDir,
];

fs.writeFileSync(argfilePath, argLines.join('\n'), 'utf8');

console.log('Compiling Kotlin classes with kotlinc...');
try {
  const kotlincBat = 'C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.2.2\\plugins\\Kotlin\\kotlinc\\bin\\kotlinc.bat';
  execSync(`"${kotlincBat}" "@${argfilePath}"`, { stdio: 'inherit' });
  console.log('✔ Kotlin compilation successful.');
} catch (err) {
  console.error('Compilation failed:', err.message);
  process.exit(1);
}

// 3. Find test class names
const testClasses = testFiles.map((file) => {
  const relative = path.relative(testDir, file);
  return relative.replace(/\.kt$/, '').split(path.sep).join('.');
});

console.log(`Running ${testClasses.length} test suites with JUnitCore...`);

const kotlinStdlib = 'C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.2.2\\plugins\\Kotlin\\kotlinc\\lib\\kotlin-stdlib.jar';
const runClasspath = [testClassesDir, ...jars, kotlinStdlib].join(';');

try {
  const output = execSync(
    `java -cp "${runClasspath}" org.junit.runner.JUnitCore ${testClasses.join(' ')}`,
    { stdio: 'inherit' }
  );
  console.log('✔ All Kotlin unit tests passed successfully.');
} catch (err) {
  console.error('JUnit tests failed:', err.message);
  process.exit(1);
}
