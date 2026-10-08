import type {
  Activity,
  ActivityInput,
  BlockInput,
  BlockStats,
  DaySummary,
  Meta,
  Overview,
  Paged,
  Period,
  PlannedActivityInput,
  ResultInput,
  Series,
  TrainingActivity,
  TrainingBlock,
  User,
  WeekSummary,
} from "../types";
import { todayISO } from "../utils/dates";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public field?: string,
  ) {
    super(message);
  }
}

/** Fired on any 401 so the app can drop back to the login screen. */
export const UNAUTHORIZED_EVENT = "pacelog:unauthorized";

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method,
      credentials: "include",
      headers: {
        Accept: "application/json",
        // Required by the API's CSRF protection on writes.
        "X-Requested-With": "pacelog",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Can't reach Pacelog. Check your connection and try again.");
  }

  if (res.status === 204) return undefined as T;

  let json: any = null;
  try {
    json = await res.json();
  } catch {
    /* non-JSON error page */
  }

  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    const err = json?.error;
    throw new ApiError(
      res.status,
      err?.code ?? "UNKNOWN",
      err?.message && res.status < 500 ? err.message : "Something went wrong. Please try again.",
      err?.field,
    );
  }
  return json as T;
}

const get = <T>(p: string) => request<{ data: T }>("GET", p).then((r) => r.data);
const send = <T>(m: string, p: string, b?: unknown) => request<{ data: T }>(m, p, b ?? {}).then((r) => r?.data);
const qs = (params: Record<string, string | number | undefined | null>) => {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v != null && v !== "") s.set(k, String(v));
  const str = s.toString();
  return str ? `?${str}` : "";
};

export const api = {
  // Auth
  authConfig: () => get<{ googleEnabled: boolean; devLoginEnabled: boolean }>("/auth/config"),
  me: () => get<User>("/auth/me"),
  devLogin: (email: string, name: string) => send<User>("POST", "/auth/dev-login", { email, name }),
  logout: () => send<{ ok: boolean }>("POST", "/auth/logout"),
  meta: () => get<Meta>("/meta"),

  // Training blocks
  listBlocks: (page = 1, limit = 50) =>
    request<Paged<TrainingBlock>>("GET", `/training-blocks${qs({ page, limit, today: todayISO() })}`),
  getBlock: (id: string) => get<TrainingBlock>(`/training-blocks/${id}`),
  createBlock: (b: BlockInput) => send<TrainingBlock>("POST", "/training-blocks", b),
  updateBlock: (id: string, b: BlockInput) => send<TrainingBlock>("PUT", `/training-blocks/${id}`, b),
  deleteBlock: (id: string) => request<void>("DELETE", `/training-blocks/${id}`),
  blockStats: (id: string) => get<BlockStats>(`/training-blocks/${id}/stats${qs({ today: todayISO() })}`),

  // Planned activities
  listPlanned: (blockId: string, from?: string, to?: string) =>
    get<TrainingActivity[]>(`/training-blocks/${blockId}/activities${qs({ from, to })}`),
  createPlanned: (blockId: string, a: PlannedActivityInput) =>
    send<TrainingActivity>("POST", `/training-blocks/${blockId}/activities`, a),
  getPlanned: (id: string) => get<TrainingActivity>(`/training-activities/${id}`),
  updatePlanned: (id: string, a: PlannedActivityInput) => send<TrainingActivity>("PUT", `/training-activities/${id}`, a),
  deletePlanned: (id: string) => request<void>("DELETE", `/training-activities/${id}`),
  complete: (id: string, r: ResultInput) => send<TrainingActivity>("POST", `/training-activities/${id}/complete`, r),
  modify: (id: string, r: ResultInput) => send<TrainingActivity>("POST", `/training-activities/${id}/modify`, r),
  skip: (id: string, reason: string, notes: string) =>
    send<TrainingActivity>("POST", `/training-activities/${id}/skip`, { reason, notes }),
  reset: (id: string) => send<TrainingActivity>("POST", `/training-activities/${id}/reset`),

  // Actual activities
  listActivities: (params: { page?: number; limit?: number; from?: string; to?: string; type?: string }) =>
    request<Paged<Activity>>("GET", `/activities${qs(params)}`),
  getActivity: (id: string) => get<Activity>(`/activities/${id}`),
  createActivity: (a: ActivityInput) => send<Activity>("POST", "/activities", a),
  updateActivity: (id: string, a: ActivityInput) => send<Activity>("PUT", `/activities/${id}`, a),
  deleteActivity: (id: string) => request<void>("DELETE", `/activities/${id}`),

  // Statistics (always relative to the runner's local "today")
  overview: (period: Period = "all") => get<Overview>(`/stats/overview${qs({ period, today: todayISO() })}`),
  series: (kind: "weekly" | "monthly" | "yearly", date = todayISO()) => get<Series>(`/stats/${kind}${qs({ date })}`),
  day: (date: string) => get<DaySummary>(`/stats/day${qs({ date })}`),
  week: (date: string) => get<WeekSummary>(`/stats/week${qs({ date, today: todayISO() })}`),
};

export function errorMessage(err: unknown, fallback = "Something went wrong. Please try again."): string {
  if (err instanceof ApiError) return err.message;
  return fallback;
}
