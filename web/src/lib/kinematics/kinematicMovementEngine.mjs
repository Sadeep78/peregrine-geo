import { GeoMath } from './geoMath.mjs';

export class KinematicMovementEngine {
  constructor(plannedRoute, options = {}) {
    this.plannedRoute = plannedRoute;
    this.tickHz = options.tickHz || 10;
    this.dtSeconds = 1.0 / this.tickHz;
    this.speedMultiplier = options.initialSpeedMultiplier || 1.0;
    this.targetSpeedMultiplier = this.speedMultiplier;

    this.currentDistanceMeters = 0.0;
    this.currentSpeedMps = 0.0;
    this.currentBearingDeg = 0.0;
    this.stepIndex = 0;
    this.isCompleted = false;

    if (this.plannedRoute.vertices && this.plannedRoute.vertices.length >= 2) {
      this.currentBearingDeg = this.plannedRoute.vertices[0].segmentBearingDeg;
    }
  }

  step() {
    const totalDistance = this.plannedRoute.totalDistanceMeters;
    const profile = this.plannedRoute.motionProfile;

    if (totalDistance <= 0.0) {
      const pt = this.plannedRoute.vertices[0]?.point || { lat: 0, lon: 0 };
      return {
        stepIndex: this.stepIndex++,
        lat: pt.lat,
        lng: pt.lng ?? pt.lon,
        speed: 0.0,
        bearing: 0.0,
        distanceTraveledMeters: 0.0,
        progressFraction: 1.0,
        isCompleted: true,
      };
    }

    if (this.isCompleted && this.plannedRoute.playMode === 'Once') {
      return this.generateOutputAt(this.currentDistanceMeters, this.currentSpeedMps, this.currentBearingDeg, true);
    }

    // 1. Smoothly ramp speed multiplier
    if (this.speedMultiplier !== this.targetSpeedMultiplier) {
      const maxDeltaMult = (profile.aAcc * this.dtSeconds) / profile.vTarget;
      if (this.speedMultiplier < this.targetSpeedMultiplier) {
        this.speedMultiplier = Math.min(this.targetSpeedMultiplier, this.speedMultiplier + maxDeltaMult);
      } else {
        this.speedMultiplier = Math.max(this.targetSpeedMultiplier, this.speedMultiplier - maxDeltaMult);
      }
    }

    // 2. Determine target velocity from profile at current distance
    const targetProfileSpeed = this.plannedRoute.speedLimitAtDistance(this.currentDistanceMeters) * this.speedMultiplier;

    const maxAccStep = profile.aAcc * this.dtSeconds;
    const maxDecStep = profile.aDec * this.dtSeconds;

    if (this.currentSpeedMps < targetProfileSpeed) {
      this.currentSpeedMps = Math.min(targetProfileSpeed, this.currentSpeedMps + maxAccStep);
    } else if (this.currentSpeedMps > targetProfileSpeed) {
      this.currentSpeedMps = Math.max(targetProfileSpeed, this.currentSpeedMps - maxDecStep);
    }

    // 3. Integrate distance traveled
    const stepDistance = this.currentSpeedMps * this.dtSeconds;

    if (this.plannedRoute.playMode === 'Once') {
      const remaining = totalDistance - this.currentDistanceMeters;
      if (remaining <= Math.max(0.2, stepDistance)) {
        this.currentDistanceMeters = totalDistance;
        this.currentSpeedMps = 0.0;
        this.isCompleted = true;
      } else {
        this.currentDistanceMeters += stepDistance;
      }
    } else {
      this.currentDistanceMeters += stepDistance;
      if (this.currentDistanceMeters >= totalDistance) {
        this.currentDistanceMeters %= totalDistance;
      }
    }

    return this.generateOutputAt(this.currentDistanceMeters, this.currentSpeedMps, this.currentBearingDeg, this.isCompleted);
  }

  setSpeedMultiplier(multiplier) {
    this.targetSpeedMultiplier = multiplier;
  }

  generateOutputAt(dist, speed, bearing, isDone) {
    const totalDistance = this.plannedRoute.totalDistanceMeters;
    const { pos, segBearing } = this.interpolatePolyline(dist);

    if (speed >= 0.5) {
      this.currentBearingDeg = segBearing;
    }

    return {
      stepIndex: this.stepIndex++,
      lat: pos.lat,
      lng: pos.lng,
      speed,
      bearing: this.currentBearingDeg,
      distanceTraveledMeters: dist,
      progressFraction: totalDistance > 0 ? dist / totalDistance : 1.0,
      isCompleted: isDone,
    };
  }

  interpolatePolyline(dist) {
    const vertices = this.plannedRoute.vertices;
    if (!vertices || vertices.length === 0) return { pos: { lat: 0, lng: 0 }, segBearing: 0 };
    if (vertices.length === 1) return { pos: { lat: vertices[0].point.lat, lng: vertices[0].point.lon }, segBearing: 0 };

    const targetDist = Math.max(0.0, Math.min(this.plannedRoute.totalDistanceMeters, dist));

    for (let i = 0; i < vertices.length - 1; i++) {
      const d1 = vertices[i].cumulativeDistanceMeters;
      const d2 = vertices[i + 1].cumulativeDistanceMeters;
      if (targetDist >= d1 && targetDist <= d2) {
        const segLen = d2 - d1;
        const fraction = segLen > 0 ? (targetDist - d1) / segLen : 0.0;
        const p1 = vertices[i].point;
        const p2 = vertices[i + 1].point;
        const interpolated = GeoMath.interpolate(p1, p2, fraction);
        const segBearing = vertices[i].segmentBearingDeg;
        return { pos: interpolated, segBearing };
      }
    }

    const last = vertices[vertices.length - 1];
    return {
      pos: { lat: last.point.lat, lng: last.point.lon },
      segBearing: vertices[vertices.length - 2].segmentBearingDeg,
    };
  }
}
