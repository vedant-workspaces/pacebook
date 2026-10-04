import { useState } from "react";
import { Link } from "react-router-dom";
import { ActivityRow, PlannedRow } from "../components/activities/ActivityCards";
import { MileagePanel } from "../components/dashboard/MileagePanel";
import { PlanComparison } from "../components/dashboard/PlanComparison";
import { Button, ButtonLink } from "../components/ui/Button";
import { BigNumber, Card } from "../components/ui/Card";
import { ElevationLine, EmptyState, ErrorState, Skeleton } from "../components/ui/Feedback";
import { CheckIcon, PlusIcon } from "../components/ui/Icons";
import { PlannedActivityModal, type PlannedMode } from "../components/training/PlannedActivityModal";
import { useAsync } from "../hooks/useAsync";
import { useAuth } from "../hooks/useAuth";
import { api } from "../services/api";
import type { TrainingActivity } from "../types";
import { greeting, longDate, todayISO } from "../utils/dates";
import { formatDistance, formatKm } from "../utils/format";

export default function Dashboard() {
  const { user } = useAuth();
  const today = todayISO();
  const [version, setVersion] = useState(0);
  const refresh = () => setVersion((v) => v + 1);

  const day = useAsync(() => api.day(today), [today, version]);
  const overview = useAsync(() => api.overview("week"), [version]);
  const week = useAsync(() => api.week(today), [today, version]);

  const [open, setOpen] = useState<{ planned: TrainingActivity; mode: PlannedMode } | null>(null);
  const firstName = user?.name?.split(" ")[0];

  const noHistory = overview.data && overview.data.totals.allTime.activityCount === 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow">{longDate(today)}</p>
          <h1 className="mt-1 font-display text-4xl font-extrabold tracking-tight sm:text-5xl">
            {greeting()}
            {firstName ? `, ${firstName}` : ""}
          </h1>
        </div>
        <ButtonLink to="/log" icon={<PlusIcon />} className="max-sm:!hidden">
          Log Activity
        </ButtonLink>
      </div>

      {/* Today: plan and actual side by side. */}
      <section aria-labelledby="today" className="grid gap-4 lg:grid-cols-2 [&>*]:min-w-0">
        <h2 id="today" className="sr-only">
          Today
        </h2>
        <Card className="p-5">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="eyebrow">Today's plan</h3>
            <Link to="/training" className="text-sm font-semibold text-ember-600 hover:underline">
              Training
            </Link>
          </div>
          {day.error ? (
            <ErrorState message={day.error} onRetry={day.reload} />
          ) : !day.data ? (
            <Skeleton className="h-24" />
          ) : day.data.planned.length === 0 ? (
            <div className="py-4">
              <p className="font-display text-xl font-bold">Nothing planned today.</p>
              <p className="text-sm text-muted">Enjoy your rest day or log an activity if you run.</p>
            </div>
          ) : (
            <div className="divide-y divide-line">
              {day.data.planned.map((p) => (
                <PlannedRow
                  key={p.id}
                  planned={p}
                  onOpen={() => setOpen({ planned: p, mode: "view" })}
                  action={
                    p.status === "planned" && p.activityType !== "rest" ? (
                      <Button size="sm" icon={<CheckIcon width={16} height={16} strokeWidth={3} />}
                        onClick={() => setOpen({ planned: p, mode: "complete" })}>
                        Complete
                      </Button>
                    ) : undefined
                  }
                />
              ))}
            </div>
          )}
        </Card>

        <Card className="p-5">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="eyebrow">Today's activities</h3>
            <Link to={`/log?date=${today}`} className="flex items-center gap-1 text-sm font-semibold text-ember-600 hover:underline">
              <PlusIcon width={16} height={16} /> Log
            </Link>
          </div>
          {!day.data ? (
            !day.error && <Skeleton className="h-24" />
          ) : day.data.actual.length === 0 ? (
            <div className="py-4">
              <p className="font-display text-xl font-bold">No activities yet today.</p>
              <p className="text-sm text-muted">Back from a run? Log it in under a minute.</p>
            </div>
          ) : (
            <>
              <div className="divide-y divide-line">
                {day.data.actual.map((a) => (
                  <ActivityRow key={a.id} activity={a} to={`/runs/${a.id}`} />
                ))}
              </div>
              <div className="mt-2 flex items-baseline justify-between border-t border-line pt-3">
                <span className="eyebrow">Total</span>
                <span className="num text-2xl font-bold">{formatDistance(day.data.distanceKm)}</span>
              </div>
            </>
          )}
        </Card>
      </section>

      {noHistory && (
        <EmptyState
          title="No runs yet."
          message="Start building your running history."
          action={
            <ButtonLink to="/log" icon={<PlusIcon />}>
              Log Your First Activity
            </ButtonLink>
          }
        />
      )}

      {/* Period totals. */}
      <section aria-label="Mileage totals" className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {(["week", "month", "year"] as const).map((k, i) => {
          const t = overview.data?.totals[k];
          const label = { week: "This week", month: "This month", year: "This year" }[k];
          return (
            <Card key={k} className={`relative overflow-hidden p-5 ${i === 0 ? "bg-ink text-white" : ""}`}>
              {i === 0 && <ElevationLine className="absolute inset-x-0 bottom-0 h-16 w-full text-ember-500" />}
              <div className="relative">
                <p className={`eyebrow ${i === 0 ? "!text-white/60" : ""}`}>{label}</p>
                {t ? (
                  <>
                    <BigNumber className="mt-2 block" value={formatKm(t.distanceKm)} unit="km" />
                    <p className={`mt-1 text-sm ${i === 0 ? "text-white/70" : "text-muted"}`}>
                      {t.activityCount} {t.activityCount === 1 ? "activity" : "activities"}
                    </p>
                  </>
                ) : overview.error ? (
                  <p className="mt-2 text-sm">Unavailable</p>
                ) : (
                  <Skeleton className="mt-2 h-12 w-32" />
                )}
              </div>
            </Card>
          );
        })}
      </section>

      <div className="grid gap-4 lg:grid-cols-3 [&>*]:min-w-0">
        <Card className="p-5 lg:col-span-1">
          <h2 className="eyebrow mb-4">Planned vs actual · this week</h2>
          {week.error ? (
            <ErrorState message={week.error} onRetry={week.reload} />
          ) : !week.data ? (
            <Skeleton className="h-40" />
          ) : week.data.training.totalActivities === 0 ? (
            <div>
              <p className="text-sm text-muted">No training planned this week.</p>
              <ButtonLink to="/training" variant="secondary" size="sm" className="mt-3" icon={<PlusIcon width={16} height={16} />}>
                Add Training
              </ButtonLink>
            </div>
          ) : (
            <PlanComparison data={week.data.plannedVsActual} label="This week" />
          )}
        </Card>
        <div className="lg:col-span-2">
          <MileagePanel refreshKey={version} />
        </div>
      </div>

      <PlannedActivityModal
        planned={open?.planned ?? null}
        initialMode={open?.mode}
        onClose={() => setOpen(null)}
        onChanged={refresh}
      />
    </div>
  );
}
