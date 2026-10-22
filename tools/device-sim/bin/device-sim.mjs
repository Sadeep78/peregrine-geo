#!/usr/bin/env node

import { DeviceSimulator } from '../src/simulator.mjs';

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    count: 10,
    rateHz: 1,
    durationSec: 10,
    serverUrl: 'ws://localhost:4000/ws/device',
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--count' && args[i + 1]) {
      options.count = parseInt(args[++i], 10);
    } else if (arg === '--rate' && args[i + 1]) {
      options.rateHz = parseFloat(args[++i]);
    } else if (arg === '--duration' && args[i + 1]) {
      options.durationSec = parseInt(args[++i], 10);
    } else if (arg === '--server' && args[i + 1]) {
      options.serverUrl = args[++i];
    } else if (arg === '--help' || arg === '-h') {
      console.log(`
Peregrine High-Scale Device Simulator CLI

Options:
  --count <N>        Number of virtual Android devices to simulate (default: 10)
  --rate <Hz>        Telemetry emission rate in Hz per device (default: 1)
  --duration <sec>   Duration in seconds to run simulation (default: 10)
  --server <url>     WebSocket endpoint (default: ws://localhost:4000/ws/device)
  --help, -h         Show this help message
`);
      process.exit(0);
    }
  }

  return options;
}

async function main() {
  const options = parseArgs();

  console.log('='.repeat(60));
  console.log(' Peregrine Device Simulator');
  console.log(` Target Server : ${options.serverUrl}`);
  console.log(` Device Count  : ${options.count}`);
  console.log(` Target Rate   : ${options.rateHz} Hz per device`);
  console.log(` Test Duration : ${options.durationSec} seconds`);
  console.log('='.repeat(60));

  const sim = new DeviceSimulator(options);

  process.on('SIGINT', () => {
    console.log('\nReceived SIGINT, shutting down simulator...');
    sim.stop();
    printReport(sim.getMetrics());
    process.exit(0);
  });

  try {
    await sim.start();
    const metrics = sim.getMetrics();
    printReport(metrics);

    if (metrics.rtt.p95 > 100) {
      console.error(`\n❌ Ingest SLA Violated: p95 RTT was ${metrics.rtt.p95}ms (> 100ms threshold)`);
      process.exit(1);
    } else {
      console.log(`\n✅ Ingest SLA Satisfied: p95 RTT was ${metrics.rtt.p95}ms (< 100ms threshold)`);
      process.exit(0);
    }
  } catch (err) {
    console.error('Simulation error:', err);
    sim.stop();
    process.exit(1);
  }
}

function printReport(metrics) {
  console.log('\n' + '='.repeat(60));
  console.log(' SIMULATION BENCHMARK REPORT');
  console.log('='.repeat(60));
  console.log(` Connected Devices   : ${metrics.connectedDevices}`);
  console.log(` Total Batches Sent  : ${metrics.totalSent}`);
  console.log(` Total Batches Acked : ${metrics.totalAcked}`);
  console.log(` Network/Parse Errors: ${metrics.errors}`);
  console.log('-'.repeat(60));
  console.log(' Round-Trip Time (RTT) from Device Client Perspective (ms):');
  console.log(`   min: ${metrics.rtt.min}ms | avg: ${metrics.rtt.avg}ms | max: ${metrics.rtt.max}ms`);
  console.log(`   p50: ${metrics.rtt.p50}ms | p90: ${metrics.rtt.p90}ms | p95: ${metrics.rtt.p95}ms | p99: ${metrics.rtt.p99}ms`);
  console.log('-'.repeat(60));
  console.log(' Server Ingest Duration (ms):');
  console.log(`   min: ${metrics.serverIngest.min}ms | avg: ${metrics.serverIngest.avg}ms | max: ${metrics.serverIngest.max}ms`);
  console.log(`   p50: ${metrics.serverIngest.p50}ms | p90: ${metrics.serverIngest.p90}ms | p95: ${metrics.serverIngest.p95}ms | p99: ${metrics.serverIngest.p99}ms`);
  console.log('='.repeat(60));
}

main();
