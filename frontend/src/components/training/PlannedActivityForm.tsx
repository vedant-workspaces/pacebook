import { useState, type FormEvent } from "react";
import { useMeta } from "../../hooks/useMeta";
import { ApiError, errorMessage } from "../../services/api";
import type { PlannedActivityInput, TrainingActivity, TrainingBlock } from "../../types";
import { parseNumber, parsePace, splitPace } from "../../utils/format";
import { Button } from "../ui/Button";
import { Field, FormError, PaceInput, Select, TextArea, TextInput, UnitInput } from "../ui/Form";

interface Props {
  block: Pick<TrainingBlock, "startDate" | "endDate">;
  date?: string;
  initial?: TrainingActivity;
  onSubmit: (values: PlannedActivityInput) => Promise<void>;
  onCancel: () => void;
}

/** Create or edit a planned training activity. Only date and type are required. */
export function PlannedActivityForm({ block, date, initial, onSubmit, onCancel }: Props) {
  const meta = useMeta();
  const [activityDate, setDate] = useState(initial?.activityDate ?? date ?? block.startDate);
  const [type, setType] = useState(initial?.activityType ?? "easy_run");
  const [title, setTitle] = useState(initial?.title ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [distance, setDistance] = useState(initial?.plannedDistanceKm != null ? String(initial.plannedDistanceKm) : "");
  const [[pm, ps], setPace] = useState<[string, string]>(splitPace(initial?.plannedPaceSecondsPerKm));
  const [duration, setDuration] = useState(
    initial?.plannedDurationSeconds != null ? String(Math.round(initial.plannedDurationSeconds / 60)) : "",
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const isRest = type === "rest";

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    const e: Record<string, string> = {};
    const km = parseNumber(distance);
    const pace = parsePace(pm, ps);
    const mins = parseNumber(duration);
    if (!activityDate) e.date = "Choose a date.";
    else if (activityDate < block.startDate || activityDate > block.endDate)
      e.date = "Date must be within the training block.";
    if (km != null && (Number.isNaN(km) || km < 0 || km >= 500)) e.distance = "Enter a distance between 0 and 500 km.";
    if (pace != null && (Number.isNaN(pace) || pace < 120 || pace > 1800)) e.pace = "Enter a pace between 2:00 and 30:00 per km.";
    if (mins != null && (Number.isNaN(mins) || mins <= 0 || mins > 2880)) e.duration = "Enter a duration in minutes.";
    setErrors(e);
    if (Object.keys(e).length) return;

    setFormError(null);
    setSaving(true);
    try {
      await onSubmit({
        activityDate,
        activityType: type,
        title: title.trim(),
        description: description.trim() || null,
        plannedDistanceKm: isRest ? null : km,
        plannedPaceSecondsPerKm: isRest ? null : pace,
        plannedDurationSeconds: isRest || mins == null ? null : Math.round(mins * 60),
      });
    } catch (err) {
      if (err instanceof ApiError && err.field === "activityDate") setErrors({ date: err.message });
      setFormError(errorMessage(err, "Unable to save activity. Please try again."));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Date" error={errors.date}>
          {(id, d) => (
            <TextInput id={id} type="date" min={block.startDate} max={block.endDate} value={activityDate}
              aria-describedby={d} aria-invalid={!!errors.date} onChange={(e) => setDate(e.target.value)} />
          )}
        </Field>
        <Field label="Activity type">
          {(id) => <Select id={id} options={meta.activityTypes} value={type} onChange={(e) => setType(e.target.value)} />}
        </Field>
      </div>
      <Field label="Title" optional hint={`Defaults to “${meta.typeLabel(type)}”`}>
        {(id, d) => (
          <TextInput id={id} placeholder="Easy Aerobic Run" value={title} maxLength={120} aria-describedby={d}
            onChange={(e) => setTitle(e.target.value)} />
        )}
      </Field>
      <Field label="Description" optional>
        {(id) => (
          <TextArea id={id} placeholder="6 × 1 km @ 4:30/km, 90 s jog recovery" value={description} maxLength={2000}
            onChange={(e) => setDescription(e.target.value)} />
        )}
      </Field>
      {!isRest && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Planned distance" optional error={errors.distance}>
            {(id, d) => (
              <UnitInput id={id} unit="km" placeholder="8" value={distance} aria-describedby={d}
                aria-invalid={!!errors.distance} onChange={(e) => setDistance(e.target.value)} />
            )}
          </Field>
          <Field label="Planned pace" optional error={errors.pace} className="sm:col-span-1">
            {(id, d) => (
              <PaceInput id={id} minutes={pm} seconds={ps} describedBy={d} invalid={!!errors.pace}
                onChange={(m, s) => setPace([m, s])} />
            )}
          </Field>
          <Field label="Planned duration" optional error={errors.duration}>
            {(id, d) => (
              <UnitInput id={id} unit="min" inputMode="numeric" placeholder="45" value={duration} aria-describedby={d}
                aria-invalid={!!errors.duration} onChange={(e) => setDuration(e.target.value)} />
            )}
          </Field>
        </div>
      )}
      <FormError message={formError} />
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="ghost" size="lg" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" size="lg" loading={saving} className="sm:min-w-40">
          {saving ? "Saving…" : "Save Activity"}
        </Button>
      </div>
    </form>
  );
}
