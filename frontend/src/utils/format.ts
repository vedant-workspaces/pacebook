// Pace is stored as whole seconds per km (330 = 5:30/km); distance in km.

export function formatPace(seconds?: number | null, withUnit = true): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds <= 0) return "—";
  const total = Math.round(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}${withUnit ? "/km" : ""}`;
}

/** "5" + "30" → 330. Returns null for empty input, NaN for invalid input. */
export function parsePace(minutes: string, seconds: string): number | null {
  const m = minutes.trim();
  const s = seconds.trim();
  if (m === "" && s === "") return null;
  if (!/^\d{1,2}$/.test(m || "0") || !/^\d{1,2}$/.test(s || "0")) return NaN;
  const mi = Number(m || 0);
  const se = Number(s || 0);
  if (se > 59) return NaN;
  return mi * 60 + se;
}

/** 330 → ["5", "30"]; empty strings for no pace. */
export function splitPace(seconds?: number | null): [string, string] {
  if (seconds == null) return ["", ""];
  return [String(Math.floor(seconds / 60)), String(seconds % 60).padStart(2, "0")];
}

export function formatKm(km?: number | null, digits = 1): string {
  if (km == null || !Number.isFinite(km)) return "—";
  return km.toLocaleString("en-US", { minimumFractionDigits: 0, maximumFractionDigits: digits });
}

export function formatDistance(km?: number | null, digits = 1): string {
  if (km == null) return "—";
  return `${formatKm(km, digits)} km`;
}

export function formatSignedKm(km: number): string {
  const rounded = Math.round(km * 10) / 10;
  if (rounded === 0) return "±0 km";
  return `${rounded > 0 ? "+" : "−"}${formatKm(Math.abs(rounded))} km`;
}

export function formatDuration(seconds?: number | null): string {
  if (seconds == null || seconds <= 0) return "—";
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

export function formatPct(pct?: number | null): string {
  if (pct == null) return "—";
  return `${Math.round(pct)}%`;
}

/** Parses a decimal number typed by a runner; accepts "8,4" as 8.4. */
export function parseNumber(v: string): number | null {
  const t = v.trim().replace(",", ".");
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : NaN;
}

/** 2840 → "47:20", 5400 → "1:30:00". */
export function formatClock(seconds: number): string {
  const t = Math.round(seconds);
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}
