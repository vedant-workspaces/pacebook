import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ActivityForm } from "../components/activities/ActivityForm";
import { PageHeader } from "../components/layout/AppLayout";
import { Card } from "../components/ui/Card";
import { ErrorState, LoadingBlock } from "../components/ui/Feedback";
import { LinkIcon } from "../components/ui/Icons";
import { useAsync } from "../hooks/useAsync";
import { api } from "../services/api";
import { longDate, todayISO } from "../utils/dates";

/** /log — log an unplanned activity (the fastest path after a run). */
export default function LogActivity() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const date = params.get("date") ?? todayISO();

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader eyebrow="Log Activity" title="What did you do?" sub="Distance, pace, heart rate — done in under a minute." />
      <Card className="p-5 sm:p-7">
        <ActivityForm
          mode="log"
          initial={{ activityDate: date }}
          onCancel={() => navigate(-1)}
          onSubmit={async (v) => {
            const a = await api.createActivity({ ...v, trainingActivityId: null });
            navigate(`/runs/${a.id}`, { replace: true });
          }}
        />
      </Card>
    </div>
  );
}

/** /runs/:id/edit — edit an actual activity without detaching it from its plan. */
export function EditActivity() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const activity = useAsync(() => api.getActivity(id), [id]);

  if (activity.error) return <ErrorState message={activity.error} onRetry={activity.reload} />;
  if (!activity.data) return <LoadingBlock />;
  const a = activity.data;

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader eyebrow="Edit Activity" title={a.title} sub={longDate(a.activityDate)} />
      {a.plannedActivity && (
        <div className="mb-4 flex items-center gap-2 rounded-2xl border border-line bg-white px-4 py-3 text-sm">
          <LinkIcon width={18} height={18} className="text-ember-600" />
          <span>
            <span className="text-muted">Linked to:</span>{" "}
            <span className="font-semibold">
              {new Date(a.plannedActivity.activityDate + "T00:00").toLocaleDateString("en-US", { weekday: "long" })}{" "}
              {a.plannedActivity.title}
            </span>
            {a.plannedActivity.trainingBlockName && <span className="text-muted"> · {a.plannedActivity.trainingBlockName}</span>}
          </span>
        </div>
      )}
      <Card className="p-5 sm:p-7">
        <ActivityForm
          mode="edit"
          initial={a}
          submitLabel="Save Changes"
          onCancel={() => navigate(-1)}
          onSubmit={async (v) => {
            await api.updateActivity(a.id, v);
            navigate(`/runs/${a.id}`, { replace: true });
          }}
        />
      </Card>
    </div>
  );
}
