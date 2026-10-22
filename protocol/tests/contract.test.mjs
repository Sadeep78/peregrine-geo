import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const protocolRoot = path.resolve(__dirname, '..');

const schemasDir = path.join(protocolRoot, 'schemas');
const fixturesDir = path.join(protocolRoot, 'fixtures', 'messages');
const kinematicsDir = path.join(protocolRoot, 'fixtures', 'kinematics');

function loadJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

// Lightweight schema validator for standard types and rules used in Peregrine schemas
function validateAgainstSchema(value, schema, context = 'root') {
  if (schema.type) {
    if (schema.type === 'integer') {
      assert(
        typeof value === 'number' && Number.isInteger(value),
        `${context}: expected integer, got ${typeof value} (${value})`
      );
    } else if (schema.type === 'number') {
      assert(
        typeof value === 'number' && !Number.isNaN(value),
        `${context}: expected number, got ${typeof value}`
      );
    } else if (schema.type === 'string') {
      assert(
        typeof value === 'string',
        `${context}: expected string, got ${typeof value}`
      );
      if (schema.minLength !== undefined) {
        assert(
          value.length >= schema.minLength,
          `${context}: expected string length >= ${schema.minLength}, got ${value.length}`
        );
      }
    } else if (schema.type === 'boolean') {
      assert(
        typeof value === 'boolean',
        `${context}: expected boolean, got ${typeof value}`
      );
    } else if (schema.type === 'object') {
      assert(
        typeof value === 'object' && value !== null && !Array.isArray(value),
        `${context}: expected object, got ${typeof value}`
      );
    } else if (schema.type === 'array') {
      assert(
        Array.isArray(value),
        `${context}: expected array, got ${typeof value}`
      );
    }
  }

  if (schema.enum) {
    assert(
      schema.enum.includes(value),
      `${context}: value '${value}' is not one of enum [${schema.enum.join(', ')}]`
    );
  }

  if (schema.minimum !== undefined) {
    assert(
      value >= schema.minimum,
      `${context}: value ${value} is less than minimum ${schema.minimum}`
    );
  }

  if (schema.maximum !== undefined) {
    assert(
      value <= schema.maximum,
      `${context}: value ${value} is greater than maximum ${schema.maximum}`
    );
  }

  if (schema.type === 'object' && value && typeof value === 'object') {
    if (schema.required) {
      for (const requiredKey of schema.required) {
        assert(
          Object.prototype.hasOwnProperty.call(value, requiredKey),
          `${context}: missing required property '${requiredKey}'`
        );
      }
    }

    if (schema.additionalProperties === false) {
      const allowedKeys = new Set(Object.keys(schema.properties || {}));
      for (const key of Object.keys(value)) {
        assert(
          allowedKeys.has(key),
          `${context}: unexpected additional property '${key}'`
        );
      }
    }

    if (schema.properties) {
      for (const [key, propSchema] of Object.entries(schema.properties)) {
        if (value[key] !== undefined) {
          validateAgainstSchema(value[key], propSchema, `${context}.${key}`);
        }
      }
    }

    if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
      for (const [key, val] of Object.entries(value)) {
        if (!schema.properties || !schema.properties[key]) {
          validateAgainstSchema(val, schema.additionalProperties, `${context}.${key}`);
        }
      }
    }
  }

  if (schema.type === 'array' && Array.isArray(value) && schema.items) {
    value.forEach((item, index) => {
      validateAgainstSchema(item, schema.items, `${context}[${index}]`);
    });
  }
}

test('Envelope Schema validates all message fixtures', () => {
  const envelopeSchema = loadJson(path.join(schemasDir, 'envelope.json'));
  const fixtureFiles = fs.readdirSync(fixturesDir).filter((f) => f.endsWith('.json'));

  assert(fixtureFiles.length >= 10, 'Must have at least 10 message fixtures');

  for (const file of fixtureFiles) {
    const fixture = loadJson(path.join(fixturesDir, file));
    validateAgainstSchema(fixture, envelopeSchema, `Envelope (${file})`);
  }
});

test('Message Payloads match their type-specific schemas', () => {
  const typeToSchema = {
    'hello': 'hello.json',
    'heartbeat': 'heartbeat.json',
    'telemetry.batch': 'telemetry-batch.json',
    'command': 'command.json',
    'command.ack': 'command-ack.json',
    'state.sync': 'state-sync.json',
    'time.sync': 'time-sync.json',
    'error': 'error.json',
    'device.update': 'device-update.json',
    'command.update': 'command-update.json',
    'geofence.event': 'geofence-event.json',
    'scenario.update': 'scenario-update.json',
  };

  const fixtureFiles = fs.readdirSync(fixturesDir).filter((f) => f.endsWith('.json'));

  for (const file of fixtureFiles) {
    const fixture = loadJson(path.join(fixturesDir, file));
    const schemaFile = typeToSchema[fixture.type];
    assert(schemaFile, `No schema mapping for message type '${fixture.type}' in fixture ${file}`);

    const payloadSchema = loadJson(path.join(schemasDir, schemaFile));
    validateAgainstSchema(fixture.payload, payloadSchema, `Payload (${file})`);
  }
});

test('Kinematics Golden Vectors fixture is valid and complete', () => {
  const gv = loadJson(path.join(kinematicsDir, 'golden_vectors.json'));
  assert.equal(gv.version, 1);
  assert(Array.isArray(gv.suites) && gv.suites.length >= 2, 'Must contain at least 2 golden vector suites');

  for (const suite of gv.suites) {
    assert(suite.id, 'Suite must have an id');
    assert(suite.profile, 'Suite must have a profile');
    assert(suite.motionProfile.vTarget > 0, 'vTarget must be > 0');
    assert(suite.motionProfile.aAcc > 0, 'aAcc must be > 0');
    assert(suite.motionProfile.aDec > 0, 'aDec must be > 0');
    assert(suite.motionProfile.aLatMax > 0, 'aLatMax must be > 0');
    assert(Array.isArray(suite.waypoints) && suite.waypoints.length >= 2, 'Waypoints must have length >= 2');
    assert(Array.isArray(suite.goldenSamples) && suite.goldenSamples.length >= 1, 'Golden samples must have length >= 1');
  }
});
