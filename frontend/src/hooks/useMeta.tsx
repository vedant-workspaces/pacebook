import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "../services/api";
import type { Meta, Option } from "../types";

// Activity types and skip reasons come from the API so new ones only need
// adding on the server. These fallbacks keep the UI usable while loading.
const FALLBACK: Meta = {
  activityTypes: [
    { value: "easy_run", label: "Easy Run", isRun: true },
    { value: "long_run", label: "Long Run", isRun: true },
    { value: "recovery", label: "Recovery Run", isRun: true },
    { value: "tempo", label: "Tempo Run", isRun: true },
    { value: "intervals", label: "Intervals", isRun: true },
    { value: "strength", label: "Strength" },
    { value: "rest", label: "Rest" },
    { value: "other", label: "Other" },
  ],
  completionReasons: [
    { value: "fatigue", label: "Fatigue" },
    { value: "other", label: "Other" },
  ],
};

const MetaContext = createContext<Meta>(FALLBACK);

export function MetaProvider({ children }: { children: ReactNode }) {
  const [meta, setMeta] = useState<Meta>(FALLBACK);
  useEffect(() => {
    api.meta().then(setMeta).catch(() => {});
  }, []);
  return <MetaContext.Provider value={meta}>{children}</MetaContext.Provider>;
}

export function useMeta() {
  const meta = useContext(MetaContext);
  const find = (opts: Option[], v?: string) => opts.find((o) => o.value === v);
  return {
    ...meta,
    typeLabel: (v: string) => find(meta.activityTypes, v)?.label ?? humanize(v),
    isRunType: (v: string) => find(meta.activityTypes, v)?.isRun ?? false,
    reasonLabel: (v?: string) => (v ? (find(meta.completionReasons, v)?.label ?? humanize(v)) : ""),
  };
}

function humanize(v: string) {
  return v.replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
}
