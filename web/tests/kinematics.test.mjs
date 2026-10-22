import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SpeedProfilePlanner } from '../src/lib/kinematics/speedProfilePlanner.mjs';
import { KinematicMovementEngine } from '../src/lib/kinematics/kinematicMovementEngine.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const goldenFixturePath = path.resolve(__dirname, '../../protocol/fixtures/kinematics/golden_vectors.json');

test('TypeScript Kinematic Engine: Golden Vectors Verification', () => {
  const fixtureData = JSON.parse(fs.readFileSync(goldenFixturePath, 'utf8'));

  // 1. Verify Suite 1 (Ideal Noise / Deterministic Kinematic Baseline)
  const idealSuite = fixtureData.suites.find((s) => s.id === 'walk_straight_ideal_seed100');
  assert.ok(idealSuite, 'Ideal suite must exist in fixture');

  const planned = SpeedProfilePlanner.plan(idealSuite.waypoints, idealSuite.motionProfile, 'Once');
  const engine = new KinematicMovementEngine(planned, { tickHz: idealSuite.tickHz });

  for (const sample of idealSuite.goldenSamples) {
    while (engine.stepIndex < sample.step) {
      engine.step();
    }
    const output = engine.step();

    const latDiff = Math.abs(output.lat - sample.lat);
    const lonDiff = Math.abs(output.lng - sample.lon);
    const speedDiff = Math.abs(output.speed - sample.speed);

    assert.ok(
      latDiff <= 1e-4,
      `Step ${sample.step} lat mismatch: got ${output.lat}, expected ${sample.lat} (diff: ${latDiff})`
    );
    assert.ok(
      lonDiff <= 1e-4,
      `Step ${sample.step} lon mismatch: got ${output.lng}, expected ${sample.lon} (diff: ${lonDiff})`
    );
    assert.ok(
      speedDiff <= 1e-4,
      `Step ${sample.step} speed mismatch: got ${output.speed}, expected ${sample.speed} (diff: ${speedDiff})`
    );
  }

  // 2. Verify Suite 2 (Physical Turn Dynamics)
  const turnSuite = fixtureData.suites.find((s) => s.id === 'drive_90deg_turn_opensky_seed42');
  assert.ok(turnSuite, 'Turn suite must exist in fixture');

  const plannedTurn = SpeedProfilePlanner.plan(turnSuite.waypoints, turnSuite.motionProfile, 'Once');
  const turnEngine = new KinematicMovementEngine(plannedTurn, { tickHz: turnSuite.tickHz });

  // Step 0 true kinematic acceleration from stop
  const turnStep0 = turnEngine.step();
  assert.equal(turnStep0.speed, 0.25, 'Step 0 true kinematic speed must be exactly 0.25 m/s (2.5 m/s^2 * 0.1s)');

  // Advance to step 10 (which is the 11th step, index 10)
  for (let i = 1; i < 10; i++) {
    turnEngine.step();
  }
  const turnStep10 = turnEngine.step();
  assert.equal(turnStep10.speed, 2.75, 'Step 10 true kinematic speed after 11 ticks is 2.75 m/s');

  console.log('✔ TypeScript Kinematic Engine matched golden vectors and physical dynamics within 1e-4 tolerance.');
});
