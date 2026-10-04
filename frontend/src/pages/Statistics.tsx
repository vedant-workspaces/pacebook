import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { MileagePanel } from "../components/dashboard/MileagePanel";
import { PlanComparison } from "../components/dashboard/PlanComparison";
import { PageHeader } from "../components/layout/AppLayout";
import { BigNumber, Card } from "../components/ui/Card";
import { ErrorState, Skeleton } from "../components/ui/Feedback";
import { BoltIcon, HeartIcon, RouteIcon, TimerIcon, TrophyIcon } from "../components/ui/Icons";
import { useAsync } from "../hooks/useAsync";
import { api } from "../services/api";
import type { Activity, Period } from "../types";
import { shortDate } from "../utils/dates";
import { formatDistance, formatDuration, formatKm, formatPace, formatPct } from "../utils/format";

const PERIODS: { value: Period; label: string }[] = [
  { value: "week", label: "This week" },
  { value: "month", label: "This month" },
  { value: "year", label: "This year" },
  { value: "all", label: "All time" },
];

export default function Statistics() {
  const [period, setPeriod] = useState<Period>("all");
  const { data, error, reload, loading } = useAsync(() => api.overview(period), [period]);
  const label = PERIODS.find((p) => p.value === period)!.label;

  return (
    <div className="space-y-6">
      <PageHeader title="Statistics" sub="Mileage, pace and consistency, calculated from what you actually ran." />

      {/* Mileage totals */}
      <section aria-label="Mileage" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {(
          [
            ["week", "Weekly"],
            ["month", "Monthly"],
            ["year", "Yearly"],
            ["allTime", "All time"],
          ] as const
        ).map(([k, l], i) => {
          const t = data?.totals[k];
          return (
            <Card key={k} className={`p-4 sm:p-5 ${i === 3 ? "bg-ink text-white" : ""}`}>
              <p className={`eyebrow ${i === 3 ? "!text-white/60" : ""}`}>{l} mileage</p>
              {t ? (
                <>
                  <BigNumber className="mt-2 block" value={formatKm(t.distanceKm)} unit="km" />
                  <p className={`mt-1 text-sm ${i === 3 ? "text-white/70" : "text-muted"}`}>
                    {t.activityCount} {t.activityCount === 1 ? "activity" : "activities"}
                  </p>
                </>
              ) : (
                <Skeleton className="mt-2 h-12 w-28" />
              )}
            </Card>
          );
        })}
      </section>

      <MileagePanel />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-display text-2xl font-bold">Averages & records</h2>
        <div role="tablist" aria-label="Period" className="flex flex-wrap rounded-xl bg-white p-1 ring-1 ring-line">
          {PERIODS.map((p) => (
            <button key={p.value} type="button" role="tab" aria-selected={period === p.value} onClick={() => setPeriod(p.value)}
              className={`h-9 rounded-lg px-3 text-sm font-semibold ${period === p.value ? "bg-ink text-white" : "text-muted hover:text-ink"}`}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !data ? (
        <Skeleton className="h-64" />
      ) : (
        <div className={`grid gap-4 lg:grid-cols-3 [&>*]:min-w-0 ${loading ? "opacity-70" : ""}`}>
          <Card className="p-5 lg:col-span-2">
            <dl className="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-3">
              <Stat icon={<RouteIcon width={16} height={16} />} label="Distance" value={formatDistance(data.aggregates.distanceKm)} />
              <Stat label="Activities" value={data.aggregates.activityCount} />
              <Stat label="Average distance" value={formatDistance(data.aggregates.averageDistanceKm)} />
              <Stat icon={<BoltIcon width={16} height={16} />} label="Average pace" value={formatPace(data.aggregates.averagePaceSecondsPerKm)}
                hint="Total time ÷ total distance" />
              <Stat icon={<HeartIcon width={16} height={16} />} label="Average HR"
                value={data.aggregates.averageHeartRate ? `${Math.round(data.aggregates.averageHeartRate)} bpm` : "—"} />
              <Stat icon={<TimerIcon width={16} height={16} />} label="Moving time" value={formatDuration(data.aggregates.totalTimeSeconds)}
                hint="Activities with a pace" />
            </dl>
            <div className="mt-6 grid gap-3 border-t border-line pt-5 sm:grid-cols-2">
              <Record title="Longest activity" activity={data.longestActivity} value={(a) => formatDistance(a.distanceKm, 2)} />
              <Record title="Fastest average pace" activity={data.fastestActivity} value={(a) => formatPace(a.paceSecondsPerKm)}
                note="Activities of 1 km or more" />
            </div>
          </Card>

          <Card className="p-5">
            <h3 className="eyebrow mb-1">Planned vs actual · {label.toLowerCase()}</h3>
            {data.training.totalActivities === 0 ? (
              <p className="mt-3 text-sm text-muted">No planned training in this period.</p>
            ) : (
              <>
                <p className="mb-4 text-sm text-muted">
                  {data.training.completed} completed · {data.training.modified} modified · {data.training.skipped} skipped
                </p>
                <PlanComparison data={data.plannedVsActual} label={label} />
              </>
            )}
            <p className="mt-5 border-t border-line pt-4 text-xs leading-relaxed text-muted">
              <strong className="text-ink-soft">Training completion {formatPct(data.training.completionPct)}</strong> — completed or
              modified sessions ÷ non-rest sessions that are due (dated today or earlier, or already marked). Rest days and future
              sessions never count against you. Planned mileage excludes skipped sessions; for month, year and all time it only counts the plan up to today.
            </p>
          </Card>
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, icon, hint }: { label: string; value: ReactNode; icon?: ReactNode; hint?: string }) {
  return (
    <div>
      <dt className="flex items-center gap-1 text-xs font-semibold text-muted">
        {icon}
        {label}
      </dt>
      <dd className="num text-3xl font-bold">{value}</dd>
      {hint && <p className="text-[0.7rem] text-muted">{hint}</p>}
    </div>
  );
}

function Record({ title, activity, value, note }: { title: string; activity: Activity | null; value: (a: Activity) => string; note?: string }) {
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-paper p-4">
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-ember-500 text-white">
        <TrophyIcon />
      </span>
      <div className="min-w-0">
        <p className="eyebrow">{title}</p>
        {activity ? (
          <Link to={`/runs/${activity.id}`} className="block hover:underline">
            <span className="num text-2xl font-bold">{value(activity)}</span>
            <span className="block truncate text-xs text-muted">
              {activity.title} · {shortDate(activity.activityDate)}
            </span>
          </Link>
        ) : (
          <p className="text-sm text-muted">No data yet</p>
        )}
        {note && activity && <p className="text-[0.7rem] text-muted">{note}</p>}
      </div>
    </div>
  );
}
