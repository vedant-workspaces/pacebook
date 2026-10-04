import type { ActivityStatus } from "../../types";
import { CheckIcon, CircleIcon, TildeIcon, XIcon } from "./Icons";

// Status is always shown as icon + word, never colour alone.
export const STATUS: Record<
  ActivityStatus,
  { label: string; symbol: string; text: string; bg: string; ring: string; Icon: typeof CheckIcon }
> = {
  completed: { label: "Completed", symbol: "✓", text: "text-done", bg: "bg-done-50", ring: "ring-done/25", Icon: CheckIcon },
  modified: { label: "Modified", symbol: "~", text: "text-changed", bg: "bg-changed-50", ring: "ring-changed/25", Icon: TildeIcon },
  skipped: { label: "Skipped", symbol: "✕", text: "text-missed", bg: "bg-missed-50", ring: "ring-missed/25", Icon: XIcon },
  planned: { label: "Planned", symbol: "○", text: "text-planned", bg: "bg-planned-50", ring: "ring-planned/20", Icon: CircleIcon },
};

export function StatusBadge({ status, compact = false }: { status: ActivityStatus; compact?: boolean }) {
  const s = STATUS[status];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full ${s.bg} ${s.text} ${
        compact ? "px-1.5 py-0.5 text-[0.65rem]" : "px-2.5 py-1 text-xs"
      } font-bold uppercase tracking-wider`}
    >
      <s.Icon width={compact ? 12 : 14} height={compact ? 12 : 14} strokeWidth={3} />
      {s.label}
    </span>
  );
}

/** Round status marker with an accessible label. */
export function StatusDot({ status, size = 22 }: { status: ActivityStatus; size?: number }) {
  const s = STATUS[status];
  return (
    <span
      className={`inline-grid shrink-0 place-items-center rounded-full ${s.bg} ${s.text} ring-1 ${s.ring}`}
      style={{ width: size, height: size }}
      role="img"
      aria-label={s.label}
      title={s.label}
    >
      <s.Icon width={size * 0.62} height={size * 0.62} strokeWidth={3} />
    </span>
  );
}
