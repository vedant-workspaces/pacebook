import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useMeta } from "../../hooks/useMeta";
import { api, errorMessage } from "../../services/api";
import type { ActivityInput, TrainingActivity, TrainingBlock } from "../../types";
import { longDate } from "../../utils/dates";
import { formatDistance, formatDuration, formatPace, formatSignedKm } from "../../utils/format";
import { ActivityForm } from "../activities/ActivityForm";
import { HeartRate, TypeBadge } from "../activities/ActivityCards";
import { Button } from "../ui/Button";
import { FormError } from "../ui/Form";
import { CheckIcon, PencilIcon, TildeIcon, TrashIcon, XIcon } from "../ui/Icons";
import { ConfirmDialog, Modal } from "../ui/Modal";
import { StatusBadge } from "../ui/Status";
import { PlannedActivityForm } from "./PlannedActivityForm";
import { SkipForm } from "./SkipForm";

export type PlannedMode = "view" | "complete" | "modify" | "skip" | "edit";

interface Props {
  planned: TrainingActivity | null;
  /** Needed for editing (date bounds). Fetched on demand if not given. */
  block?: Pick<TrainingBlock, "startDate" | "endDate">;
  initialMode?: PlannedMode;
  onClose: () => void;
  /** Called after any change so the parent can refresh. */
  onChanged: () => void;
}

/**
 * Everything you can do with one planned activity: see it, mark it
 * completed / modified / not completed, edit it, or delete it.
 */
