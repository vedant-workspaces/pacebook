import type { HTMLAttributes, ReactNode } from "react";

export function Card({ className = "", children, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`rounded-2xl border border-line shadow-[0_1px_2px_rgb(14_23_38/0.04)] ${/\bbg-/.test(className) ? "" : "bg-white"} ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}

export function SectionHeading({
  title,
  action,
  id,
  sub,
}: {
  title: string;
  action?: ReactNode;
  id?: string;
  sub?: ReactNode;
}) {
  return (
    <div className="mb-3 flex items-end justify-between gap-3">
      <div>
        <h2 id={id} className="eyebrow">
          {title}
        </h2>
        {sub && <p className="mt-1 text-sm text-muted">{sub}</p>}
      </div>
      {action}
    </div>
  );
}

/** Big condensed number with a small unit, e.g. 48.5 km. */
export function BigNumber({
  value,
  unit,
  size = "lg",
  className = "",
}: {
  value: ReactNode;
  unit?: string;
  size?: "md" | "lg" | "xl";
  className?: string;
}) {
  const sz = { md: "text-3xl", lg: "text-4xl sm:text-5xl", xl: "text-5xl sm:text-6xl" }[size];
  return (
    <span className={`num font-bold leading-none tracking-tight ${sz} ${className}`}>
      {value}
      {unit && <span className="ml-1 font-sans text-base font-semibold text-muted">{unit}</span>}
    </span>
  );
}

/** Compact label/value pair used inside cards. */
export function Metric({ label, value, icon }: { label: string; value: ReactNode; icon?: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="flex items-center gap-1 text-xs font-medium text-muted">
        {icon}
        {label}
      </div>
      <div className="num mt-0.5 truncate text-xl font-bold">{value}</div>
    </div>
  );
}
