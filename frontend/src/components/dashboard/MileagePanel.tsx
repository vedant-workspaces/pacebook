import { useState } from "react";
import { useAsync } from "../../hooks/useAsync";
import { api } from "../../services/api";
import { monthShort, monthYear, parseISODate, shortDate, weekdayShort } from "../../utils/dates";
import { formatDistance } from "../../utils/format";
import { Legend, MileageChart, seriesToRows } from "../charts/MileageChart";
import { Card } from "../ui/Card";
import { ErrorState, Skeleton } from "../ui/Feedback";

type Range = "weekly" | "monthly" | "yearly";
const TABS: { value: Range; label: string }[] = [
  { value: "weekly", label: "Week" },
  { value: "monthly", label: "Month" },
  { value: "yearly", label: "Year" },
];

/** Actual mileage chart with a Week / Month / Year switch. */
export function MileagePanel({ refreshKey = 0 }: { refreshKey?: number }) {
  const [range, setRange] = useState<Range>("weekly");
  const { data, error, loading, reload } = useAsync(() => api.series(range), [range, refreshKey]);

  const rows = data
    ? seriesToRows(
        data.points,
        range === "weekly" ? weekdayShort : range === "monthly" ? (d) => String(parseISODate(d).getDate()) : monthShort,
        range === "yearly" ? monthYear : (d) => `${weekdayShort(d)}, ${shortDate(d)}`,
      )
    : [];

  const caption =
    range === "weekly" ? "Daily mileage this week" : range === "monthly" ? "Daily mileage this month" : "Monthly mileage this year";

  return (
    <Card className="p-5">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="eyebrow">Mileage</h2>
          <p className="num mt-1 text-3xl font-bold">{data ? formatDistance(data.totalDistanceKm) : "—"}</p>
          <p className="text-xs text-muted">{caption}</p>
        </div>
        <div role="tablist" aria-label="Mileage range" className="flex rounded-xl bg-paper p-1">
          {TABS.map((t) => (
            <button
              key={t.value}
              role="tab"
              type="button"
              aria-selected={range === t.value}
              onClick={() => setRange(t.value)}
              className={`h-9 rounded-lg px-3.5 text-sm font-semibold transition-colors ${
                range === t.value ? "bg-white text-ink shadow-sm" : "text-muted hover:text-ink"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading && !data ? (
        <Skeleton className="h-56" />
      ) : (
        <>
          <MileageChart rows={rows} caption={caption} />
          <div className="mt-3">
            <Legend planned={rows.some((r) => (r.planned ?? 0) > 0)} />
          </div>
        </>
      )}
    </Card>
  );
}
