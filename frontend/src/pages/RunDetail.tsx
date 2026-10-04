import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { plannedTarget, TypeBadge } from "../components/activities/ActivityCards";
import { Button, ButtonLink } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { ErrorState, LoadingBlock } from "../components/ui/Feedback";
import { ArrowLeft, HeartIcon, PencilIcon, RouteIcon, TimerIcon, TrashIcon } from "../components/ui/Icons";
import { ConfirmDialog } from "../components/ui/Modal";
import { StatusBadge } from "../components/ui/Status";
import { useAsync } from "../hooks/useAsync";
import { useMeta } from "../hooks/useMeta";
import { api, errorMessage } from "../services/api";
import { fullDate } from "../utils/dates";
import { formatClock, formatKm, formatPace, formatSignedKm } from "../utils/format";

/** /runs/:id — one actual activity, with its plan if it had one. */
export default function RunDetail() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { typeLabel } = useMeta();
  const { data: a, error, reload } = useAsync(() => api.getActivity(id), [id]);
  const [confirm, setConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const back = (
    <Link to="/runs" className="inline-flex items-center gap-1 text-sm font-semibold text-muted hover:text-ink">
      <ArrowLeft width={16} height={16} /> Runs
    </Link>
  );

  if (error)
    return (
      <div className="space-y-4">
        {back}
        <ErrorState message={error === "Not found." ? "This activity doesn't exist." : error} onRetry={reload} />
      </div>
    );
  if (!a) return <LoadingBlock />;

  const p = a.plannedActivity;
  const moving = a.distanceKm && a.paceSecondsPerKm ? a.distanceKm * a.paceSecondsPerKm : null;

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      {back}
      <Card className="overflow-hidden">
        <div className="relative bg-ink px-6 pt-6 pb-8 text-white sm:px-8">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <TypeBadge type={a.activityType} tone="ember" />
              <div>
                <p className="eyebrow !text-white/60">{typeLabel(a.activityType)}</p>
                <h1 className="font-display text-3xl font-extrabold leading-tight sm:text-4xl">{a.title}</h1>
              </div>
            </div>
          </div>
          <p className="mt-2 text-white/70">{fullDate(a.activityDate)}</p>
          <div className="mt-6 flex flex-wrap items-end gap-x-10 gap-y-4">
            <div>
              <p className="num text-6xl font-bold leading-none sm:text-7xl">
                {formatKm(a.distanceKm, 2)}
                <span className="ml-1 font-sans text-lg font-semibold text-white/60">km</span>
              </p>
            </div>
            <dl className="flex gap-8">
              <div>
                <dt className="flex items-center gap-1 text-xs font-semibold text-white/60">
                  <RouteIcon width={14} height={14} /> Pace
                </dt>
                <dd className="num text-3xl font-bold">{formatPace(a.paceSecondsPerKm)}</dd>
              </div>
              <div>
                <dt className="flex items-center gap-1 text-xs font-semibold text-white/60">
                  <HeartIcon width={14} height={14} /> Avg HR
                </dt>
                <dd className="num text-3xl font-bold">{a.averageHeartRate ? `${a.averageHeartRate}` : "—"}<span className="ml-1 font-sans text-sm text-white/60">bpm</span></dd>
              </div>
              {moving && (
                <div>
                  <dt className="flex items-center gap-1 text-xs font-semibold text-white/60">
                    <TimerIcon width={14} height={14} /> Time
                  </dt>
                  <dd className="num text-3xl font-bold">{formatClock(moving)}</dd>
                </div>
              )}
            </dl>
          </div>
        </div>

        <div className="space-y-5 p-6 sm:p-8">
          <div>
            <h2 className="eyebrow mb-1">Training</h2>
            {p ? (
              <Link to={`/training/${p.trainingBlockId}`} className="font-semibold text-ember-600 hover:underline">
                {p.trainingBlockName}
              </Link>
            ) : (
              <p className="font-semibold">Unplanned Activity</p>
            )}
          </div>
          {p && (
            <div className="rounded-2xl bg-paper p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="eyebrow">Planned</h2>
                <StatusBadge status={p.status} />
              </div>
              <p className="mt-1 font-semibold">
                {p.title}
                {plannedTarget(p) && <span className="font-normal text-ink-soft"> — {plannedTarget(p)}</span>}
              </p>
              {p.plannedDistanceKm != null && a.distanceKm != null && (
                <p className="mt-1 text-sm text-muted">
                  Planned {formatKm(p.plannedDistanceKm)} km · Actual {formatKm(a.distanceKm)} km · Difference{" "}
                  {formatSignedKm(a.distanceKm - p.plannedDistanceKm)}
                </p>
              )}
            </div>
          )}
          <div>
            <h2 className="eyebrow mb-1">Comment</h2>
            {a.comment ? <p className="whitespace-pre-line text-lg">“{a.comment}”</p> : <p className="text-muted">No comment.</p>}
          </div>
          <div className="flex flex-wrap gap-2 border-t border-line pt-5">
            <ButtonLink to={`/runs/${a.id}/edit`} variant="secondary" icon={<PencilIcon width={18} height={18} />}>
              Edit
            </ButtonLink>
            <Button variant="ghost" className="text-missed" icon={<TrashIcon width={18} height={18} />} onClick={() => setConfirm(true)}>
              Delete
            </Button>
          </div>
        </div>
      </Card>

      <ConfirmDialog
        open={confirm}
        title="Delete this activity?"
        message={
          <>
            <p>This will also affect your mileage statistics.</p>
            {p && (
              <p className="mt-2">
                The planned activity <strong>{p.title}</strong> stays in your plan and returns to <em>planned</em>.
              </p>
            )}
          </>
        }
        confirmLabel="Delete"
        busy={deleting}
        error={deleteError}
        onCancel={() => setConfirm(false)}
        onConfirm={async () => {
          setDeleting(true);
          setDeleteError(null);
          try {
            await api.deleteActivity(a.id);
            navigate("/runs", { replace: true });
          } catch (e) {
            setDeleteError(errorMessage(e, "Unable to delete activity. Please try again."));
            setDeleting(false);
          }
        }}
      />
    </div>
  );
}
