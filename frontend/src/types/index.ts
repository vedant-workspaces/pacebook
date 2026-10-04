export type ActivityStatus = "planned" | "completed" | "skipped" | "modified";

export interface User {
  id: string;
  email: string;
  name: string;
  profilePicture?: string;
}

export interface Option {
  value: string;
  label: string;
  isRun?: boolean;
}

export interface Meta {
  activityTypes: Option[];
  completionReasons: Option[];
}

export interface PlanSummary {
  totalActivities: number;
  sessionCount: number;
  planned: number;
  completed: number;
  modified: number;
  skipped: number;
  due: number;
  completionPct: number | null;
  plannedDistanceKm: number;
  actualDistanceKm: number;
}

export interface TrainingBlock {
  id: string;
  name: string;
  description?: string;
  startDate: string;
  endDate: string;
  goal?: string;
  summary?: PlanSummary;
}

export interface TrainingActivity {
  id: string;
  trainingBlockId: string;
  trainingBlockName?: string;
  activityDate: string;
  activityType: string;
  title: string;
  description?: string;
  plannedDistanceKm?: number;
  plannedPaceSecondsPerKm?: number;
  plannedDurationSeconds?: number;
  status: ActivityStatus;
  completionReason?: string;
  completionNotes?: string;
  actualActivity?: Activity;
}

export interface Activity {
  id: string;
  trainingActivityId?: string;
  activityDate: string;
  activityType: string;
  title: string;
  distanceKm?: number;
  paceSecondsPerKm?: number;
  averageHeartRate?: number;
  comment?: string;
  source: string;
  createdAt: string;
  plannedActivity?: TrainingActivity;
}

export interface Pagination {
  page: number;
  limit: number;
  total: number;
}

export interface Paged<T> {
  data: T[];
  pagination: Pagination;
}

export interface Totals {
  distanceKm: number;
  activityCount: number;
}

export interface PlannedVsActual {
  plannedDistanceKm: number;
  actualDistanceKm: number;
  differenceKm: number;
  completionPct: number | null;
}

export type Period = "week" | "month" | "year" | "all";

export interface Overview {
  today: string;
  period: Period;
  totals: { week: Totals; month: Totals; year: Totals; allTime: Totals };
  aggregates: {
    distanceKm: number;
    activityCount: number;
    averageDistanceKm: number | null;
    averagePaceSecondsPerKm: number | null;
    averageHeartRate: number | null;
    totalTimeSeconds: number;
  };
  longestActivity: Activity | null;
  fastestActivity: Activity | null;
  training: PlanSummary;
  plannedVsActual: PlannedVsActual;
}

export interface SeriesPoint {
  date: string;
  distanceKm: number;
  activityCount: number;
  plannedDistanceKm: number;
}

export interface Series {
  range: { from: string; to: string };
  bucket: "day" | "month";
  points: SeriesPoint[];
  totalDistanceKm: number;
  activityCount: number;
  plannedDistanceKm: number;
}

export interface DaySummary {
  date: string;
  planned: TrainingActivity[];
  actual: Activity[];
  distanceKm: number;
  plannedDistanceKm: number;
}

export interface WeekSummary {
  range: { from: string; to: string };
  days: SeriesPoint[];
  planned: TrainingActivity[];
  training: PlanSummary;
  plannedVsActual: PlannedVsActual;
}

export interface BlockWeek {
  weekStart: string;
  plannedDistanceKm: number;
  actualDistanceKm: number;
  completed: number;
  due: number;
}

export interface BlockStats {
  summary: PlanSummary;
  longestActivity: Activity | null;
  averagePlannedDistanceKm: number | null;
  averageActualDistanceKm: number | null;
  averagePaceSecondsPerKm: number | null;
  weeks: BlockWeek[];
}

export interface PlannedActivityInput {
  activityDate: string;
  activityType: string;
  title: string;
  description?: string | null;
  plannedDistanceKm?: number | null;
  plannedPaceSecondsPerKm?: number | null;
  plannedDurationSeconds?: number | null;
}

export interface ActivityInput {
  trainingActivityId?: string | null;
  activityDate: string;
  activityType: string;
  title: string;
  distanceKm?: number | null;
  paceSecondsPerKm?: number | null;
  averageHeartRate?: number | null;
  comment?: string | null;
}

export interface ResultInput {
  activityDate?: string;
  activityType?: string;
  title?: string;
  distanceKm?: number | null;
  paceSecondsPerKm?: number | null;
  averageHeartRate?: number | null;
  comment?: string | null;
}

export interface BlockInput {
  name: string;
  description?: string | null;
  startDate: string;
  endDate: string;
  goal?: string | null;
}
