// Geofence evaluator with hysteresis exit margin and dwell detection

function haversineDistanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000.0;
  const dLat = (lat2 - lat1) * (Math.PI / 180.0);
  const dLon = (lon2 - lon1) * (Math.PI / 180.0);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180.0)) *
      Math.cos(lat2 * (Math.PI / 180.0)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function pointInPolygon(lat, lng, polygon) {
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const xi = polygon[i].lng, yi = polygon[i].lat;
    const xj = polygon[j].lng, yj = polygon[j].lat;

    const intersect =
      yi > lat !== yj > lat &&
      lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

export class GeofenceEvaluator {
  constructor(geofences = [], hysteresisMarginMeters = 10.0, dwellThresholdCount = 3) {
    this.geofences = geofences;
    this.hysteresisMargin = hysteresisMarginMeters;
    this.dwellThresholdCount = dwellThresholdCount;
    // Map of key `${deviceId}:${geofenceId}` -> state
    this.deviceStates = new Map();
  }

  evaluatePoint(deviceId, point) {
    const events = [];

    for (const fence of this.geofences) {
      const stateKey = `${deviceId}:${fence.id}`;
      const state = this.deviceStates.get(stateKey) || {
        isInside: false,
        dwellCount: 0,
        lastEvent: null,
      };

      let currentlyInside = false;

      if (fence.type === 'circle') {
        const center = fence.geometry.center;
        const dist = haversineDistanceMeters(point.lat, point.lng, center.lat, center.lng);
        // Hysteresis: if already inside, stay inside until exiting radius + margin
        const threshold = state.isInside ? fence.radiusMeters + this.hysteresisMargin : fence.radiusMeters;
        currentlyInside = dist <= threshold;
      } else if (fence.type === 'polygon') {
        currentlyInside = pointInPolygon(point.lat, point.lng, fence.geometry.polygon);
      }

      if (!state.isInside && currentlyInside) {
        // ENTER event
        state.isInside = true;
        state.dwellCount = 1;
        state.lastEvent = 'enter';
        events.push({
          geofenceId: fence.id,
          deviceId,
          event: 'enter',
          ts: point.ts || Date.now(),
          lat: point.lat,
          lng: point.lng,
        });
      } else if (state.isInside && !currentlyInside) {
        // EXIT event
        state.isInside = false;
        state.dwellCount = 0;
        state.lastEvent = 'exit';
        events.push({
          geofenceId: fence.id,
          deviceId,
          event: 'exit',
          ts: point.ts || Date.now(),
          lat: point.lat,
          lng: point.lng,
        });
      } else if (state.isInside && currentlyInside) {
        // DWELL check
        state.dwellCount++;
        if (state.dwellCount === this.dwellThresholdCount) {
          state.lastEvent = 'dwell';
          events.push({
            geofenceId: fence.id,
            deviceId,
            event: 'dwell',
            ts: point.ts || Date.now(),
            lat: point.lat,
            lng: point.lng,
          });
        }
      }

      this.deviceStates.set(stateKey, state);
    }

    return events;
  }
}
