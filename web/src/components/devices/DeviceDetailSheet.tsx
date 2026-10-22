'use client';

import React from 'react';
import { DeviceSummary } from '../../lib/api';

interface DeviceDetailSheetProps {
  device: DeviceSummary | null;
  onClose: () => void;
  onSendCommand: (name: string, params?: any) => void;
}

export function DeviceDetailSheet({ device, onClose, onSendCommand }: DeviceDetailSheetProps) {
  if (!device) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-slate-900 border-l border-slate-800 shadow-2xl p-6 text-slate-200 z-50 flex flex-col justify-between overflow-y-auto">
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div>
            <h3 className="text-base font-bold text-white">{device.name}</h3>
            <span className="text-xs text-slate-400 font-mono">{device.id}</span>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white text-lg font-bold p-1 rounded"
          >
            ✕
          </button>
        </div>

        {/* Status Highlights */}
        <div className="grid grid-cols-2 gap-2 text-xs font-mono">
          <div className="p-2.5 rounded bg-slate-950 border border-slate-800">
            <div className="text-slate-500 uppercase text-[10px]">Status</div>
            <div className="font-bold text-cyan-400">{device.status.toUpperCase()}</div>
          </div>
          <div className="p-2.5 rounded bg-slate-950 border border-slate-800">
            <div className="text-slate-500 uppercase text-[10px]">Battery</div>
            <div className="font-bold text-emerald-400">{device.batteryPct}%</div>
          </div>
          <div className="p-2.5 rounded bg-slate-950 border border-slate-800">
            <div className="text-slate-500 uppercase text-[10px]">Speed</div>
            <div className="font-bold text-amber-400">{device.speed.toFixed(1)} m/s</div>
          </div>
          <div className="p-2.5 rounded bg-slate-950 border border-slate-800">
            <div className="text-slate-500 uppercase text-[10px]">Accuracy</div>
            <div className="font-bold text-slate-300">±{device.accuracy.toFixed(1)} m</div>
          </div>
        </div>

        {/* Telemetry Breakdown */}
        <div className="flex flex-col gap-1 text-xs font-mono p-3 rounded bg-slate-950/60 border border-slate-800">
          <div className="flex justify-between">
            <span className="text-slate-500">Latitude:</span>
            <span>{device.lat.toFixed(6)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Longitude:</span>
            <span>{device.lng.toFixed(6)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Bearing:</span>
            <span>{device.bearing.toFixed(1)}°</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Altitude:</span>
            <span>{device.altitude.toFixed(1)} m</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Remote Consent:</span>
            <span className={device.remoteControlEnabled ? 'text-emerald-400' : 'text-rose-400'}>
              {device.remoteControlEnabled ? 'Enabled' : 'Disabled'}
            </span>
          </div>
        </div>

        {/* Quick Remote Actions */}
        <div className="flex flex-col gap-2 pt-2">
          <span className="text-xs font-semibold text-slate-400">Quick Commands</span>
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => onSendCommand('pause_mock')}
              className="px-3 py-1.5 text-xs font-medium rounded bg-slate-800 hover:bg-slate-700 text-slate-200 transition"
            >
              Pause
            </button>
            <button
              onClick={() => onSendCommand('resume_mock')}
              className="px-3 py-1.5 text-xs font-medium rounded bg-slate-800 hover:bg-slate-700 text-slate-200 transition"
            >
              Resume
            </button>
            <button
              onClick={() => onSendCommand('set_speed', { speedMultiplier: 2.0 })}
              className="px-3 py-1.5 text-xs font-medium rounded bg-slate-800 hover:bg-slate-700 text-slate-200 transition"
            >
              2x Speed
            </button>
            <button
              onClick={() => onSendCommand('stop_mock')}
              className="px-3 py-1.5 text-xs font-medium rounded bg-rose-900/60 hover:bg-rose-900 text-rose-200 transition"
            >
              Stop Mock
            </button>
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-slate-800 text-[11px] text-slate-500 font-mono">
        Last ping: {new Date(device.lastSeen).toLocaleTimeString()}
      </div>
    </div>
  );
}
