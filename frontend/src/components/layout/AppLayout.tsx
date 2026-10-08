import { useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { ButtonLink } from "../ui/Button";
import { CalendarIcon, ChartIcon, HomeIcon, ListIcon, LogoMark, LogoutIcon, PlusIcon } from "../ui/Icons";

const NAV = [
  { to: "/", label: "Dashboard", Icon: HomeIcon, end: true },
  { to: "/training", label: "Training", Icon: CalendarIcon, end: false },
  { to: "/runs", label: "Runs", Icon: ListIcon, end: false },
  { to: "/stats", label: "Statistics", Icon: ChartIcon, end: false },
];

export function Wordmark() {
  return (
    <span className="flex items-center gap-2">
      <LogoMark />
      <span className="font-display text-2xl font-extrabold uppercase italic tracking-wide">
        Pace<span className="text-ember-500">log</span>
      </span>
    </span>
  );
}

export function AppLayout() {
  const location = useLocation();
  useEffect(() => window.scrollTo(0, 0), [location.pathname]);

  return (
    <div className="min-h-dvh">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-line bg-paper/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <NavLink to="/" aria-label="Pacelog home">
            <Wordmark />
          </NavLink>
          <nav aria-label="Main" className="hidden md:block">
            <ul className="flex items-center gap-1">
              {NAV.map(({ to, label, end }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    end={end}
                    className={({ isActive }) =>
                      `rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
                        isActive ? "bg-ink text-white" : "text-ink-soft hover:bg-ink/5"
                      }`
                    }
                  >
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ButtonLink to="/log" size="sm" icon={<PlusIcon width={18} height={18} />} className="max-sm:!hidden">
              Log Activity
            </ButtonLink>
            <ProfileMenu />
          </div>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-6xl px-4 pt-6 pb-28 sm:px-6 md:pb-12">
        <Outlet />
      </main>

      <BottomNav />
    </div>
  );
}

/** Mobile navigation with a central Log Activity button. */
function BottomNav() {
  const left = NAV.slice(0, 2);
  const right = NAV.slice(2);
  const item = ({ to, label, Icon, end }: (typeof NAV)[number]) => (
    <NavLink
      key={to}
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex flex-1 flex-col items-center gap-0.5 py-2 text-[0.7rem] font-semibold ${isActive ? "text-ember-600" : "text-muted"}`
      }
    >
      <Icon width={22} height={22} />
      {label}
    </NavLink>
  );
  return (
    <nav aria-label="Main" className="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-line bg-white/95 backdrop-blur md:hidden">
      <div className="mx-auto flex max-w-md items-end px-2">
        {left.map(item)}
        <div className="flex flex-1 justify-center">
          <NavLink
            to="/log"
            aria-label="Log Activity"
            className="-mt-6 grid h-14 w-14 place-items-center rounded-2xl bg-ember-500 text-white shadow-lg shadow-ember-500/30 ring-4 ring-paper"
          >
            <PlusIcon width={28} height={28} strokeWidth={2.5} />
          </NavLink>
        </div>
        {right.map(item)}
      </div>
    </nav>
  );
}

function ProfileMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!user) return null;
  const initials = (user.name || user.email).split(/\s+/).map((s) => s[0]).slice(0, 2).join("").toUpperCase();

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label="Account menu"
        className="grid h-10 w-10 place-items-center overflow-hidden rounded-full bg-ink text-sm font-bold text-white ring-2 ring-white"
      >
        {user.profilePicture ? (
          <img src={user.profilePicture} alt="" className="h-full w-full object-cover" referrerPolicy="no-referrer" />
        ) : (
          initials
        )}
      </button>
      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-64 rounded-2xl border border-line bg-white p-2 shadow-xl">
          <div className="px-3 py-2">
            <p className="truncate font-semibold">{user.name}</p>
            <p className="truncate text-sm text-muted">{user.email}</p>
          </div>
          <button
            role="menuitem"
            type="button"
            className="flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm font-semibold hover:bg-paper"
            onClick={async () => {
              await logout();
              navigate("/login");
            }}
          >
            <LogoutIcon width={18} height={18} /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

/** Page title row with optional back link and actions. */
export function PageHeader({
  title,
  eyebrow,
  sub,
  actions,
}: {
  title: string;
  eyebrow?: string;
  sub?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <p className="eyebrow mb-1">{eyebrow}</p>}
        <h1 className="font-display text-4xl font-extrabold leading-none tracking-tight sm:text-5xl">{title}</h1>
        {sub && <div className="mt-2 text-ink-soft">{sub}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
