import { DEFAULT_THRESHOLDS, instantaneousStatus } from "./safety";

const now = 1_000_000_000_000;
const fresh = (extra: object) => ({ timestamp: now, motionState: "MOVING", ...extra });

describe("instantaneousStatus", () => {
  it("is OFFLINE with no reading", () => {
    expect(instantaneousStatus(null, DEFAULT_THRESHOLDS, now)).toBe("OFFLINE");
  });

  it("is OFFLINE when stale", () => {
    const r = { timestamp: now - 120_000, heartRate: 70 };
    expect(instantaneousStatus(r, DEFAULT_THRESHOLDS, now)).toBe("OFFLINE");
  });

  it("is NORMAL for healthy vitals", () => {
    expect(instantaneousStatus(fresh({ heartRate: 80, temperature: 36.9 }), DEFAULT_THRESHOLDS, now)).toBe("NORMAL");
  });

  it("is WARNING for high heart rate", () => {
    expect(instantaneousStatus(fresh({ heartRate: 130 }), DEFAULT_THRESHOLDS, now)).toBe("WARNING");
  });

  it("is DANGER on fall", () => {
    expect(instantaneousStatus(fresh({ fallDetected: true, heartRate: 80 }), DEFAULT_THRESHOLDS, now)).toBe("DANGER");
  });

  it("is EMERGENCY on a manual emergency request", () => {
    expect(
      instantaneousStatus(fresh({ heartRate: 80, safetyResponse: "EMERGENCY_REQUESTED" }), DEFAULT_THRESHOLDS, now)
    ).toBe("EMERGENCY");
  });

  it("is EMERGENCY when an escalation goes unanswered, over a fall", () => {
    expect(
      instantaneousStatus(fresh({ fallDetected: true, safetyResponse: "NO_RESPONSE" }), DEFAULT_THRESHOLDS, now)
    ).toBe("EMERGENCY");
  });
});
