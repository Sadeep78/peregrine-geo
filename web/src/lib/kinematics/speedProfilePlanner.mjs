import { GeoMath } from './geoMath.mjs';

const MAX_CURVATURE_RADIUS = 10000.0;

export class SpeedProfilePlanner {
  static plan(points, profile, playMode = 'Once') {
    if (!points || points.length < 2) {
      throw new Error('Route must have at least 2 points to plan speed profile');
    }

    const n = points.length;
    const cumulativeDistances = new Float64Array(n);
    const segmentDistances = new Float64Array(n - 1);
    const segmentBearings = new Float64Array(n - 1);
    const turnAngles = new Float64Array(n);
    const curvatureRadii = new Float64Array(n);
    const vLim = new Float64Array(n);

    // 1. Calculate segment lengths and bearings
    let cumDist = 0.0;
    cumulativeDistances[0] = 0.0;
    for (let i = 0; i < n - 1; i++) {
      const p1 = points[i];
      const p2 = points[i + 1];
      const d = GeoMath.distanceMeters(p1, p2);
      segmentDistances[i] = d;
      segmentBearings[i] = GeoMath.initialBearing(p1, p2);
      cumDist += d;
      cumulativeDistances[i + 1] = cumDist;
    }
    const totalDistance = cumDist;

    // 2. Calculate turn angles and curvature radii for all vertices
    for (let i = 0; i < n; i++) {
      if (i === 0 || i === n - 1) {
        turnAngles[i] = 0.0;
        curvatureRadii[i] = MAX_CURVATURE_RADIUS;
      } else {
        const bIn = segmentBearings[i - 1];
        const bOut = segmentBearings[i];
        let turn = Math.abs(bOut - bIn);
        if (turn > 180.0) turn = 360.0 - turn;
        turnAngles[i] = turn;

        curvatureRadii[i] = this.computeCircumradius(points[i - 1], points[i], points[i + 1]);
      }
    }

    // 3. Compute vertex speed limit: v_lim = min(v_target, sqrt(a_lat_max * R))
    // Turns sharper than 45 degrees receive corner cap v_corner
    for (let i = 0; i < n; i++) {
      const r = curvatureRadii[i];
      const vLat = Math.sqrt(Math.max(0.0, profile.aLatMax * r));
      let cap = Math.min(profile.vTarget, vLat);

      if (turnAngles[i] > 45.0) {
        cap = Math.min(cap, profile.vCorner);
      }
      vLim[i] = cap;
    }

    // 4. Backward Pass (enforce deceleration limits before corners and destination)
    const vPlanned = new Float64Array(n);
    if (playMode === 'Once') {
      vPlanned[n - 1] = 0.0; // Decelerate to stop at the end
    } else {
      vPlanned[n - 1] = vLim[n - 1];
    }

    for (let i = n - 2; i >= 0; i--) {
      const d = segmentDistances[i];
      const maxAllowedFromNext = Math.sqrt(vPlanned[i + 1] * vPlanned[i + 1] + 2.0 * profile.aDec * d);
      vPlanned[i] = Math.min(vLim[i], maxAllowedFromNext);
    }

    // 5. Forward Pass (enforce acceleration limits from start and out of corners)
    vPlanned[0] = vLim[0];
    for (let i = 0; i < n - 1; i++) {
      const d = segmentDistances[i];
      const maxAllowedFromPrev = Math.sqrt(vPlanned[i] * vPlanned[i] + 2.0 * profile.aAcc * d);
      vPlanned[i + 1] = Math.min(vPlanned[i + 1], maxAllowedFromPrev);
    }

    // Build vertex profile list
    const vertexProfiles = [];
    for (let i = 0; i < n; i++) {
      const segBearing = i < n - 1 ? segmentBearings[i] : segmentBearings[segmentBearings.length - 1] ?? 0.0;
      vertexProfiles.push({
        point: points[i],
        cumulativeDistanceMeters: cumulativeDistances[i],
        segmentBearingDeg: segBearing,
        turnAngleDeg: turnAngles[i],
        radiusOfCurvatureMeters: curvatureRadii[i],
        targetSpeedMps: vLim[i],
        plannedSpeedMps: vPlanned[i],
      });
    }

    return {
      vertices: vertexProfiles,
      totalDistanceMeters: totalDistance,
      motionProfile: profile,
      playMode,
      speedLimitAtDistance: (distanceMeters) => {
        const clamped = Math.max(0.0, Math.min(totalDistance, distanceMeters));
        if (vertexProfiles.length < 2) return 0.0;

        for (let i = 0; i < vertexProfiles.length - 1; i++) {
          const d1 = vertexProfiles[i].cumulativeDistanceMeters;
          const d2 = vertexProfiles[i + 1].cumulativeDistanceMeters;
          if (clamped >= d1 && clamped <= d2) {
            const remToNextVertex = d2 - clamped;
            const vNext = vertexProfiles[i + 1].plannedSpeedMps;
            const vBrake = Math.sqrt(Math.max(0.0, vNext * vNext + 2.0 * profile.aDec * remToNextVertex));
            return Math.min(profile.vTarget, vBrake);
          }
        }
        return vertexProfiles[vertexProfiles.length - 1].plannedSpeedMps;
      },
    };
  }

  static computeCircumradius(a, b, c) {
    const distA = GeoMath.distanceMeters(b, c);
    const distB = GeoMath.distanceMeters(a, c);
    const distC = GeoMath.distanceMeters(a, b);

    if (distA <= 0.0 || distB <= 0.0 || distC <= 0.0) return MAX_CURVATURE_RADIUS;

    const s = (distA + distB + distC) / 2.0;
    const areaSq = s * (s - distA) * (s - distB) * (s - distC);

    if (areaSq <= 1e-6) return MAX_CURVATURE_RADIUS;
    const area = Math.sqrt(areaSq);

    const r = (distA * distB * distC) / (4.0 * area);
    return Math.min(r, MAX_CURVATURE_RADIUS);
  }
}
