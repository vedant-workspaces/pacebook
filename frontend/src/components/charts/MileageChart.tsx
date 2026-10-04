import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from "recharts";
import type { SeriesPoint } from "../../types";
import { formatKm } from "../../utils/format";

// Validated pair (light surface): actual = ember, planned = steel blue.
export const ACTUAL_COLOR = "#f2591d";
export const PLANNED_COLOR = "#4e78b5";
const GRID = "#ece9e2";
const AXIS = "#6b7486";

const axisProps = {
  tick: { fill: AXIS, fontSize: 12 },
  tickLine: false,
  axisLine: false,
} as const;

export interface ChartRow {
  label: string;
  /** Long label for the tooltip, e.g. "Mon, Oct 5". */
  full: string;
  actual: number;
  planned?: number;
}

function ChartTooltip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload as ChartRow;
  return (
    <div className="rounded-xl border border-line bg-white px-3 py-2 text-sm shadow-lg">
      <p className="font-semibold">{row.full}</p>
      <p className="flex items-center gap-2 text-ink-soft">
        <span className="h-2.5 w-2.5 rounded-sm" style={{ background: ACTUAL_COLOR }} aria-hidden />
        Actual <span className="num ml-auto pl-3 font-bold text-ink">{formatKm(row.actual)} km</span>
      </p>
      {row.planned != null && (
        <p className="flex items-center gap-2 text-ink-soft">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: PLANNED_COLOR }} aria-hidden />
          Planned <span className="num ml-auto pl-3 font-bold text-ink">{formatKm(row.planned)} km</span>
        </p>
      )}
    </div>
  );
}

export function Legend({ planned = true }: { planned?: boolean }) {
  return (
    <div className="flex items-center gap-4 text-xs font-semibold text-ink-soft">
      <span className="flex items-center gap-1.5">
        <span className="h-2.5 w-2.5 rounded-sm" style={{ background: ACTUAL_COLOR }} aria-hidden /> Actual
      </span>
      {planned && (
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: PLANNED_COLOR }} aria-hidden /> Planned
        </span>
      )}
    </div>
  );
}

/**
 * Actual mileage as columns, paired with planned mileage columns when
 * there is any plan in range. Wrapped in a <figure> with a hidden data table.
 */
export function MileageChart({ rows, height = 220, caption }: { rows: ChartRow[]; height?: number; caption: string }) {
  const hasPlan = rows.some((r) => (r.planned ?? 0) > 0);
  const many = rows.length > 14;
  return (
    <figure>
      <div style={{ height }} aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: -18 }} barGap={2} barCategoryGap={many ? "18%" : "28%"}>
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="label" {...axisProps} interval={many ? "preserveStartEnd" : 0} minTickGap={8} />
            <YAxis {...axisProps} width={44} allowDecimals={false} />
            <Tooltip content={<ChartTooltip />} cursor={{ fill: "rgb(14 23 38 / 0.04)" }} />
            {hasPlan && (
              <Bar isAnimationActive={false} dataKey="planned" name="Planned" fill={PLANNED_COLOR} radius={[4, 4, 0, 0]} maxBarSize={many ? 8 : 18} />
            )}
            <Bar isAnimationActive={false} dataKey="actual" name="Actual" fill={ACTUAL_COLOR} radius={[4, 4, 0, 0]} maxBarSize={many ? 8 : 18} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">{caption}</figcaption>
      <DataTable rows={rows} caption={caption} />
    </figure>
  );
}

/** Grouped planned vs actual columns, e.g. per training week. */
export function PlannedActualBars({ rows, height = 240, caption }: { rows: ChartRow[]; height?: number; caption: string }) {
  return (
    <figure>
      <div style={{ height }} aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: -18 }} barGap={2} barCategoryGap="22%">
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="label" {...axisProps} interval="preserveStartEnd" minTickGap={6} />
            <YAxis {...axisProps} width={44} allowDecimals={false} />
            <Tooltip content={<ChartTooltip />} cursor={{ fill: "rgb(14 23 38 / 0.04)" }} />
            <Bar isAnimationActive={false} dataKey="planned" name="Planned" fill={PLANNED_COLOR} radius={[4, 4, 0, 0]} maxBarSize={18} />
            <Bar isAnimationActive={false} dataKey="actual" name="Actual" fill={ACTUAL_COLOR} radius={[4, 4, 0, 0]} maxBarSize={18} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">{caption}</figcaption>
      <DataTable rows={rows} caption={caption} />
    </figure>
  );
}

function DataTable({ rows, caption }: { rows: ChartRow[]; caption: string }) {
  return (
    <table className="sr-only">
      <caption>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Period</th>
          <th scope="col">Actual km</th>
          <th scope="col">Planned km</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.full}>
            <th scope="row">{r.full}</th>
            <td>{formatKm(r.actual)}</td>
            <td>{r.planned != null ? formatKm(r.planned) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function seriesToRows(points: SeriesPoint[], label: (iso: string) => string, full: (iso: string) => string): ChartRow[] {
  return points.map((p) => ({
    label: label(p.date),
    full: full(p.date),
    actual: Math.round(p.distanceKm * 10) / 10,
    planned: Math.round(p.plannedDistanceKm * 10) / 10,
  }));
}
