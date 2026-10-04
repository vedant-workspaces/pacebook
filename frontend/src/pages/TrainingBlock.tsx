import { useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ActivityRow } from "../components/activities/ActivityCards";
import { Legend, PlannedActualBars } from "../components/charts/MileageChart";
import { Button, ButtonLink } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { ErrorState, LoadingBlock, Skeleton } from "../components/ui/Feedback";
import { ArrowLeft, ChevronLeft, ChevronRight, PencilIcon, PlusIcon, TrashIcon, TrophyIcon } from "../components/ui/Icons";
import { ConfirmDialog, Modal } from "../components/ui/Modal";
import { DayView } from "../components/training/DayView";
import { PlannedActivityForm } from "../components/training/PlannedActivityForm";
import { PlannedActivityModal } from "../components/training/PlannedActivityModal";
import { TrainingCalendar, type CalendarView } from "../components/training/TrainingCalendar";
import { useAsync } from "../hooks/useAsync";
import { api, errorMessage } from "../services/api";
import type { BlockStats, TrainingActivity, TrainingBlock as Block } from "../types";
import { addDays, addMonths, dateRange, daysBetween, longDate, monthStart, monthYear, shortDate, todayISO, weekStart } from "../utils/dates";
import { formatDistance, formatKm, formatPace, formatPct, formatSignedKm } from "../utils/format";

export default function TrainingBlockPage() {
  const { id = "" } = useParams();
  const block = useAsync(() => api.getBlock(id), [id]);

  if (block.error) {
    return (
      <div className="space-y-4">
        <BackLink />
        <ErrorState message={block.error === "Not found." ? "This training block doesn't exist." : block.error} onRetry={block.reload} />
      </div>
    );
  }
  if (!block.data) return <LoadingBlock label="Loading training block" />;
  return <BlockDetail block={block.data} />;
}

function BackLink() {
  return (
    <Link to="/training" className="inline-flex items-center gap-1 text-sm font-semibold text-muted hover:text-ink">
      <ArrowLeft width={16} height={16} /> Training
    </Link>
  );
}

function initialAnchor(b: Block) {
  const t = todayISO();
  if (t < b.startDate) return b.startDate;
  if (t > b.endDate) return b.endDate;
  return t;
}

