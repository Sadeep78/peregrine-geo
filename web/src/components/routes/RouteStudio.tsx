'use client';

import React, { useState, useEffect, useRef } from 'react';
import { RouteWaypoint } from '../../lib/api';
import { SpeedProfilePlanner } from '../../lib/kinematics/speedProfilePlanner.mjs';
import { KinematicMovementEngine } from '../../lib/kinematics/kinematicMovementEngine.mjs';

interface RouteStudioProps {
  onDispatchRoute: (waypoints: RouteWaypoint[], motionProfile: any) => void;
  selectedDeviceCount: number;
}

const MOTION_PRESETS = {
  drive: { vTarget: 18.0, aAcc: 2.5, aDec: 4.0, aLatMax: 3.0, vCorner: 5.0 },
  bike: { vTarget: 6.0, aAcc: 1.2, aDec: 2.0, aLatMax: 2.0, vCorner: 3.0 },
  run: { vTarget: 3.5, aAcc: 1.0, aDec: 1.5, aLatMax: 1.5, vCorner: 2.0 },
  walk: { vTarget: 1.4, aAcc: 0.8, aDec: 1.2, aLatMax: 1.5, vCorner: 1.0 },
};

export function RouteStudio({ onDispatchRoute, selectedDeviceCount }: RouteStudioProps) {
  const [profileKey, setProfileKey] = useState<keyof typeof MOTION_PRESETS>('drive');
  const [waypoints, setWaypoints] = useState<RouteWaypoint[]>([
    { lat: 37.7749, lng: -122.4194 },
    { lat: 37.7790, lng: -122.4194 },
    { lat: 37.7790, lng: -122.4140 },
  ]);

  const [simState, setSimState] = useState<{
    isRunning: boolean;
    lat: number;
    lng: number;
    speed: number;
    progress: number;
  }>({
    isRunning: false,
    lat: 37.7749,
    lng: -122.4194,
    speed: 0,
    progress: 0,
  });

  const simRef = useRef<{
    engine: KinematicMovementEngine | null;
    timer: any;
  }>({ engine: null, timer: null });

  const handleStartSim = () => {
    if (waypoints.length < 2) return;

    try {
      const planned = SpeedProfilePlanner.plan(waypoints, MOTION_PRESETS[profileKey], 'Once');
      const engine = new KinematicMovementEngine(planned, { tickHz: 10 });
      simRef.current.engine = engine;

      setSimState((prev) => ({ ...prev, isRunning: true }));

      if (simRef.current.timer) clearInterval(simRef.current.timer);

      simRef.current.timer = setInterval(() => {
        if (!simRef.current.engine) return;
        const out = simRef.current.engine.step();

        setSimState({
          isRunning: !out.isCompleted,
          lat: out.lat,
          lng: out.lng,
          speed: out.speed,
          progress: out.progressFraction,
        });

        if (out.isCompleted) {
          clearInterval(simRef.current.timer);
          simRef.current.timer = null;
        }
      }, 50); // 2x simulation speed
    } catch (e) {
      console.error('Simulation error:', e);
    }
  };

  const handleStopSim = () => {
    if (simRef.current.timer) {
      clearInterval(simRef.current.timer);
      simRef.current.timer = null;
    }
    setSimState((prev) => ({ ...prev, isRunning: false }));
  };

  useEffect(() => {
    return () => {
      if (simRef.current.timer) clearInterval(simRef.current.timer);
    };
  }, []);

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 text-slate-200 flex flex-col gap-4 shadow-xl">
      <div className="flex items-center justify-between border-b border-slate-800 pb-3">
        <div>
          <h2 className="text-base font-bold text-white flex items-center gap-2">
            <span>Route Studio & Kinematic Preview</span>
          </h2>
          <p className="text-xs text-slate-400">
            Draw multi-segment paths, configure kinematic acceleration profiles, and preview vehicle physics before fleet dispatch.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={profileKey}
            onChange={(e) => setProfileKey(e.target.value as any)}
            className="px-3 py-1.5 text-xs bg-slate-950 border border-slate-700 rounded-md text-slate-200 font-semibold focus:outline-none focus:border-cyan-500"
          >
            <option value="drive">Drive (18 m/s, a=2.5, dec=4.0)</option>
            <option value="bike">Bicycle (6 m/s, a=1.2, dec=2.0)</option>
            <option value="run">Run (3.5 m/s, a=1.0, dec=1.5)</option>
            <option value="walk">Walk (1.4 m/s, a=0.8, dec=1.2)</option>
          </select>
        </div>
      </div>

      {/* Waypoints List */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs font-mono">
        {waypoints.map((wp, idx) => (
          <div key={idx} className="p-2.5 rounded bg-slate-950/80 border border-slate-800 flex items-center justify-between">
            <div>
              <span className="text-cyan-400 font-bold mr-2">WP #{idx + 1}</span>
              <span>{wp.lat.toFixed(4)}, {wp.lng.toFixed(4)}</span>
            </div>
            {idx === 0 && <span className="text-[10px] text-emerald-400 uppercase font-bold">Start</span>}
            {idx === waypoints.length - 1 && <span className="text-[10px] text-rose-400 uppercase font-bold">End</span>}
          </div>
        ))}
      </div>

      {/* Simulation Preview Card */}
      <div className="p-4 rounded-lg bg-slate-950 border border-slate-800 flex flex-col gap-2">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-slate-300">Kinematic Simulation Preview (TypeScript Port):</span>
          <span className="font-mono text-cyan-400">
            Progress: {(simState.progress * 100).toFixed(0)}% | Speed: {simState.speed.toFixed(1)} m/s
          </span>
        </div>

        {/* Progress Bar */}
        <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
          <div
            className="bg-cyan-500 h-full transition-all duration-75"
            style={{ width: `${Math.min(100, simState.progress * 100)}%` }}
          />
        </div>

        <div className="flex items-center justify-between pt-2">
          <div className="text-xs text-slate-400 font-mono">
            Position: {simState.lat.toFixed(5)}, {simState.lng.toFixed(5)}
          </div>
          <div className="flex items-center gap-2">
            {!simState.isRunning ? (
              <button
                onClick={handleStartSim}
                className="px-3 py-1 text-xs font-semibold rounded bg-cyan-600 hover:bg-cyan-500 text-white transition"
              >
                Run Kinematic Preview
              </button>
            ) : (
              <button
                onClick={handleStopSim}
                className="px-3 py-1 text-xs font-semibold rounded bg-amber-600 hover:bg-amber-500 text-white transition"
              >
                Pause Preview
              </button>
            )}

            <button
              onClick={() => onDispatchRoute(waypoints, MOTION_PRESETS[profileKey])}
              disabled={selectedDeviceCount === 0}
              className={`px-4 py-1 text-xs font-bold rounded transition shadow ${
                selectedDeviceCount > 0
                  ? 'bg-emerald-600 hover:bg-emerald-500 text-white cursor-pointer'
                  : 'bg-slate-800 text-slate-500 cursor-not-allowed'
              }`}
            >
              Dispatch to {selectedDeviceCount} Device(s)
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