export function PlannedActivityModal({ planned, block, initialMode = "view", onClose, onChanged }: Props) {
  const meta = useMeta();
  const [mode, setMode] = useState<PlannedMode>(initialMode);
  const [current, setCurrent] = useState<TrainingActivity | null>(planned);
  const [bounds, setBounds] = useState(block);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    setCurrent(planned);
    setMode(initialMode);
    setError(null);
    // Refresh to pick up the linked actual activity.
    if (planned) api.getPlanned(planned.id).then(setCurrent).catch(() => {});
  }, [planned, initialMode]);

  useEffect(() => {
    if (mode === "edit" && !bounds && current) {
      api.getBlock(current.trainingBlockId).then(setBounds).catch(() => {});
    }
  }, [mode, bounds, current]);

  if (!current) return <Modal open={false} onClose={onClose} title="" children={null} />;
  const p = current;

  const done = (updated?: TrainingActivity) => {
    if (updated) setCurrent(updated);
    setMode("view");
    onChanged();
  };

  async function run(fn: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const resultInput = (v: ActivityInput) => ({
    distanceKm: v.distanceKm,
    paceSecondsPerKm: v.paceSecondsPerKm,
    averageHeartRate: v.averageHeartRate,
    comment: v.comment,
    activityType: mode === "modify" ? v.activityType : undefined,
  });

  const titles: Record<PlannedMode, string> = {
    view: p.title,
    complete: "Complete Activity",
    modify: "Completed Differently",
    skip: "Why didn't you complete this?",
    edit: "Edit Planned Activity",
  };

  const actual = p.actualActivity;
  const target = [
    p.plannedDistanceKm ? formatDistance(p.plannedDistanceKm) : null,
    p.plannedPaceSecondsPerKm ? `Target pace ${formatPace(p.plannedPaceSecondsPerKm)}` : null,
    p.plannedDurationSeconds ? formatDuration(p.plannedDurationSeconds) : null,
  ].filter(Boolean);

  return (
    <>
      <Modal
        open={!!planned && !confirmDelete}
        onClose={mode === "view" ? onClose : () => setMode("view")}
        title={titles[mode]}
        subtitle={
          <>
            {longDate(p.activityDate)}
            {p.trainingBlockName && <> · {p.trainingBlockName}</>}
          </>
        }
      >
        {mode === "view" && (
          <div className="space-y-5">
            <div className="flex items-start gap-3">
              <TypeBadge type={p.activityType} tone="ink" />
              <div className="min-w-0 flex-1">
                <div className="eyebrow">Planned · {meta.typeLabel(p.activityType)}</div>
                <p className="num mt-0.5 text-2xl font-bold">{target[0] ?? meta.typeLabel(p.activityType)}</p>
                {target.slice(1).map((t) => (
                  <p key={t} className="text-sm text-ink-soft">
                    {t}
                  </p>
                ))}
              </div>
              <StatusBadge status={p.status} />
            </div>
            {p.description && <p className="whitespace-pre-line rounded-xl bg-paper px-4 py-3 text-sm">{p.description}</p>}

            {p.status === "skipped" && (
              <div className="rounded-xl border border-missed/15 bg-missed-50 px-4 py-3 text-sm">
                <p className="font-semibold text-missed">Not completed — {meta.reasonLabel(p.completionReason)}</p>
                {p.completionNotes && <p className="mt-1 text-ink-soft">{p.completionNotes}</p>}
              </div>
            )}

            {actual && (
              <div className="rounded-2xl border border-line p-4">
                <div className="flex items-center justify-between">
                  <div className="eyebrow">Actual{p.status === "modified" && ` · ${meta.typeLabel(actual.activityType)}`}</div>
                  <Link to={`/runs/${actual.id}`} className="text-sm font-semibold text-ember-600 hover:underline">
                    Open activity
                  </Link>
                </div>
                <div className="mt-2 flex flex-wrap items-baseline gap-x-5 gap-y-1">
                  <span className="num text-3xl font-bold">{formatDistance(actual.distanceKm)}</span>
                  <span className="num text-xl font-semibold">{formatPace(actual.paceSecondsPerKm)}</span>
                  <span className="text-sm font-medium">
                    <HeartRate bpm={actual.averageHeartRate} />
                  </span>
                </div>
                {p.plannedDistanceKm != null && actual.distanceKm != null && (
                  <p className="mt-1 text-sm text-muted">
                    Planned {formatDistance(p.plannedDistanceKm)} · Actual {formatDistance(actual.distanceKm)} · Difference{" "}
                    {formatSignedKm(actual.distanceKm - p.plannedDistanceKm)}
                  </p>
                )}
                {actual.comment && <p className="mt-3 text-sm italic text-ink-soft">“{actual.comment}”</p>}
              </div>
            )}

            <FormError message={error} />

            <div className="grid gap-2 sm:grid-cols-2">
              {!actual && (
                <>
                  <Button size="lg" icon={<CheckIcon />} onClick={() => setMode("complete")}>
                    Mark Completed
                  </Button>
                  <Button size="lg" variant="secondary" icon={<XIcon />} onClick={() => setMode("skip")}>
                    {p.status === "skipped" ? "Change Reason" : "Mark Not Completed"}
                  </Button>
                  <Button size="lg" variant="secondary" icon={<TildeIcon />} onClick={() => setMode("modify")}>
                    Completed Differently
                  </Button>
                </>
              )}
              {actual && (
                <>
                  <Button size="lg" variant="secondary" icon={<PencilIcon />}
                    onClick={() => setMode(p.status === "modified" ? "modify" : "complete")}>
                    Edit Result
                  </Button>
                  {p.status === "completed" ? (
                    <Button size="lg" variant="secondary" icon={<TildeIcon />} onClick={() => setMode("modify")}>
                      Mark as Modified
                    </Button>
                  ) : (
                    <Button size="lg" variant="secondary" icon={<CheckIcon />} onClick={() => setMode("complete")}>
                      Mark as Completed
                    </Button>
                  )}
                </>
              )}
              {p.status === "skipped" && (
                <Button size="lg" variant="ghost" loading={busy}
                  onClick={() => run(async () => done(await api.reset(p.id)))}>
                  Back to Planned
                </Button>
              )}
              <Button size="lg" variant="ghost" icon={<PencilIcon />} onClick={() => setMode("edit")}>
                Edit Plan
              </Button>
              <Button size="lg" variant="ghost" icon={<TrashIcon />} className="text-missed" onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            </div>
          </div>
        )}

        {(mode === "complete" || mode === "modify") && (
          <ActivityForm
            key={mode}
            mode={mode}
            planned={p}
            submitLabel="Save Activity"
            onCancel={() => setMode("view")}
            onSubmit={async (v) => {
              const updated = mode === "complete" ? await api.complete(p.id, resultInput(v)) : await api.modify(p.id, resultInput(v));
              done(updated);
            }}
          />
        )}

        {mode === "skip" && (
          <SkipForm
            planned={p}
            onCancel={() => setMode("view")}
            onSubmit={async (reason, notes) => done(await api.skip(p.id, reason, notes))}
          />
        )}

        {mode === "edit" &&
          (bounds ? (
            <PlannedActivityForm
              block={bounds}
              initial={p}
              onCancel={() => setMode("view")}
              onSubmit={async (v) => done(await api.updatePlanned(p.id, v))}
            />
          ) : (
            <p className="text-muted">Loading…</p>
          ))}
      </Modal>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete planned activity?"
        message={
          actual
            ? "The plan entry will be removed. The activity you logged for it is kept as an unplanned activity."
            : "This removes it from your training plan."
        }
        confirmLabel="Delete"
        busy={busy}
        error={error}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={() =>
          run(async () => {
            await api.deletePlanned(p.id);
            setConfirmDelete(false);
            onChanged();
            onClose();
          })
        }
      />
    </>
  );
}
