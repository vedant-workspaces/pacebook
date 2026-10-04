// Dates are calendar dates ("YYYY-MM-DD") in the runner's local time. They are
// never round-tripped through UTC, so a Sunday run stays on Sunday.

export function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function parseISODate(s: string): Date {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function todayISO(): string {
  return toISODate(new Date());
}

export function addDays(iso: string, n: number): string {
  const d = parseISODate(iso);
  d.setDate(d.getDate() + n);
  return toISODate(d);
}

export function addMonths(iso: string, n: number): string {
  const d = parseISODate(iso);
  d.setDate(1);
  d.setMonth(d.getMonth() + n);
  return toISODate(d);
}

/** Monday of the Monday→Sunday week containing iso. */
export function weekStart(iso: string): string {
  const d = parseISODate(iso);
  const offset = (d.getDay() + 6) % 7;
  return addDays(iso, -offset);
}

export function monthStart(iso: string): string {
  return iso.slice(0, 8) + "01";
}

export function daysBetween(a: string, b: string): number {
  return Math.round((parseISODate(b).getTime() - parseISODate(a).getTime()) / 86_400_000);
}

export function isBetween(iso: string, from: string, to: string): boolean {
  return iso >= from && iso <= to;
}

const fmt = (opts: Intl.DateTimeFormatOptions) => (iso: string) =>
  parseISODate(iso).toLocaleDateString("en-US", opts);

/** "Oct 4" */
export const shortDate = fmt({ month: "short", day: "numeric" });
/** "Sunday, October 4" */
export const longDate = fmt({ weekday: "long", month: "long", day: "numeric" });
/** "October 4, 2026" */
export const fullDate = fmt({ month: "long", day: "numeric", year: "numeric" });
/** "Sun" */
export const weekdayShort = fmt({ weekday: "short" });
/** "Oct" */
export const monthShort = fmt({ month: "short" });
/** "October 2026" */
export const monthYear = fmt({ month: "long", year: "numeric" });

/** "Oct 5 → Jan 17" (adds years when they differ from today's). */
export function dateRange(from: string, to: string): string {
  const thisYear = new Date().getFullYear();
  const withYear = from.slice(0, 4) !== to.slice(0, 4) || Number(from.slice(0, 4)) !== thisYear;
  const f = withYear ? fmt({ month: "short", day: "numeric", year: "numeric" }) : shortDate;
  return `${f(from)} → ${f(to)}`;
}

export function greeting(now = new Date()): string {
  const h = now.getHours();
  if (h < 5) return "Late miles";
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
}
