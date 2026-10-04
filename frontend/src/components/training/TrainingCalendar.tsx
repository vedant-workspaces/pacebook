import { useMeta } from "../../hooks/useMeta";
import type { Activity, TrainingActivity } from "../../types";
import { addDays, isBetween, parseISODate, todayISO, weekdayShort } from "../../utils/dates";
import { formatKm, formatDuration } from "../../utils/format";
import { PlusIcon } from "../ui/Icons";
import { STATUS } from "../ui/Status";

export type CalendarView = "week" | "month";

interface Props {
  view: CalendarView;
  /** First visible day (a Monday). */
  from: string;
  /** Number of visible days (7 for week, 35/42 for month). */
  days: number;
  /** Month being shown (YYYY-MM), used to dim neighbouring-month days. */
  month?: string;
  block: { startDate: string; endDate: string };
  planned: TrainingActivity[];
  actual: Activity[];
  onOpenPlanned: (p: TrainingActivity) => void;
  onOpenDay: (date: string) => void;
  onAdd: (date: string) => void;
}

/**
 * Monday→Sunday training calendar. Each planned activity shows its type,
 * title, distance and a status symbol (✓ ✕ ~ ○) — never colour alone.
 */
export function TrainingCalendar(props: Props) {
  const { view, from, days, planned, actual } = props;
  const dates = Array.from({ length: days }, (_, i) => addDays(from, i));
  const plannedBy = group(planned, (p) => p.activityDate);
  const actualKm = new Map<string, number>();
  for (const a of actual) actualKm.set(a.activityDate, (actualKm.get(a.activityDate) ?? 0) + (a.distanceKm ?? 0));

  if (view === "week") {
    return (
      <div className="grid gap-2 md:grid-cols-7">
        {dates.map((d) => (
          <DayCell key={d} date={d} {...props} items={plannedBy.get(d) ?? []} km={actualKm.get(d)} variant="week" />
        ))}
      </div>
    );
  }

  return (
    <div>
      <div className="mb-1 hidden grid-cols-7 gap-1 md:grid" aria-hidden>
        {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
          <div key={d} className="eyebrow px-2 py-1">
            {d}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 gap-1 md:grid-cols-7">
        {dates.map((d) => (
          <DayCell key={d} date={d} {...props} items={plannedBy.get(d) ?? []} km={actualKm.get(d)} variant="month" />
        ))}
      </div>
    </div>
  );
}

function DayCell({
  date,
  items,
  km,
  variant,
  block,
  month,
  onOpenPlanned,
  onOpenDay,
  onAdd,
}: Props & { date: string; items: TrainingActivity[]; km?: number; variant: CalendarView }) {
  const { typeLabel } = useMeta();
  const today = todayISO();
  const inBlock = isBetween(date, block.startDate, block.endDate);
  const otherMonth = month != null && date.slice(0, 7) !== month;
  const isToday = date === today;
  const dayNum = parseISODate(date).getDate();
  const dateLabel = parseISODate(date).toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });

  // Month view on mobile: hide empty days outside the month to keep it short.
  const hideOnMobile = variant === "month" && (otherMonth || (!items.length && !km));

  return (
    <div
      className={`group relative flex flex-col rounded-xl border bg-white ${
        variant === "week" ? "min-h-36 p-2.5" : "min-h-28 p-1.5"
      } ${isToday ? "border-ember-500 ring-2 ring-ember-500/20" : "border-line"} ${
        !inBlock || otherMonth ? "bg-white/50 opacity-60" : ""
      } ${hideOnMobile ? "hidden md:flex" : ""}`}
    >
      <button
        type="button"
        onClick={() => onOpenDay(date)}
        className="mb-1.5 flex items-baseline justify-between gap-1 rounded-lg px-1 text-left hover:bg-paper"
        aria-label={`Open ${dateLabel}`}
      >
        <span className="flex items-baseline gap-1.5">
          <span className={`text-xs font-bold uppercase tracking-wider ${isToday ? "text-ember-600" : "text-muted"} ${variant === "month" ? "md:hidden" : ""}`}>
            {weekdayShort(date)}
          </span>
          <span className={`num text-lg font-bold ${isToday ? "text-ember-600" : ""}`}>{dayNum}</span>
          {isToday && <span className="sr-only">(today)</span>}
        </span>
        {km ? (
          <span className="num whitespace-nowrap text-xs font-bold text-ink-soft" title="Actual distance">
            {formatKm(km)} km
          </span>
        ) : null}
      </button>

      <ul className="flex flex-1 flex-col gap-1">
        {items.map((p) => {
          const s = STATUS[p.status];
          const metric = p.plannedDistanceKm
            ? `${formatKm(p.plannedDistanceKm)} km`
            : p.plannedDurationSeconds
              ? formatDuration(p.plannedDurationSeconds)
              : "";
          return (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => onOpenPlanned(p)}
                className={`w-full rounded-lg ${s.bg} px-2 py-1.5 text-left ring-1 ${s.ring} transition hover:brightness-95`}
                aria-label={`${p.title}, ${metric || typeLabel(p.activityType)}, ${s.label}`}
              >
                <span className="flex items-center gap-1.5">
                  <span className={`text-sm font-black ${s.text}`} aria-hidden>
                    {s.symbol}
                  </span>
                  <span className={`truncate text-xs font-bold ${p.status === "skipped" ? "line-through decoration-1" : ""}`}>
                    {variant === "week" ? typeLabel(p.activityType) : p.title}
                  </span>
                </span>
                {variant === "week" && <span className="mt-0.5 block truncate text-xs text-ink-soft">{p.title}</span>}
                {metric && <span className="num block text-base font-bold leading-tight">{metric}</span>}
              </button>
            </li>
          );
        })}
      </ul>

      {inBlock && (
        <button
          type="button"
          onClick={() => onAdd(date)}
          className={`mt-1 flex items-center justify-center gap-1 rounded-lg border border-dashed border-line-strong text-xs font-semibold text-muted transition hover:border-ember-500 hover:text-ember-600 ${
            variant === "week" ? "h-8" : "h-7 md:opacity-0 md:group-hover:opacity-100 md:focus-visible:opacity-100"
          }`}
          aria-label={`Add activity on ${dateLabel}`}
        >
          <PlusIcon width={14} height={14} /> {variant === "week" && "Add"}
        </button>
      )}
    </div>
  );
}

function group<T>(items: T[], key: (t: T) => string): Map<string, T[]> {
  const m = new Map<string, T[]>();
  for (const it of items) {
    const k = key(it);
    const list = m.get(k);
    if (list) list.push(it);
    else m.set(k, [it]);
  }
  return m;
}