function BlockDetail({ block }: { block: Block }) {
  const navigate = useNavigate();
  const [view, setView] = useState<CalendarView>("week");
  const [anchor, setAnchor] = useState(() => initialAnchor(block));
  const [version, setVersion] = useState(0);
  const refresh = () => setVersion((v) => v + 1);

  const [openPlanned, setOpenPlanned] = useState<TrainingActivity | null>(null);
  const [openDay, setOpenDay] = useState<string | null>(null);
  const [addDate, setAddDate] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Visible range: one Monday→Sunday week, or the full weeks covering a month.
  const from = view === "week" ? weekStart(anchor) : weekStart(monthStart(anchor));
  const days =
    view === "week" ? 7 : Math.ceil((daysBetween(from, addDays(addMonths(monthStart(anchor), 1), -1)) + 1) / 7) * 7;
  const to = addDays(from, days - 1);

  const range = useAsync(
    () =>
      Promise.all([
        api.listPlanned(block.id, from, to),
        api.listActivities({ from, to, limit: 100 }),
      ]).then(([planned, actual]) => ({ planned, actual: actual.data })),
    [block.id, from, to, version],
  );
  const stats = useAsync(() => api.blockStats(block.id), [block.id, version]);
  const day = useAsync(() => (openDay ? api.day(openDay) : Promise.resolve(null)), [openDay, version]);

  const step = (dir: 1 | -1) => setAnchor((a) => (view === "week" ? addDays(a, 7 * dir) : addMonths(a, dir)));
  const weekNo = Math.floor(daysBetween(weekStart(block.startDate), from) / 7) + 1;
  const totalWeeks = Math.ceil((daysBetween(weekStart(block.startDate), block.endDate) + 1) / 7);
  const rangeLabel =
    view === "week"
      ? `${shortDate(from)} – ${shortDate(to)}${weekNo >= 1 && weekNo <= totalWeeks ? ` · Week ${weekNo} of ${totalWeeks}` : ""}`
      : monthYear(monthStart(anchor));

  return (
    <div className="space-y-6">
      <BackLink />

      {/* Block hero */}
      <Card className="relative overflow-hidden bg-ink p-6 text-white sm:p-8">
        <svg className="pointer-events-none absolute inset-x-0 bottom-0 h-24 w-full text-ember-500/25" viewBox="0 0 400 60" preserveAspectRatio="none" aria-hidden>
          <path d="M0 55 L40 45 L70 50 L110 30 L150 38 L190 15 L230 28 L270 10 L310 30 L350 22 L400 34" fill="none" stroke="currentColor" strokeWidth="3" />
        </svg>
        <div className="relative flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="eyebrow !text-white/60">Training block · {dateRange(block.startDate, block.endDate)}</p>
            <h1 className="mt-1 font-display text-4xl font-extrabold leading-none tracking-tight sm:text-5xl">{block.name}</h1>
            {block.goal && (
              <p className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-ember-500 px-3 py-1 text-sm font-bold">
                <TrophyIcon width={16} height={16} /> Goal: {block.goal}
              </p>
            )}
            {block.description && <p className="mt-3 max-w-2xl whitespace-pre-line text-white/70">{block.description}</p>}
          </div>
          <div className="flex gap-2">
            <ButtonLink to={`/training/${block.id}/edit`} variant="secondary" size="sm" icon={<PencilIcon width={16} height={16} />}>
              Edit
            </ButtonLink>
            <Button variant="secondary" size="sm" aria-label="Delete training block" onClick={() => setConfirmDelete(true)}>
              <TrashIcon width={16} height={16} />
            </Button>
          </div>
        </div>
        <SummaryStrip stats={stats.data} />
      </Card>

      {/* Calendar */}
      <section aria-labelledby="cal-title">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <h2 id="cal-title" className="sr-only">
              Training calendar
            </h2>
            <Button variant="secondary" size="sm" aria-label={`Previous ${view}`} onClick={() => step(-1)}>
              <ChevronLeft width={18} height={18} />
            </Button>
            <Button variant="secondary" size="sm" aria-label={`Next ${view}`} onClick={() => step(1)}>
              <ChevronRight width={18} height={18} />
            </Button>
            <p className="ml-1 font-display text-xl font-bold" aria-live="polite">
              {rangeLabel}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => setAnchor(initialAnchor(block))}>
              Today
            </Button>
            <div role="tablist" aria-label="Calendar view" className="flex rounded-xl bg-white p-1 ring-1 ring-line">
              {(["week", "month"] as const).map((v) => (
                <button key={v} type="button" role="tab" aria-selected={view === v} onClick={() => setView(v)}
                  className={`h-8 rounded-lg px-3 text-sm font-semibold capitalize ${view === v ? "bg-ink text-white" : "text-muted hover:text-ink"}`}>
                  {v}
                </button>
              ))}
            </div>
            <Button size="sm" icon={<PlusIcon width={16} height={16} />} onClick={() => setAddDate(initialAnchor(block))}>
              Add Training
            </Button>
          </div>
        </div>
        <p className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-xs font-semibold text-muted" aria-label="Status key">
          <span><span className="text-done">✓</span> Completed</span>
          <span><span className="text-changed">~</span> Modified</span>
          <span><span className="text-missed">✕</span> Skipped</span>
          <span><span className="text-planned">○</span> Planned</span>
        </p>
        {range.error ? (
          <ErrorState message={range.error} onRetry={range.reload} />
        ) : !range.data ? (
          <Skeleton className="h-48" />
        ) : (
          <div className={range.loading ? "opacity-70 transition-opacity" : ""} aria-busy={range.loading}>
            <TrainingCalendar
              view={view}
              from={from}
              days={days}
              month={view === "month" ? monthStart(anchor).slice(0, 7) : undefined}
              block={block}
              planned={range.data.planned}
              actual={range.data.actual}
              onOpenPlanned={setOpenPlanned}
              onOpenDay={setOpenDay}
              onAdd={setAddDate}
            />
          </div>
        )}
      </section>

      <BlockStatsSection stats={stats.data} error={stats.error} onRetry={stats.reload} />

      {/* Day view */}
      <Modal open={!!openDay && !openPlanned && !addDate} onClose={() => setOpenDay(null)} title={openDay ? longDate(openDay) : ""} size="lg">
        {day.data ? (
          <DayView day={day.data} onOpenPlanned={setOpenPlanned} onAddPlanned={openDay && openDay >= block.startDate && openDay <= block.endDate ? () => setAddDate(openDay) : undefined} />
        ) : day.error ? (
          <ErrorState message={day.error} onRetry={day.reload} />
        ) : (
          <Skeleton className="h-48" />
        )}
      </Modal>

      <PlannedActivityModal planned={openPlanned} block={block} onClose={() => setOpenPlanned(null)} onChanged={refresh} />

      <Modal open={!!addDate} onClose={() => setAddDate(null)} title="Add Activity" subtitle={addDate ? longDate(addDate) : undefined}>
        {addDate && (
          <PlannedActivityForm
            key={addDate}
            block={block}
            date={addDate}
            onCancel={() => setAddDate(null)}
            onSubmit={async (v) => {
              await api.createPlanned(block.id, v);
              setAddDate(null);
              refresh();
            }}
          />
        )}
      </Modal>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete this training block?"
        message="All planned activities in this block are deleted. Activities you've logged are kept in your run history as unplanned activities."
        confirmLabel="Delete Block"
        busy={deleting}
        error={deleteError}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={async () => {
          setDeleting(true);
          setDeleteError(null);
          try {
            await api.deleteBlock(block.id);
            navigate("/training");
          } catch (e) {
            setDeleteError(errorMessage(e));
            setDeleting(false);
          }
        }}
      />
    </div>
  );
}

