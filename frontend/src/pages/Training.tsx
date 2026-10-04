import { Link } from "react-router-dom";
import { PageHeader } from "../components/layout/AppLayout";
import { ButtonLink } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { EmptyState, ErrorState, LoadingBlock } from "../components/ui/Feedback";
import { CalendarIcon, PlusIcon, TrophyIcon } from "../components/ui/Icons";
import { useAsync } from "../hooks/useAsync";
import { api } from "../services/api";
import type { TrainingBlock } from "../types";
import { dateRange, daysBetween, todayISO } from "../utils/dates";
import { formatKm, formatPct } from "../utils/format";

export default function Training() {
  const { data, error, loading, reload } = useAsync(() => api.listBlocks(), []);
  const today = todayISO();

  const blocks = data?.data ?? [];
  const active = blocks.filter((b) => b.startDate <= today && b.endDate >= today);
  const upcoming = blocks.filter((b) => b.startDate > today).reverse();
  const past = blocks.filter((b) => b.endDate < today);

  return (
    <div>
      <PageHeader
        title="Training"
        sub="Your training blocks — the plan behind the miles."
        actions={
          <ButtonLink to="/training/new" icon={<PlusIcon />}>
            Create Training Block
          </ButtonLink>
        }
      />
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading && !data ? (
        <LoadingBlock label="Loading training blocks" />
      ) : blocks.length === 0 ? (
        <EmptyState
          title="No training blocks yet."
          message="Create a training block to start planning your training."
          action={
            <ButtonLink to="/training/new" icon={<PlusIcon />}>
              Create Training Block
            </ButtonLink>
          }
        />
      ) : (
        <div className="space-y-8">
          <BlockSection title="Active" blocks={active} featured />
          <BlockSection title="Upcoming" blocks={upcoming} />
          <BlockSection title="Recent blocks" blocks={past} />
        </div>
      )}
    </div>
  );
}

function BlockSection({ title, blocks, featured }: { title: string; blocks: TrainingBlock[]; featured?: boolean }) {
  if (blocks.length === 0) return null;
  return (
    <section>
      <h2 className="eyebrow mb-3">{title}</h2>
      <div className={`grid gap-4 ${featured ? "md:grid-cols-1" : "sm:grid-cols-2 lg:grid-cols-3"}`}>
        {blocks.map((b) => (
          <BlockCard key={b.id} block={b} featured={featured} />
        ))}
      </div>
    </section>
  );
}

function BlockCard({ block, featured }: { block: TrainingBlock; featured?: boolean }) {
  const s = block.summary;
  const today = todayISO();
  const weeks = Math.ceil((daysBetween(block.startDate, block.endDate) + 1) / 7);
  const progress = Math.min(1, Math.max(0, (daysBetween(block.startDate, today) + 1) / (daysBetween(block.startDate, block.endDate) + 1)));
  const weekNo = Math.min(weeks, Math.floor(daysBetween(block.startDate, today) / 7) + 1);
  const isPast = block.endDate < today;
  const isActive = !isPast && block.startDate <= today;

  return (
    <Link to={`/training/${block.id}`} className="group block rounded-2xl focus-visible:outline-none">
      <Card className={`relative h-full overflow-hidden p-5 transition group-hover:border-line-strong group-hover:shadow-md group-focus-visible:ring-3 group-focus-visible:ring-ember-500 ${featured ? "sm:p-6" : ""}`}>
        {featured && (
          <svg className="absolute -right-6 -top-6 h-40 w-40 text-ember-500/10" viewBox="0 0 100 100" aria-hidden>
            <path d="M5 80 L25 55 L40 65 L60 30 L80 45 L95 20" fill="none" stroke="currentColor" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
        <div className="relative flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 text-sm text-muted">
              <CalendarIcon width={16} height={16} /> {dateRange(block.startDate, block.endDate)}
              {isPast && <span className="ml-1 rounded-full bg-paper px-2 py-0.5 text-xs font-semibold">Finished</span>}
            </p>
            <h3 className={`mt-1 font-display font-bold leading-tight ${featured ? "text-3xl" : "text-2xl"}`}>{block.name}</h3>
            {block.goal && (
              <p className="mt-1 flex items-center gap-1.5 text-sm font-semibold text-ember-600">
                <TrophyIcon width={16} height={16} /> Goal: {block.goal}
              </p>
            )}
          </div>
          {s && (
            <div className="text-right">
              <p className="num text-4xl font-bold leading-none">{formatPct(s.completionPct)}</p>
              <p className="text-xs font-medium text-muted">completion</p>
            </div>
          )}
        </div>

        {s && (
          <dl className="relative mt-5 grid grid-cols-3 gap-3 border-t border-line pt-4">
            <div>
              <dt className="text-xs text-muted">Length</dt>
              <dd className="num text-xl font-bold">{weeks} weeks</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Planned sessions</dt>
              <dd className="num text-xl font-bold">{s.sessionCount}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Actual / planned</dt>
              <dd className="num text-xl font-bold">
                {formatKm(s.actualDistanceKm, 0)} / {formatKm(s.plannedDistanceKm, 0)} km
              </dd>
            </div>
          </dl>
        )}

        {isActive && (
          <div className="relative mt-4">
            <div className="mb-1 flex justify-between text-xs font-semibold text-muted">
              <span>Week {weekNo} of {weeks}</span>
              <span>{Math.round(progress * 100)}% through the block</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-paper" aria-hidden>
              <div className="h-full rounded-full bg-ember-500" style={{ width: `${progress * 100}%` }} />
            </div>
          </div>
        )}
        <span className="relative mt-4 inline-block text-sm font-semibold text-ember-600 group-hover:underline">Open Block →</span>
      </Card>
    </Link>
  );
}
