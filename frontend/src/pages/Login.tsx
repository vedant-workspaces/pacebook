import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { Wordmark } from "../components/layout/AppLayout";
import { Button } from "../components/ui/Button";
import { ElevationLine } from "../components/ui/Feedback";
import { FormError, TextInput } from "../components/ui/Form";
import { useAsync } from "../hooks/useAsync";
import { useAuth } from "../hooks/useAuth";
import { api, errorMessage } from "../services/api";

export default function Login() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const config = useAsync(() => api.authConfig(), []);
  const [email, setEmail] = useState("runner@pacelog.local");
  const [devError, setDevError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function devLogin(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setDevError(null);
    try {
      await api.devLogin(email, "");
      await refresh();
      navigate("/");
    } catch (err) {
      setDevError(errorMessage(err));
      setBusy(false);
    }
  }

  const googleEnabled = config.data?.googleEnabled ?? true;

  return (
    <div className="relative flex min-h-dvh flex-col overflow-hidden bg-ink text-white">
      <ElevationLine className="pointer-events-none absolute inset-x-0 bottom-0 h-64 w-full text-ember-500" />
      <svg className="pointer-events-none absolute -right-24 top-16 h-96 w-96 text-white/5" viewBox="0 0 200 200" aria-hidden>
        {[90, 70, 50, 30].map((r) => (
          <circle key={r} cx="100" cy="100" r={r} fill="none" stroke="currentColor" strokeWidth="10" />
        ))}
      </svg>

      <main className="relative z-10 mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-6 py-16">
        <Wordmark />
        <h1 className="mt-10 font-display text-5xl font-extrabold uppercase leading-[0.95] tracking-tight sm:text-6xl">
          Plan your training.
          <br />
          <span className="text-ember-500">Run your plan.</span>
          <br />
          <span className="text-white/70">Track what actually happened.</span>
        </h1>
        <p className="mt-5 max-w-sm text-white/70">
          A training diary for runners who want to see the plan and the reality side by side.
        </p>

        <div className="mt-10 space-y-3">
          {params.get("error") && (
            <p role="alert" className="rounded-xl bg-missed/20 px-4 py-3 text-sm font-medium text-white">
              Sign-in didn't complete. Please try again.
            </p>
          )}
          {googleEnabled && (
            <a
              href="/api/auth/google"
              className="flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-white text-base font-semibold text-ink shadow-lg transition hover:bg-white/90"
            >
              <GoogleG />
              Continue with Google
            </a>
          )}
          {config.data && !config.data.googleEnabled && !config.data.devLoginEnabled && (
            <p className="rounded-xl bg-white/10 px-4 py-3 text-sm text-white/80">
              Google sign-in isn't configured on this server yet. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.
            </p>
          )}
          {config.data?.devLoginEnabled && (
            <form onSubmit={devLogin} className="rounded-2xl border border-white/15 bg-white/5 p-4">
              <label htmlFor="dev-email" className="text-xs font-bold uppercase tracking-widest text-white/60">
                Development sign-in
              </label>
              <div className="mt-2 flex gap-2">
                <TextInput id="dev-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="!h-11" />
                <Button type="submit" size="md" loading={busy}>
                  Sign in
                </Button>
              </div>
              <div className="mt-2">
                <FormError message={devError} />
              </div>
            </form>
          )}
        </div>
        <p className="mt-8 text-xs text-white/50">
          By continuing you agree to the{" "}
          <Link to="/terms" className="underline hover:text-white">Terms</Link> and{" "}
          <Link to="/privacy" className="underline hover:text-white">Privacy Policy</Link>.
        </p>
      </main>
    </div>
  );
}

function GoogleG() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden>
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  );
}