function SummaryStrip({ stats }: { stats?: BlockStats }) {
  const s = stats?.summary;
  const items = [
    { label: "Completion", value: formatPct(s?.completionPct) },
    { label: "Planned", value: s ? `${formatKm(s.plannedDistanceKm, 0)} km` : "—" },
    { label: "Actual", value: s ? `${formatKm(s.actualDistanceKm, 0)} km` : "—" },
    { label: "Sessions", value: s ? `${s.completed + s.modified} / ${s.due}` : "—" },
  ];
  return (
    <dl className="relative mt-6 grid grid-cols-2 gap-4 border-t border-white/15 pt-5 sm:grid-cols-4">
      {items.map((i) => (
        <div key={i.label}>
          <dt className="text-xs font-semibold text-white/60">{i.label}</dt>
          <dd className="num text-3xl font-bold">{i.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function BlockStatsSection({ stats, error, onRetry }: { stats?: BlockStats; error: string | null; onRetry: () => void }) {
  if (error) return <ErrorState message={error} onRetry={onRetry} />;
  if (!stats) return <Skeleton className="h-64" />;
  const s = stats.summary;
  const rows = stats.weeks.map((w, i) => ({
    label: `W${i + 1}`,
    full: `Week ${i + 1} · ${shortDate(w.weekStart)}`,
    actual: Math.round(w.actualDistanceKm * 10) / 10,
    planned: Math.round(w.plannedDistanceKm * 10) / 10,
  }));
  const today = todayISO();

  return (
    <section aria-labelledby="block-stats" className="space-y-4">
      <h2 id="block-stats" className="eyebrow">Block statistics</h2>
      <div className="grid gap-4 lg:grid-cols-3 [&>*]:min-w-0">
        <Card className="p-5">
          <h3 className="eyebrow mb-3">Activities</h3>
          <dl className="grid grid-cols-2 gap-3">
            <StatPair label="Planned sessions" value={s.sessionCount} />
            <StatPair label="✓ Completed" value={s.completed} />
            <StatPair label="~ Modified" value={s.modified} />
            <StatPair label="✕ Skipped" value={s.skipped} />
            <StatPair label="Completion" value={formatPct(s.completionPct)} />
            <StatPair label="Still planned" value={s.planned} />
          </dl>
          <h3 className="eyebrow mt-5 mb-3">Distance</h3>
          <dl className="grid grid-cols-2 gap-3">
            <StatPair label="Planned mileage" value={formatDistance(s.plannedDistanceKm, 0)} />
            <StatPair label="Actual mileage" value={formatDistance(s.actualDistanceKm, 0)} />
            <StatPair label="Avg planned distance" value={formatDistance(stats.averagePlannedDistanceKm)} />
            <StatPair label="Avg actual distance" value={formatDistance(stats.averageActualDistanceKm)} />
            <StatPair label="Average pace" value={formatPace(stats.averagePaceSecondsPerKm)} />
          </dl>
          {stats.longestActivity && (
            <>
              <h3 className="eyebrow mt-5">Longest run</h3>
              <ActivityRow activity={stats.longestActivity} to={`/runs/${stats.longestActivity.id}`} />
            </>
          )}
        </Card>

        <Card className="p-5 lg:col-span-2">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h3 className="eyebrow">Planned vs actual mileage by week</h3>
            <Legend />
          </div>
          <PlannedActualBars rows={rows} caption="Planned and actual mileage for each week of the block" />
          <div className="mt-4 max-h-80 overflow-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-white text-left text-xs text-muted">
                <tr>
                  <th scope="col" className="py-2 font-semibold">Week</th>
                  <th scope="col" className="py-2 text-right font-semibold">Planned</th>
                  <th scope="col" className="py-2 text-right font-semibold">Actual</th>
                  <th scope="col" className="py-2 text-right font-semibold">Difference</th>
                  <th scope="col" className="hidden py-2 text-right font-semibold sm:table-cell">Completion</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {stats.weeks.map((w, i) => {
                  const future = w.weekStart > today;
                  return (
                    <tr key={w.weekStart} className={w.weekStart === weekStart(today) ? "bg-ember-50" : ""}>
                      <th scope="row" className="py-2 text-left font-semibold">
                        Week {i + 1} <span className="font-normal text-muted">· {shortDate(w.weekStart)}</span>
                      </th>
                      <td className="num py-2 text-right text-base">{formatKm(w.plannedDistanceKm)} km</td>
                      <td className="num py-2 text-right text-base">{future ? "—" : `${formatKm(w.actualDistanceKm)} km`}</td>
                      <td className="num py-2 text-right text-base text-ink-soft">
                        {future ? "—" : formatSignedKm(w.actualDistanceKm - w.plannedDistanceKm)}
                      </td>
                      <td className="num hidden py-2 text-right text-base sm:table-cell">
                        {w.due > 0 ? formatPct((w.completed / w.due) * 100) : "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </section>
  );
}

function StatPair({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="num text-xl font-bold">{value}</dd>
    </div>
  );
}
