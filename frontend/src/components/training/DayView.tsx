import { Link } from "react-router-dom";
import type { DaySummary, TrainingActivity } from "../../types";
import { formatDistance } from "../../utils/format";
import { ActivityRow, PlannedRow } from "../activities/ActivityCards";
import { buttonClass } from "../ui/Button";
import { PlusIcon } from "../ui/Icons";

/**
 * A runner's day: what was PLANNED next to what ACTUALLY happened, with the
 * day's total. Used by the calendar and the dashboard.
 */
export function DayView({
  day,
  onOpenPlanned,
  onAddPlanned,
}: {
  day: DaySummary;
  onOpenPlanned: (p: TrainingActivity) => void;
  onAddPlanned?: () => void;
}) {
  return (
    <div className="space-y-6">
      <section aria-labelledby="day-plan">
        <div className="mb-1 flex items-center justify-between border-b border-line pb-2">
          <h3 id="day-plan" className="eyebrow">Plan</h3>
          {onAddPlanned && (
            <button type="button" onClick={onAddPlanned} className="flex items-center gap-1 text-sm font-semibold text-ember-600 hover:underline">
              <PlusIcon width={16} height={16} /> Add Activity
            </button>
          )}
        </div>
        {day.planned.length === 0 ? (
          <p className="py-3 text-sm text-muted">Nothing planned for this day.</p>
        ) : (
          <div className="divide-y divide-line">
            {day.planned.map((p) => (
              <PlannedRow key={p.id} planned={p} onOpen={() => onOpenPlanned(p)} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="day-actual">
        <div className="mb-1 flex items-center justify-between border-b border-line pb-2">
          <h3 id="day-actual" className="eyebrow">Actual</h3>
          <Link to={`/log?date=${day.date}`} className="flex items-center gap-1 text-sm font-semibold text-ember-600 hover:underline">
            <PlusIcon width={16} height={16} /> Log Activity
          </Link>
        </div>
        {day.actual.length === 0 ? (
          <p className="py-3 text-sm text-muted">No activities logged.</p>
        ) : (
          <div className="divide-y divide-line">
            {day.actual.map((a) => (
              <ActivityRow key={a.id} activity={a} to={`/runs/${a.id}`} />
            ))}
          </div>
        )}
      </section>

      <div className="flex items-baseline justify-between rounded-2xl bg-ink px-5 py-4 text-white">
        <span className="eyebrow !text-white/60">Total</span>
        <span className="num text-3xl font-bold">{formatDistance(day.distanceKm)}</span>
      </div>
      {day.planned.length === 0 && day.actual.length === 0 && onAddPlanned && (
        <button type="button" onClick={onAddPlanned} className={buttonClass("secondary", "lg", "w-full")}>
          <PlusIcon /> Plan an activity
        </button>
      )}
    </div>
  );
}
