import type { PlannedVsActual } from "../../types";
import { formatDistance, formatPct, formatSignedKm } from "../../utils/format";
import { ACTUAL_COLOR, PLANNED_COLOR } from "../charts/MileageChart";

/**
 * Neutral planned-vs-actual comparison: two labelled bars and the
 * difference. Never framed as good or bad.
 */
export function PlanComparison({ data, label }: { data: PlannedVsActual; label: string }) {
  const max = Math.max(data.plannedDistanceKm, data.actualDistanceKm, 1);
  const rows = [
    { name: "Planned", km: data.plannedDistanceKm, color: PLANNED_COLOR },
    { name: "Actual", km: data.actualDistanceKm, color: ACTUAL_COLOR },
  ];
  return (
    <div>
      <div className="space-y-3" role="list" aria-label={`${label}: planned vs actual mileage`}>
        {rows.map((r) => (
          <div key={r.name} role="listitem">
            <div className="mb-1 flex items-baseline justify-between text-sm">
              <span className="font-semibold text-ink-soft">{r.name}</span>
              <span className="num text-xl font-bold">{formatDistance(r.km)}</span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-paper">
              <div className="h-full rounded-full" style={{ width: `${(r.km / max) * 100}%`, background: r.color }} />
            </div>
          </div>
        ))}
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-4">
        <div>
          <dt className="text-xs font-medium text-muted">Difference</dt>
          <dd className="num text-2xl font-bold">{formatSignedKm(data.differenceKm)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted">Sessions completed</dt>
          <dd className="num text-2xl font-bold">{formatPct(data.completionPct)}</dd>
        </div>
      </dl>
    </div>
  );
}
