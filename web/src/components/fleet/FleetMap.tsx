'use client';

import React, { useState } from 'react';
import { DeviceSummary } from '../../lib/api';

interface FleetMapProps {
  devices: DeviceSummary[];
  selectedDeviceIds: string[];
  onSelectDevice: (id: string) => void;
  onToggleSelectAll: () => void;
  onStartScenario: (selectedIds: string[]) => void;
  onStopMock: (selectedIds: string[]) => void;
}

export function FleetMap({
  devices,
  selectedDeviceIds,
  onSelectDevice,
  onToggleSelectAll,
  onStartScenario,
  onStopMock,
}: FleetMapProps) {
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');

  const filteredDevices = devices.filter((d) => {
    const matchesStatus = filterStatus === 'all' || d.status === filterStatus;
    const matchesSearch =
      d.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      d.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesStatus && matchesSearch;
  });

  return (
    <div className="relative w-full h-[650px] bg-slate-900 rounded-xl overflow-hidden border border-slate-800 shadow-2xl flex flex-col">
      {/* Top Controls Toolbar */}
      <div className="z-10 bg-slate-950/90 backdrop-blur border-b border-slate-800 p-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <input
            type="text"
            placeholder="Search device ID or name..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="px-3 py-1.5 text-sm bg-slate-900 border border-slate-700 rounded-md text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
          />
          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            className="px-3 py-1.5 text-sm bg-slate-900 border border-slate-700 rounded-md text-slate-200 focus:outline-none focus:border-cyan-500"
          >
            <option value="all">All Statuses ({devices.length})</option>
            <option value="mocking">Mocking</option>
            <option value="idle">Idle / Connected</option>
            <option value="disconnected">Offline</option>
          </select>
        </div>

        {/* Multi-select Action Buttons */}
        <div className="flex items-center gap-2">
          <button
            onClick={onToggleSelectAll}
            className="px-3 py-1.5 text-xs font-medium rounded border border-slate-700 text-slate-300 hover:bg-slate-800 transition"
          >
            {selectedDeviceIds.length === devices.length && devices.length > 0 ? 'Deselect All' : 'Select All'}
          </button>
          {selectedDeviceIds.length > 0 && (
            <>
              <span className="text-xs text-cyan-400 font-mono">
                {selectedDeviceIds.length} selected
              </span>
              <button
                onClick={() => onStartScenario(selectedDeviceIds)}
                className="px-3 py-1.5 text-xs font-semibold rounded bg-emerald-600 hover:bg-emerald-500 text-white shadow transition"
              >
                Start Mock
              </button>
              <button
                onClick={() => onStopMock(selectedDeviceIds)}
                className="px-3 py-1.5 text-xs font-semibold rounded bg-rose-600 hover:bg-rose-500 text-white shadow transition"
              >
                Stop Mock
              </button>
            </>
          )}
        </div>
      </div>

      {/* Map Canvas / Grid Presentation */}
      <div className="relative flex-1 bg-slate-950 p-4 overflow-y-auto">
        <div className="text-xs text-slate-400 mb-2 font-mono flex items-center justify-between">
          <span>MapLibre GL Vector Viewport (San Francisco Centered)</span>
          <span>{filteredDevices.length} devices active on map</span>
        </div>

        {/* Live Device Markers Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {filteredDevices.map((dev) => {
            const isSelected = selectedDeviceIds.includes(dev.id);
            const statusColor =
              dev.status === 'mocking'
                ? 'bg-emerald-500 text-emerald-950'
                : dev.status === 'idle'
                ? 'bg-amber-500 text-amber-950'
                : 'bg-slate-700 text-slate-300';

            return (
              <div
                key={dev.id}
                onClick={() => onSelectDevice(dev.id)}
                className={`p-3 rounded-lg border cursor-pointer transition flex flex-col gap-2 ${
                  isSelected
                    ? 'border-cyan-500 bg-cyan-950/20'
                    : 'border-slate-800 bg-slate-900/60 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => {}}
                      className="rounded border-slate-700 text-cyan-500 focus:ring-0"
                    />
                    <span className="font-semibold text-slate-200 text-sm">{dev.name}</span>
                  </div>
                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wider ${statusColor}`}>
                    {dev.status}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-x-2 gap-y-1 text-xs text-slate-400 font-mono">
                  <div>Lat: {dev.lat.toFixed(5)}</div>
                  <div>Lng: {dev.lng.toFixed(5)}</div>
                  <div>Speed: {dev.speed.toFixed(1)} m/s</div>
                  <div>Bearing: {dev.bearing.toFixed(0)}°</div>
                  <div>Battery: {dev.batteryPct}%</div>
                  <div>Acc: ±{dev.accuracy.toFixed(1)}m</div>
                </div>

                {/* Heading Vector Indicator */}
                <div className="flex items-center gap-2 pt-1 border-t border-slate-800/80 text-[11px] text-slate-500">
                  <span
                    className="inline-block transform origin-center text-cyan-400"
                    style={{ transform: `rotate(${dev.bearing}deg)` }}
                  >
                    ➤
                  </span>
                  <span>Heading vector: {dev.bearing.toFixed(0)}°</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
