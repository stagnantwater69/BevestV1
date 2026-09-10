/**
 * Pure safety logic — mirrors the Android SafetyStatusEngine so both sides agree.
 * Kept dependency-free so it can be unit tested in isolation.
 */

export interface Thresholds {
  heartRateHigh: number;
  temperatureHigh: number;
  warningDurationSeconds: number;
  responseTimeoutSeconds: number;
  offlineTimeoutSeconds: number;
  lowBatteryPercent: number;
}

export const DEFAULT_THRESHOLDS: Thresholds = {
  heartRateHigh: 100,
  temperatureHigh: 38.0,
  warningDurationSeconds: 5 * 60,
  responseTimeoutSeconds: 15,
  offlineTimeoutSeconds: 60,
  lowBatteryPercent: 20,
};

export interface Reading {
  heartRate?: number | null;
  temperature?: number | null;
  motionState?: string | null;
  fallDetected?: boolean | null;
  safetyResponse?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  battery?: number | null;
  timestamp?: number | null;
}

export type SafetyStatus = "NORMAL" | "WARNING" | "DANGER" | "EMERGENCY" | "OFFLINE";

export function instantaneousStatus(
  reading: Reading | null,
  t: Thresholds,
  now: number
): SafetyStatus {
  if (!reading || !reading.timestamp) return "OFFLINE";
  const ageSeconds = (now - reading.timestamp) / 1000;
  if (ageSeconds > t.offlineTimeoutSeconds) return "OFFLINE";

  if (
    reading.safetyResponse === "EMERGENCY_REQUESTED" ||
    reading.safetyResponse === "NO_RESPONSE" ||
    reading.safetyResponse === "ESCALATED"
  ) {
    return "EMERGENCY";
  }

  if (reading.fallDetected || reading.motionState === "FALL_DETECTED") return "DANGER";

  const hrHigh = reading.heartRate != null && reading.heartRate > t.heartRateHigh;
  const tempHigh = reading.temperature != null && reading.temperature > t.temperatureHigh;
  if (hrHigh || tempHigh || reading.motionState === "INACTIVE") return "WARNING";

  return "NORMAL";
}

export function reason(reading: Reading, t: Thresholds): string {
  if (reading.fallDetected || reading.motionState === "FALL_DETECTED") return "Fall detected";
  if (reading.heartRate != null && reading.heartRate > t.heartRateHigh) {
    return `Elevated heart rate (${reading.heartRate} BPM)`;
  }
  if (reading.temperature != null && reading.temperature > t.temperatureHigh) {
    return `High body temperature (${reading.temperature.toFixed(1)} °C)`;
  }
  if (reading.motionState === "INACTIVE") return "Prolonged inactivity";
  return "Abnormal safety condition";
}
