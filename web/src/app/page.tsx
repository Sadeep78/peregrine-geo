'use client';

import React, { useState, useEffect } from 'react';
import { FleetApiClient, DeviceSummary, RouteWaypoint } from '../lib/api';
import { FleetMap } from '../components/fleet/FleetMap';
import { RouteStudio } from '../components/routes/RouteStudio';
import { DeviceDetailSheet } from '../components/devices/DeviceDetailSheet';
import { GeofenceEditor } from '../components/geofences/GeofenceEditor';

export default function FleetConsolePage() {
  const [apiClient] = useState(() => new FleetApiClient());
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<string[]>([]);
  const [activeSheetDeviceId, setActiveSheetDeviceId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'map' | 'routes' | 'geofences'>('map');

  useEffect(() => {
    // Initial fetch
    apiClient.fetchDevices().then((devs) => {
      setDevices(devs);
    });

    // Real-time WebSocket connection
    apiClient.connectWs((updatedDevs) => {
      setDevices(updatedDevs);
    });

    return () => {
      apiClient.disconnect();
    };
  }, [apiClient]);

  const handleSelectDevice = (id: string) => {
    setSelectedDeviceIds((prev) =>
      prev.includes(id) ? prev.filter((d) => d !== id) : [...prev, id]
    );
    setActiveSheetDeviceId(id);
  };

  const handleToggleSelectAll = () => {
    if (selectedDeviceIds.length === devices.length) {
      setSelectedDeviceIds([]);
    } else {
      setSelectedDeviceIds(devices.map((d) => d.id));
    }
  };

  const handleStartScenario = async (ids: string[]) => {
    for (const id of ids) {
      await apiClient.dispatchCommand(id, 'start_mock', {
        lat: 37.7749,
        lng: -122.4194,
        profile: 'drive',
        speedMultiplier: 1.0,
      });
    }
  };

  const handleStopMock = async (ids: string[]) => {
    for (const id of ids) {
      await apiClient.dispatchCommand(id, 'stop_mock');
    }
  };

  const handleDispatchRoute = async (waypoints: RouteWaypoint[], motionProfile: any) => {
    if (selectedDeviceIds.length === 0) return;
    await apiClient.startScenario(selectedDeviceIds, waypoints, motionProfile);
  };

  const activeDevice = devices.find((d) => d.id === activeSheetDeviceId) || null;

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-950/80 backdrop-blur px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-cyan-600 flex items-center justify-center font-bold text-white shadow-lg shadow-cyan-500/20">
            P
          </div>
          <div>
            <h1 className="text-base font-extrabold tracking-tight text-white flex items-center gap-2">
              Peregrine Fleet Console
              <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-cyan-950 border border-cyan-800 text-cyan-400">
                v0.1.0
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              Mock-location control, multi-device scenarios, and telemetry supervision.
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-1 bg-slate-900 border border-slate-800 rounded-lg p-1 text-xs font-semibold">
          <button
            onClick={() => setActiveTab('map')}
            className={`px-3 py-1.5 rounded transition ${
              activeTab === 'map' ? 'bg-cyan-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Fleet Live Map
          </button>
          <button
            onClick={() => setActiveTab('routes')}
            className={`px-3 py-1.5 rounded transition ${
              activeTab === 'routes' ? 'bg-cyan-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Route Studio
          </button>
          <button
            onClick={() => setActiveTab('geofences')}
            className={`px-3 py-1.5 rounded transition ${
              activeTab === 'geofences' ? 'bg-cyan-600 text-white shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Geofences
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <div className="flex-1 p-6 flex flex-col gap-6 max-w-7xl mx-auto w-full">
        {activeTab === 'map' && (
          <FleetMap
            devices={devices}
            selectedDeviceIds={selectedDeviceIds}
            onSelectDevice={handleSelectDevice}
            onToggleSelectAll={handleToggleSelectAll}
            onStartScenario={handleStartScenario}
            onStopMock={handleStopMock}
          />
        )}

        {activeTab === 'routes' && (
          <div className="flex flex-col gap-6">
            <RouteStudio
              onDispatchRoute={handleDispatchRoute}
              selectedDeviceCount={selectedDeviceIds.length}
            />
            <FleetMap
              devices={devices}
              selectedDeviceIds={selectedDeviceIds}
              onSelectDevice={handleSelectDevice}
              onToggleSelectAll={handleToggleSelectAll}
              onStartScenario={handleStartScenario}
              onStopMock={handleStopMock}
            />
          </div>
        )}

        {activeTab === 'geofences' && (
          <div className="flex flex-col gap-6">
            <GeofenceEditor />
            <FleetMap
              devices={devices}
              selectedDeviceIds={selectedDeviceIds}
              onSelectDevice={handleSelectDevice}
              onToggleSelectAll={handleToggleSelectAll}
              onStartScenario={handleStartScenario}
              onStopMock={handleStopMock}
            />
          </div>
        )}
      </div>

      {/* Slide-out Device Detail Sheet */}
      <DeviceDetailSheet
        device={activeDevice}
        onClose={() => setActiveSheetDeviceId(null)}
        onSendCommand={(name, params) => {
          if (activeDevice) {
            apiClient.dispatchCommand(activeDevice.id, name, params);
          }
        }}
      />
    </main>
  );
}
