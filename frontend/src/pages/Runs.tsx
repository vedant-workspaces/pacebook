import { Link, useSearchParams } from "react-router-dom";
import { TypeBadge } from "../components/activities/ActivityCards";
import { PageHeader } from "../components/layout/AppLayout";
import { Button, ButtonLink } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { EmptyState, ErrorState, Skeleton } from "../components/ui/Feedback";
import { Select } from "../components/ui/Form";
import { ChevronLeft, ChevronRight, LinkIcon, PlusIcon } from "../components/ui/Icons";
import { useAsync } from "../hooks/useAsync";
import { useMeta } from "../hooks/useMeta";
import { api } from "../services/api";
import { shortDate, weekdayShort } from "../utils/dates";
import { formatKm, formatPace } from "../utils/format";

const PAGE_SIZE = 25;

/** /runs — complete history of actual activities, newest first. */
export default function Runs() {
  const meta = useMeta();
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number(params.get("page")) || 1);
  const type = params.get("type") ?? "";
  const { data, error, loading, reload } = useAsync(
    () => api.listActivities({ page, limit: PAGE_SIZE, type: type || undefined }),
    [page, type],
  );

  const total = data?.pagination.total ?? 0;
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const set = (next: Record<string, string>) => setParams({ ...Object.fromEntries(params), ...next });

  return (
    <div>
      <PageHeader
        title="Runs"
        sub={data ? `${total.toLocaleString()} ${total === 1 ? "activity" : "activities"} logged` : "Your running history"}
        actions={
          <ButtonLink to="/log" icon={<PlusIcon />}>
            Log Activity
          </ButtonLink>
        }
      />
      <div className="mb-4 max-w-xs">
        <label htmlFor="type-filter" className="sr-only">
          Filter by activity type
        </label>
        <Select id="type-filter" options={meta.activityTypes} placeholder="All activity types" value={type}
          onChange={(e) => set({ type: e.target.value, page: "1" })} />
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !data ? (
        <div role="status" className="space-y-2">
          <span className="sr-only">Loading runs…</span>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : data.data.length === 0 ? (
        type ? (
          <EmptyState compact title="No activities of this type yet." />
        ) : (
          <EmptyState
            title="No runs yet."
            message="Start building your running history."
            action={
              <ButtonLink to="/log" icon={<PlusIcon />}>
                Log Your First Activity
              </ButtonLink>
            }
          />
        )
      ) : (
        <Card className={`overflow-hidden ${loading ? "opacity-70" : ""}`}>
          {/* Desktop: table */}
          <table className="hidden w-full text-left md:table">
            <thead className="border-b border-line bg-paper/60 text-xs uppercase tracking-wider text-muted">
              <tr>
                <th scope="col" className="px-5 py-3 font-bold">Date</th>
                <th scope="col" className="px-5 py-3 font-bold">Activity</th>
                <th scope="col" className="px-5 py-3 text-right font-bold">Distance</th>
                <th scope="col" className="px-5 py-3 text-right font-bold">Pace</th>
                <th scope="col" className="px-5 py-3 text-right font-bold">HR</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.data.map((a) => (
                <tr key={a.id} className="group relative hover:bg-paper/70">
                  <td className="whitespace-nowrap px-5 py-3 text-sm">
                    <span className="font-semibold">{shortDate(a.activityDate)}</span>{" "}
                    <span className="text-muted">{weekdayShort(a.activityDate)}</span>
                  </td>
                  <td className="px-5 py-3">
                    <Link to={`/runs/${a.id}`} className="flex items-center gap-3 font-semibold after:absolute after:inset-0 focus-visible:outline-none group-focus-within:underline">
                      <TypeBadge type={a.activityType} tone="muted" />
                      <span className="min-w-0">
                        <span className="flex items-center gap-1.5">
                          {a.title}
                          {a.trainingActivityId && <LinkIcon width={14} height={14} className="text-muted" aria-label="Planned" role="img" />}
                        </span>
                        <span className="block text-xs font-normal text-muted">{meta.typeLabel(a.activityType)}</span>
                      </span>
                    </Link>
                  </td>
                  <td className="num px-5 py-3 text-right text-lg font-bold">{a.distanceKm != null ? `${formatKm(a.distanceKm, 2)} km` : "—"}</td>
                  <td className="num px-5 py-3 text-right text-lg">{formatPace(a.paceSecondsPerKm)}</td>
                  <td className="num px-5 py-3 text-right text-lg">{a.averageHeartRate ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Mobile: cards */}
          <ul className="divide-y divide-line md:hidden">
            {data.data.map((a) => (
              <li key={a.id}>
                <Link to={`/runs/${a.id}`} className="flex items-center gap-3 px-4 py-3 active:bg-paper">
                  <div className="w-11 shrink-0 text-center">
                    <div className="text-[0.65rem] font-bold uppercase text-muted">{weekdayShort(a.activityDate)}</div>
                    <div className="num text-lg font-bold leading-none">{shortDate(a.activityDate).split(" ")[1]}</div>
                    <div className="text-[0.65rem] font-semibold uppercase text-muted">{shortDate(a.activityDate).split(" ")[0]}</div>
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">{a.title}</p>
                    <p className="num text-sm text-ink-soft">
                      {formatPace(a.paceSecondsPerKm)}
                      {a.averageHeartRate ? ` · ${a.averageHeartRate} bpm` : ""}
                    </p>
                  </div>
                  <div className="num text-right text-2xl font-bold">
                    {formatKm(a.distanceKm)}
                    <span className="ml-0.5 font-sans text-xs font-semibold text-muted">km</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {pages > 1 && (
        <nav aria-label="Pagination" className="mt-4 flex items-center justify-between">
          <Button variant="secondary" size="sm" disabled={page <= 1} onClick={() => set({ page: String(page - 1) })} icon={<ChevronLeft width={16} height={16} />}>
            Newer
          </Button>
          <span className="text-sm text-muted">
            Page {page} of {pages}
          </span>
          <Button variant="secondary" size="sm" disabled={page >= pages} onClick={() => set({ page: String(page + 1) })}>
            Older <ChevronRight width={16} height={16} />
          </Button>
        </nav>
      )}
    </div>
  );
}
