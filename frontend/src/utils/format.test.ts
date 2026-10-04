import { describe, expect, it } from "vitest";
import { formatPace, parsePace, splitPace, formatSignedKm, parseNumber, formatDuration } from "./format";
import { addDays, addMonths, weekStart, toISODate, parseISODate } from "./dates";

describe("pace", () => {
  it("formats seconds per km", () => {
    expect(formatPace(330)).toBe("5:30/km");
    expect(formatPace(210)).toBe("3:30/km");
    expect(formatPace(600)).toBe("10:00/km");
    expect(formatPace(347.6)).toBe("5:48/km");
    expect(formatPace(null)).toBe("—");
  });

  it("parses minute and second inputs", () => {
    expect(parsePace("5", "30")).toBe(330);
    expect(parsePace("4", "45")).toBe(285);
    expect(parsePace("10", "")).toBe(600);
    expect(parsePace("", "")).toBeNull();
    expect(parsePace("5", "75")).toBeNaN();
    expect(parsePace("a", "1")).toBeNaN();
  });

  it("splits for editing", () => {
    expect(splitPace(375)).toEqual(["6", "15"]);
    expect(splitPace(undefined)).toEqual(["", ""]);
  });
});

describe("numbers", () => {
  it("formats signed differences neutrally", () => {
    expect(formatSignedKm(0.4)).toBe("+0.4 km");
    expect(formatSignedKm(-3.6)).toBe("−3.6 km");
    expect(formatSignedKm(0.01)).toBe("±0 km");
  });
  it("accepts decimal commas", () => {
    expect(parseNumber("8,4")).toBe(8.4);
    expect(parseNumber("")).toBeNull();
    expect(parseNumber("abc")).toBeNaN();
  });
  it("formats durations", () => {
    expect(formatDuration(45 * 60)).toBe("45 min");
    expect(formatDuration(5400)).toBe("1 h 30 min");
  });
});

describe("dates", () => {
  it("uses Monday-based weeks", () => {
    expect(weekStart("2026-10-04")).toBe("2026-09-28"); // Sunday
    expect(weekStart("2026-09-28")).toBe("2026-09-28"); // Monday
    expect(weekStart("2027-01-01")).toBe("2026-12-28");
  });
  it("does arithmetic without timezone drift", () => {
    expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
    expect(addDays("2026-03-29", 1)).toBe("2026-03-30"); // DST change in many zones
    expect(addMonths("2026-01-31", 1)).toBe("2026-02-01");
    expect(toISODate(parseISODate("2026-10-04"))).toBe("2026-10-04");
  });
});
