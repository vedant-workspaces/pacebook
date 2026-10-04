import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import type { Option } from "../../types";

const control =
  "block w-full rounded-xl border border-line-strong bg-white px-3.5 text-base text-ink placeholder:text-muted/70 " +
  "focus:border-ember-500 focus:outline-none focus:ring-3 focus:ring-ember-500/20 aria-[invalid=true]:border-missed";

interface FieldProps {
  label: string;
  hint?: ReactNode;
  error?: string | null;
  optional?: boolean;
  children: (id: string, describedBy: string | undefined) => ReactNode;
  className?: string;
}

/** Label + control + hint/error, wired with ids for screen readers. */
export function Field({ label, hint, error, optional, children, className = "" }: FieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [hintId, errId].filter(Boolean).join(" ") || undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 flex items-baseline gap-2 text-sm font-semibold text-ink">
        {label}
        {optional && <span className="text-xs font-normal text-muted">Optional</span>}
      </label>
      {children(id, describedBy)}
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errId} className="mt-1.5 text-sm font-medium text-missed">
          {error}
        </p>
      )}
    </div>
  );
}

export function TextInput({ className = "", ...p }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`${control} h-12 ${className}`} {...p} />;
}

export function TextArea({ className = "", ...p }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={`${control} min-h-24 py-3 ${className}`} {...p} />;
}

export function Select({
  options,
  placeholder,
  className = "",
  ...p
}: SelectHTMLAttributes<HTMLSelectElement> & { options: Option[]; placeholder?: string }) {
  return (
    <select className={`${control} h-12 appearance-none bg-[length:16px] bg-[right_0.9rem_center] bg-no-repeat pr-9 ${className}`}
      style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b7486' stroke-width='2.5'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")" }}
      {...p}
    >
      {placeholder && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

/** Number field with a unit suffix, using a decimal keypad on phones. */
export function UnitInput({
  unit,
  className = "",
  ...p
}: InputHTMLAttributes<HTMLInputElement> & { unit: string }) {
  return (
    <div className={`relative ${className}`}>
      <input className={`${control} num h-12 pr-14 text-xl font-semibold`} inputMode="decimal" autoComplete="off" {...p} />
      <span className="pointer-events-none absolute inset-y-0 right-3.5 flex items-center text-sm font-semibold text-muted">
        {unit}
      </span>
    </div>
  );
}

/** Pace entered as minutes : seconds per km. */
export function PaceInput({
  id,
  minutes,
  seconds,
  onChange,
  describedBy,
  invalid,
}: {
  id: string;
  minutes: string;
  seconds: string;
  onChange: (minutes: string, seconds: string) => void;
  describedBy?: string;
  invalid?: boolean;
}) {
  const cls = `${control.replace("w-full", "")} num h-12 w-16 text-center text-xl font-semibold`;
  return (
    <div className="flex items-center gap-2" role="group" aria-describedby={describedBy}>
      <input
        id={id}
        className={cls}
        inputMode="numeric"
        maxLength={2}
        placeholder="5"
        aria-label="Pace minutes"
        aria-invalid={invalid || undefined}
        value={minutes}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, ""), seconds)}
      />
      <span className="num text-2xl font-bold text-muted" aria-hidden>
        :
      </span>
      <input
        className={cls}
        inputMode="numeric"
        maxLength={2}
        placeholder="30"
        aria-label="Pace seconds"
        aria-invalid={invalid || undefined}
        value={seconds}
        onChange={(e) => onChange(minutes, e.target.value.replace(/\D/g, ""))}
      />
      <span className="text-sm font-semibold text-muted">/km</span>
    </div>
  );
}

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null;
  return (
    <div role="alert" className="rounded-xl border border-missed/20 bg-missed-50 px-4 py-3 text-sm font-medium text-missed">
      {message}
    </div>
  );
}

/** Large tappable chips for choosing one option (e.g. activity type). */
export function ChipGroup({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Option[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <fieldset>
      <legend className="mb-1.5 text-sm font-semibold">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => {
          const active = o.value === value;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(o.value)}
              className={`h-10 rounded-full border px-3.5 text-sm font-semibold transition-colors ${
                active
                  ? "border-ink bg-ink text-white"
                  : "border-line-strong bg-white text-ink-soft hover:border-ink/40"
              }`}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
