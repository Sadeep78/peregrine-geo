export const EARTH_RADIUS_METERS = 6371000.0;

function toRadians(deg) {
  return (deg * Math.PI) / 180.0;
}

function toDegrees(rad) {
  return (rad * 180.0) / Math.PI;
}

export class GeoMath {
  static distanceMeters(from, to) {
    const lat1Rad = toRadians(from.lat);
    const lon1Rad = toRadians(from.lng ?? from.lon);
    const lat2Rad = toRadians(to.lat);
    const lon2Rad = toRadians(to.lng ?? to.lon);

    const dLat = lat2Rad - lat1Rad;
    const dLon = lon2Rad - lon1Rad;

    const a =
      Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
      Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
    const c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

    return EARTH_RADIUS_METERS * c;
  }

  static initialBearing(from, to) {
    const lat1Rad = toRadians(from.lat);
    const lon1Rad = toRadians(from.lng ?? from.lon);
    const lat2Rad = toRadians(to.lat);
    const lon2Rad = toRadians(to.lng ?? to.lon);

    const dLon = lon2Rad - lon1Rad;

    const y = Math.sin(dLon) * Math.cos(lat2Rad);
    const x =
      Math.cos(lat1Rad) * Math.sin(lat2Rad) -
      Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);

    const bearingRad = Math.atan2(y, x);
    const bearingDeg = toDegrees(bearingRad);
    return (bearingDeg + 360.0) % 360.0;
  }

  static destination(start, bearingDeg, distanceMeters) {
    const dOverR = distanceMeters / EARTH_RADIUS_METERS;
    const bearingRad = toRadians(bearingDeg);

    const lat1Rad = toRadians(start.lat);
    const lon1Rad = toRadians(start.lng ?? start.lon);

    const lat2Rad = Math.asin(
      Math.sin(lat1Rad) * Math.cos(dOverR) +
        Math.cos(lat1Rad) * Math.sin(dOverR) * Math.cos(bearingRad)
    );
    const lon2Rad =
      lon1Rad +
      Math.atan2(
        Math.sin(bearingRad) * Math.sin(dOverR) * Math.cos(lat1Rad),
        Math.cos(dOverR) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
      );

    const lat2 = toDegrees(lat2Rad);
    const lon2 = ((toDegrees(lon2Rad) + 540.0) % 360.0) - 180.0;

    return { lat: lat2, lng: lon2 };
  }

  static interpolate(from, to, fraction) {
    const clamped = Math.max(0.0, Math.min(1.0, fraction));
    if (clamped === 0.0) return { lat: from.lat, lng: from.lng ?? from.lon };
    if (clamped === 1.0) return { lat: to.lat, lng: to.lng ?? to.lon };

    const totalDist = GeoMath.distanceMeters(from, to);
    if (totalDist === 0.0) return { lat: from.lat, lng: from.lng ?? from.lon };

    const bearing = GeoMath.initialBearing(from, to);
    const dist = totalDist * clamped;
    const dest = GeoMath.destination(from, bearing, dist);

    const fromEle = from.ele ?? 0;
    const toEle = to.ele ?? 0;
    const ele = fromEle + (toEle - fromEle) * clamped;

    return { lat: dest.lat, lng: dest.lng, ele };
  }
}
