import type { ReactNode } from "react";
import { Button } from "./Button";

export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`skeleton ${className}`} aria-hidden />;
}

export function LoadingBlock({ label = "Loading", className = "" }: { label?: string; className?: string }) {
  return (
    <div role="status" aria-live="polite" className={`space-y-3 ${className}`}>
      <span className="sr-only">{label}…</span>
      <Skeleton className="h-28" />
      <Skeleton className="h-20" />
      <Skeleton className="h-20" />
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-missed/20 bg-missed-50 px-5 py-6 text-center">
      <p className="font-semibold text-missed">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-3" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

/** Friendly empty state with a dotted route line. */
export function EmptyState({
  title,
  message,
  action,
  compact = false,
}: {
  title: string;
  message?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div
      className={`relative overflow-hidden rounded-2xl border border-dashed border-line-strong bg-white/60 text-center ${
        compact ? "px-4 py-6" : "px-6 py-12"
      }`}
    >
      {!compact && (
        <svg className="mx-auto mb-4 text-ember-500" width="120" height="40" viewBox="0 0 120 40" aria-hidden>
          <path
            d="M4 32 C 24 32, 26 10, 46 12 S 70 34, 88 24 S 108 6, 116 8"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            strokeDasharray="2 7"
            strokeLinecap="round"
          />
          <circle cx="4" cy="32" r="4" fill="currentColor" />
          <circle cx="116" cy="8" r="4" fill="none" stroke="currentColor" strokeWidth="3" />
        </svg>
      )}
      <p className={`font-display font-bold ${compact ? "text-lg" : "text-2xl"}`}>{title}</p>
      {message && <p className="mx-auto mt-1 max-w-sm text-sm text-muted">{message}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

/** Subtle elevation-profile decoration for hero cards. */
export function ElevationLine({ className = "" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 400 80" preserveAspectRatio="none" aria-hidden>
      <path
        d="M0 70 L30 62 L55 66 L80 48 L110 52 L140 30 L165 38 L195 22 L220 34 L250 18 L285 40 L310 28 L340 46 L370 36 L400 44 L400 80 L0 80 Z"
        fill="currentColor"
        opacity="0.12"
      />
      <path
        d="M0 70 L30 62 L55 66 L80 48 L110 52 L140 30 L165 38 L195 22 L220 34 L250 18 L285 40 L310 28 L340 46 L370 36 L400 44"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        opacity="0.5"
      />
    </svg>
  );
}
