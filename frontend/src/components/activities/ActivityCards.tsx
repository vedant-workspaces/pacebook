import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useMeta } from "../../hooks/useMeta";
import type { Activity, TrainingActivity } from "../../types";
import { formatDistance, formatDuration, formatKm, formatPace } from "../../utils/format";
import { ActivityGlyph, HeartIcon, LinkIcon } from "../ui/Icons";
import { STATUS, StatusDot } from "../ui/Status";

/** Round badge with the activity's glyph. */
export function TypeBadge({ type, tone = "ember" }: { type: string; tone?: "ember" | "ink" | "muted" }) {
  const tones = {
    ember: "bg-ember-50 text-ember-600",
    ink: "bg-ink text-white",
    muted: "bg-paper text-ink-soft",
  };
  return (
    <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${tones[tone]}`}>
      <ActivityGlyph type={type} width={20} height={20} />
    </span>
  );
}

/** One actual activity: what the runner did. */
export function ActivityRow({ activity, to }: { activity: Activity; to?: string }) {
  const { typeLabel } = useMeta();
  const body = (
    <div className="flex items-center gap-3 py-3">
      <TypeBadge type={activity.activityType} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <p className="truncate font-semibold">{activity.title}</p>
          {activity.trainingActivityId && (
            <LinkIcon width={14} height={14} className="shrink-0 text-muted" aria-label="Linked to plan" role="img" />
          )}
        </div>
        <p className="truncate text-xs text-muted">{typeLabel(activity.activityType)}</p>
      </div>
      <div className="grid grid-cols-3 gap-3 text-right sm:gap-5">
        <Stat value={formatKm(activity.distanceKm)} unit="km" />
        <Stat value={formatPace(activity.paceSecondsPerKm, false)} unit="/km" />
        <Stat value={activity.averageHeartRate ?? "—"} unit="bpm" />
      </div>
    </div>
  );
  if (!to) return body;
  return (
    <Link to={to} className="-mx-2 block rounded-xl px-2 hover:bg-paper focus-visible:bg-paper">
      {body}
    </Link>
  );
}

function Stat({ value, unit }: { value: ReactNode; unit: string }) {
  return (
    <div className="min-w-11">
      <div className="num text-lg font-bold leading-tight">{value}</div>
      <div className="text-[0.65rem] font-semibold uppercase tracking-wide text-muted">{unit}</div>
    </div>
  );
}

/** Short summary of a planned target: "8 km @ 5:45/km" or "45 min". */
export function plannedTarget(p: TrainingActivity): string {
  const parts: string[] = [];
  if (p.plannedDistanceKm) parts.push(formatDistance(p.plannedDistanceKm));
  if (p.plannedPaceSecondsPerKm) parts.push(`@ ${formatPace(p.plannedPaceSecondsPerKm)}`);
  if (!p.plannedDistanceKm && p.plannedDurationSeconds) parts.push(formatDuration(p.plannedDurationSeconds));
  return parts.join(" ");
}

/** One planned activity: what the runner intended to do. */
export function PlannedRow({
  planned,
  onOpen,
  action,
}: {
  planned: TrainingActivity;
  onOpen?: () => void;
  action?: ReactNode;
}) {
  const { typeLabel, reasonLabel } = useMeta();
  const target = plannedTarget(planned);
  const s = STATUS[planned.status];
  const content = (
    <>
      <StatusDot status={planned.status} size={28} />
      <div className="min-w-0 flex-1 text-left">
        <p className={`truncate font-semibold ${planned.status === "skipped" ? "text-muted line-through decoration-1" : ""}`}>
          {planned.title}
        </p>
        <p className="truncate text-sm text-muted">
          {target || typeLabel(planned.activityType)}
          <span className={`ml-2 text-xs font-bold uppercase tracking-wider ${s.text}`}>{s.label}</span>
          {planned.status === "skipped" && planned.completionReason && (
            <span className="text-xs"> — {reasonLabel(planned.completionReason)}</span>
          )}
        </p>
      </div>
    </>
  );
  return (
    <div className="flex items-center gap-3 py-2.5">
      {onOpen ? (
        <button type="button" onClick={onOpen} className="-mx-2 flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-1 hover:bg-paper">
          {content}
        </button>
      ) : (
        <div className="flex min-w-0 flex-1 items-center gap-3">{content}</div>
      )}
      {action}
    </div>
  );
}

export function HeartRate({ bpm }: { bpm?: number }) {
  if (!bpm) return null;
  return (
    <span className="inline-flex items-center gap-1">
      <HeartIcon width={14} height={14} className="text-missed" /> {bpm} bpm
    </span>
  );
}
