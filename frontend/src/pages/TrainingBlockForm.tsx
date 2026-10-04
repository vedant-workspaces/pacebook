import { useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { PageHeader } from "../components/layout/AppLayout";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { ErrorState, LoadingBlock } from "../components/ui/Feedback";
import { Field, FormError, TextArea, TextInput } from "../components/ui/Form";
import { useAsync } from "../hooks/useAsync";
import { api, ApiError, errorMessage } from "../services/api";
import type { TrainingBlock } from "../types";
import { addDays, daysBetween, todayISO, weekStart } from "../utils/dates";

/** /training/new and /training/:id/edit */
export default function TrainingBlockFormPage() {
  const { id } = useParams();
  const existing = useAsync(() => (id ? api.getBlock(id) : Promise.resolve(undefined)), [id]);

  if (id && existing.error) return <ErrorState message={existing.error} onRetry={existing.reload} />;
  if (id && !existing.data) return <LoadingBlock />;
  return <BlockForm initial={existing.data} />;
}

function BlockForm({ initial }: { initial?: TrainingBlock }) {
  const navigate = useNavigate();
  const nextMonday = addDays(weekStart(todayISO()), 7);
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [startDate, setStart] = useState(initial?.startDate ?? nextMonday);
  const [endDate, setEnd] = useState(initial?.endDate ?? addDays(nextMonday, 16 * 7 - 1));
  const [goal, setGoal] = useState(initial?.goal ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const weeks = startDate && endDate && endDate >= startDate ? Math.ceil((daysBetween(startDate, endDate) + 1) / 7) : null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = "Give your training block a name.";
    if (!startDate) errs.startDate = "Choose a start date.";
    if (!endDate) errs.endDate = "Choose an end date.";
    else if (startDate && endDate < startDate) errs.endDate = "End date must be on or after the start date.";
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setSaving(true);
    setFormError(null);
    const body = { name: name.trim(), description: description.trim() || null, startDate, endDate, goal: goal.trim() || null };
    try {
      const saved = initial ? await api.updateBlock(initial.id, body) : await api.createBlock(body);
      navigate(`/training/${saved.id}`);
    } catch (err) {
      if (err instanceof ApiError && err.field) setErrors({ [err.field]: err.message });
      setFormError(errorMessage(err, "Unable to save training block. Please try again."));
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        eyebrow="Training"
        title={initial ? "Edit Training Block" : "New Training Block"}
        sub="A block is a stretch of structured training toward a goal."
      />
      <Card className="p-5 sm:p-7">
        <form onSubmit={submit} noValidate className="space-y-5">
          <Field label="Training block name" error={errors.name}>
            {(id, d) => (
              <TextInput id={id} placeholder="Marathon Training — TMM 2027" value={name} maxLength={120} autoFocus
                aria-describedby={d} aria-invalid={!!errors.name} onChange={(e) => setName(e.target.value)} />
            )}
          </Field>
          <Field label="Goal" optional hint="e.g. Sub 4:00 Marathon, Build aerobic base, Prepare for 50K ultra">
            {(id, d) => (
              <TextInput id={id} placeholder="Sub 4:00" value={goal} maxLength={200} aria-describedby={d}
                onChange={(e) => setGoal(e.target.value)} />
            )}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Start date" error={errors.startDate}>
              {(id, d) => (
                <TextInput id={id} type="date" value={startDate} aria-describedby={d} aria-invalid={!!errors.startDate}
                  onChange={(e) => setStart(e.target.value)} />
              )}
            </Field>
            <Field label="End date" error={errors.endDate} hint={weeks ? `${weeks} weeks` : undefined}>
              {(id, d) => (
                <TextInput id={id} type="date" value={endDate} min={startDate} aria-describedby={d}
                  aria-invalid={!!errors.endDate} onChange={(e) => setEnd(e.target.value)} />
              )}
            </Field>
          </div>
          <Field label="Description" optional>
            {(id) => (
              <TextArea id={id} placeholder="Focus, key races, notes for yourself…" value={description} maxLength={2000}
                onChange={(e) => setDescription(e.target.value)} />
            )}
          </Field>
          <FormError message={formError} />
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="ghost" size="lg" onClick={() => navigate(-1)}>
              Cancel
            </Button>
            <Button type="submit" size="lg" loading={saving} className="sm:min-w-52">
              {saving ? "Saving…" : initial ? "Save Changes" : "Create Training Block"}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
