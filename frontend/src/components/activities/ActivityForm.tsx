import { useState, type FormEvent } from "react";
import { useMeta } from "../../hooks/useMeta";
import { errorMessage, ApiError } from "../../services/api";
import type { Activity, ActivityInput, TrainingActivity } from "../../types";
import { todayISO } from "../../utils/dates";
import { formatClock, formatDistance, formatPace, parseNumber, parsePace, splitPace } from "../../utils/format";
import { Button } from "../ui/Button";
import { ChipGroup, Field, FormError, PaceInput, Select, TextArea, TextInput, UnitInput } from "../ui/Form";

/**
 * - log:      a new (usually unplanned) activity — every field
 * - edit:     an existing actual activity — every field
 * - complete: result of a planned activity done as planned — metrics only
 * - modify:   result of a planned activity done differently — type + metrics
 */
export type ActivityFormMode = "log" | "edit" | "complete" | "modify";

interface Props {
  mode: ActivityFormMode;
  planned?: TrainingActivity;
  initial?: Partial<Activity>;
  submitLabel?: string;
  onSubmit: (values: ActivityInput) => Promise<void>;
  onCancel?: () => void;
}

const QUICK_TYPES = ["easy_run", "long_run", "recovery", "tempo", "intervals"];

export function ActivityForm({ mode, planned, initial, submitLabel = "Save Activity", onSubmit, onCancel }: Props) {
  const meta = useMeta();
  const full = mode === "log" || mode === "edit";
  const existing = initial ?? planned?.actualActivity;

  const [date, setDate] = useState(existing?.activityDate ?? planned?.activityDate ?? todayISO());
  const [type, setType] = useState(
    existing?.activityType ?? (mode === "modify" ? "easy_run" : planned?.activityType) ?? "easy_run",
  );
  const [title, setTitle] = useState(full ? (existing?.title ?? "") : "");
  const [distance, setDistance] = useState(existing?.distanceKm != null ? String(existing.distanceKm) : "");
  const [[paceMin, paceSec], setPace] = useState<[string, string]>(splitPace(existing?.paceSecondsPerKm));
  const [hr, setHr] = useState(existing?.averageHeartRate != null ? String(existing.averageHeartRate) : "");
  const [comment, setComment] = useState(existing?.comment ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const effectiveType = mode === "complete" ? (planned?.activityType ?? type) : type;
  const distanceRequired = meta.isRunType(effectiveType) && mode !== "edit";

  const distanceNum = parseNumber(distance);
  const paceNum = parsePace(paceMin, paceSec);
  const movingTime =
    distanceNum && paceNum && Number.isFinite(distanceNum) && Number.isFinite(paceNum) ? distanceNum * paceNum : null;

  function validate(): ActivityInput | null {
    const e: Record<string, string> = {};
    const hrNum = parseNumber(hr);
    if (full && !date) e.date = "Choose a date.";
    if (distanceNum == null) {
      if (distanceRequired) e.distance = "Enter the distance you covered.";
    } else if (Number.isNaN(distanceNum) || distanceNum <= 0) {
      e.distance = "Distance must be greater than zero.";
    } else if (distanceNum >= 500) {
      e.distance = "Distance must be less than 500 km.";
    }
    if (paceNum != null && (Number.isNaN(paceNum) || paceNum < 120 || paceNum > 1800)) {
      e.pace = "Enter a pace between 2:00 and 30:00 per km.";
    }
    if (hrNum != null && (Number.isNaN(hrNum) || !Number.isInteger(hrNum) || hrNum < 30 || hrNum > 250)) {
      e.hr = "Heart rate must be a whole number between 30 and 250.";
    }
    if (comment.length > 2000) e.comment = "Keep comments under 2000 characters.";
    if (title.length > 120) e.title = "Keep the title under 120 characters.";
    setErrors(e);
    if (Object.keys(e).length) return null;
    return {
      activityDate: date,
      activityType: effectiveType,
      title: title.trim(),
      distanceKm: distanceNum,
      paceSecondsPerKm: paceNum,
      averageHeartRate: hrNum,
      comment: comment.trim() || null,
    };
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    setFormError(null);
    const values = validate();
    if (!values) return;
    setSaving(true);
    try {
      await onSubmit(values);
    } catch (err) {
      if (err instanceof ApiError && err.field) {
        const map: Record<string, string> = {
          distanceKm: "distance",
          paceSecondsPerKm: "pace",
          averageHeartRate: "hr",
          activityDate: "date",
        };
        setErrors({ [map[err.field] ?? err.field]: err.message });
      }
      setFormError(errorMessage(err, "Unable to save activity. Please try again."));
      setSaving(false);
    }
  }

  const quick = meta.activityTypes.filter((t) => QUICK_TYPES.includes(t.value));

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      {planned && (
        <div className="rounded-xl bg-paper px-4 py-3">
          <div className="eyebrow">Planned</div>
          <div className="mt-0.5 font-semibold">
            {planned.title}
            {planned.plannedDistanceKm != null && ` — ${formatDistance(planned.plannedDistanceKm)}`}
            {planned.plannedPaceSecondsPerKm != null && ` @ ${formatPace(planned.plannedPaceSecondsPerKm)}`}
          </div>
        </div>
      )}

      {full && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Date" error={errors.date}>
            {(id, d) => (
              <TextInput id={id} type="date" value={date} max="2200-12-31" aria-describedby={d}
                aria-invalid={!!errors.date} onChange={(e) => setDate(e.target.value)} required />
            )}
          </Field>
          <Field label="Activity type">
            {(id) => <Select id={id} options={meta.activityTypes} value={type} onChange={(e) => setType(e.target.value)} />}
          </Field>
        </div>
      )}
      {full && mode === "log" && (
        <div className="-mt-2">
          <ChipGroup label="Quick pick" options={quick} value={type} onChange={setType} />
        </div>
      )}

      {mode === "modify" && (
        <Field label="What did you actually do?" hint="The plan stays as it was; this records what happened.">
          {(id, d) => (
            <Select id={id} aria-describedby={d} options={meta.activityTypes} value={type} onChange={(e) => setType(e.target.value)} />
          )}
        </Field>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Field label="Distance" error={errors.distance} optional={!distanceRequired} className="col-span-2 sm:col-span-1">
          {(id, d) => (
            <UnitInput id={id} unit="km" placeholder="8.4" value={distance} aria-describedby={d}
              aria-invalid={!!errors.distance} onChange={(e) => setDistance(e.target.value)} autoFocus={!full} />
          )}
        </Field>
        <Field label="Pace" error={errors.pace} optional className="col-span-2 sm:col-span-1"
          hint={movingTime ? `Moving time ≈ ${formatClock(movingTime)}` : undefined}>
          {(id, d) => (
            <PaceInput id={id} minutes={paceMin} seconds={paceSec} describedBy={d} invalid={!!errors.pace}
              onChange={(m, s) => setPace([m, s])} />
          )}
        </Field>
        <Field label="Average heart rate" error={errors.hr} optional className="col-span-2 sm:col-span-1">
          {(id, d) => (
            <UnitInput id={id} unit="bpm" inputMode="numeric" placeholder="151" value={hr} aria-describedby={d}
              aria-invalid={!!errors.hr} onChange={(e) => setHr(e.target.value.replace(/[^\d]/g, ""))} />
          )}
        </Field>
        {full && (
          <Field label="Title" optional error={errors.title} className="col-span-2 sm:col-span-1"
            hint={`Defaults to “${meta.typeLabel(type)}”`}>
            {(id, d) => (
              <TextInput id={id} placeholder="Morning run" value={title} aria-describedby={d} maxLength={120}
                onChange={(e) => setTitle(e.target.value)} />
            )}
          </Field>
        )}
      </div>

      <Field label="Comment" optional error={errors.comment}>
        {(id, d) => (
          <TextArea id={id} placeholder="How did it feel?" value={comment} aria-describedby={d} maxLength={2000}
            onChange={(e) => setComment(e.target.value)} />
        )}
      </Field>

      <FormError message={formError} />

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        {onCancel && (
          <Button variant="ghost" size="lg" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" size="lg" loading={saving} className="sm:min-w-44">
          {saving ? "Saving…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
