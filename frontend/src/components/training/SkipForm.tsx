import { useState, type FormEvent } from "react";
import { useMeta } from "../../hooks/useMeta";
import { errorMessage } from "../../services/api";
import type { TrainingActivity } from "../../types";
import { Button } from "../ui/Button";
import { Field, FormError, TextArea } from "../ui/Form";

/** "Why didn't you complete this activity?" */
export function SkipForm({
  planned,
  onSubmit,
  onCancel,
}: {
  planned: TrainingActivity;
  onSubmit: (reason: string, notes: string) => Promise<void>;
  onCancel: () => void;
}) {
  const { completionReasons } = useMeta();
  const [reason, setReason] = useState(planned.completionReason ?? "");
  const [notes, setNotes] = useState(planned.completionNotes ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason) {
      setError("Please choose a reason.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await onSubmit(reason, notes.trim());
    } catch (err) {
      setError(errorMessage(err, "Unable to save. Please try again."));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <fieldset>
        <legend className="mb-2 text-sm font-semibold">Reason</legend>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {completionReasons.map((r) => (
            <label
              key={r.value}
              className={`flex h-12 cursor-pointer items-center justify-center rounded-xl border px-3 text-sm font-semibold transition-colors has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ember-500 ${
                reason === r.value ? "border-ink bg-ink text-white" : "border-line-strong bg-white text-ink-soft hover:border-ink/40"
              }`}
            >
              <input
                type="radio"
                name="reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
                className="sr-only"
              />
              {r.label}
            </label>
          ))}
        </div>
      </fieldset>
      <Field label="Additional notes" optional>
        {(id) => (
          <TextArea id={id} placeholder="Legs felt heavy after yesterday's run." value={notes} maxLength={2000}
            onChange={(e) => setNotes(e.target.value)} />
        )}
      </Field>
      <FormError message={error} />
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="ghost" size="lg" onClick={onCancel}>
          Back
        </Button>
        <Button type="submit" variant="dark" size="lg" loading={saving} className="sm:min-w-32">
          {saving ? "Saving…" : "Save"}
        </Button>
      </div>
    </form>
  );
}
