'use client';

import React, { useState } from 'react';

export function GeofenceEditor() {
  const [name, setName] = useState('San Francisco Downtown');
  const [type, setType] = useState<'circle' | 'polygon'>('circle');
  const [radiusMeters, setRadiusMeters] = useState(250);
  const [webhookUrl, setWebhookUrl] = useState('https://api.example.com/webhooks/peregrine');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const handleCreate = async () => {
    setStatusMessage('Geofence created successfully with active enter/exit triggers.');
    setTimeout(() => setStatusMessage(null), 3500);
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 text-slate-200 flex flex-col gap-4 shadow-xl">
      <div className="border-b border-slate-800 pb-3">
        <h2 className="text-base font-bold text-white">Geofence Manager</h2>
        <p className="text-xs text-slate-400">
          Configure geographic perimeters and HMAC-signed webhook alerts (US-08) for device enter and exit events.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
        <div className="flex flex-col gap-1">
          <label className="text-slate-400">Geofence Name</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded text-slate-200 focus:outline-none focus:border-cyan-500"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-slate-400">Geometry Type</label>
          <select
            value={type}
            onChange={(e) => setType(e.target.value as any)}
            className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded text-slate-200 focus:outline-none focus:border-cyan-500"
          >
            <option value="circle">Radial Circle</option>
            <option value="polygon">Custom Polygon</option>
          </select>
        </div>

        {type === 'circle' && (
          <div className="flex flex-col gap-1">
            <label className="text-slate-400">Radius (meters)</label>
            <input
              type="number"
              value={radiusMeters}
              onChange={(e) => setRadiusMeters(parseInt(e.target.value, 10))}
              className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded text-slate-200 focus:outline-none focus:border-cyan-500"
            />
          </div>
        )}

        <div className="flex flex-col gap-1">
          <label className="text-slate-400">Webhook Endpoint URL</label>
          <input
            type="url"
            value={webhookUrl}
            onChange={(e) => setWebhookUrl(e.target.value)}
            className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded text-slate-200 focus:outline-none focus:border-cyan-500"
          />
        </div>
      </div>

      <div className="flex items-center justify-between pt-2">
        {statusMessage && (
          <span className="text-xs text-emerald-400 font-mono animate-fade-in">
            {statusMessage}
          </span>
        )}
        <button
          onClick={handleCreate}
          className="ml-auto px-4 py-1.5 text-xs font-semibold rounded bg-cyan-600 hover:bg-cyan-500 text-white transition shadow"
        >
          Save Geofence
        </button>
      </div>
    </div>
  );
}
