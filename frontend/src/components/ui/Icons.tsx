import type { ReactElement, SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { title?: string };

function Svg({ title, children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      width={20}
      height={20}
      aria-hidden={title ? undefined : true}
      role={title ? "img" : undefined}
      {...props}
    >
      {title && <title>{title}</title>}
      {children}
    </svg>
  );
}

export const PlusIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);
export const CheckIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </Svg>
);
export const XIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 6l12 12M18 6 6 18" />
  </Svg>
);
export const TildeIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 14c2.5-4 5-4 8 0s5.5 4 8 0" />
  </Svg>
);
export const CircleIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="6.5" />
  </Svg>
);
export const ChevronLeft = (p: IconProps) => (
  <Svg {...p}>
    <path d="m15 18-6-6 6-6" />
  </Svg>
);
export const ChevronRight = (p: IconProps) => (
  <Svg {...p}>
    <path d="m9 18 6-6-6-6" />
  </Svg>
);
export const ArrowLeft = (p: IconProps) => (
  <Svg {...p}>
    <path d="M19 12H5M12 19l-7-7 7-7" />
  </Svg>
);
export const PencilIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4" />
  </Svg>
);
export const TrashIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
  </Svg>
);
export const LinkIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />
  </Svg>
);
export const HeartIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" />
    <path d="M7.5 12h2l1-2 2 4 1-2h3" strokeWidth={1.6} />
  </Svg>
);
export const TimerIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="13" r="7.5" />
    <path d="M12 9v4l2.5 2M10 2.5h4" />
  </Svg>
);
export const RouteIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="6" cy="18" r="2" />
    <circle cx="18" cy="6" r="2" />
    <path d="M8 18h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7" />
  </Svg>
);
export const TrophyIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M8 4h8v5a4 4 0 0 1-8 0V4zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 21h8M9.5 17h5" />
  </Svg>
);
export const BoltIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M13 3 5 14h6l-1 7 8-11h-6l1-7z" />
  </Svg>
);
export const CalendarIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3.5" y="5" width="17" height="15" rx="2.5" />
    <path d="M3.5 10h17M8 3v4M16 3v4" />
  </Svg>
);
export const HomeIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 11.5 12 4l8 7.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1v-8.5z" />
  </Svg>
);
export const ListIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01" strokeWidth={2.4} />
  </Svg>
);
export const ChartIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />
  </Svg>
);
export const LogoutIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10" />
  </Svg>
);

/** Brand mark: a rising pace line ending in a "you are here" dot. */
export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="8" fill="#0E1726" />
      <path
        d="M5 22 L11 15 L15 18 L21 9 L27 13"
        fill="none"
        stroke="#FF5A1F"
        strokeWidth={3}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="27" cy="13" r="2.5" fill="#FF5A1F" />
    </svg>
  );
}

// Activity glyphs, grouped so new activity types fall back gracefully.
const SHOE = (
  <path d="M3 16.5c0-1.5.5-3.5 1.5-5L6 9l3 1.5 2-2.5 3 3.5c2 .5 4.5 1 6 2.5.7.7 1 1.6 1 2.5H3zM3 19.5h18" />
);
const MOUNTAIN = <path d="m2.5 19 6.5-10 4 6 2.5-3.5 6 7.5z" />;
const DUMBBELL = <path d="M3 9.5v5M6 7v10M18 7v10M21 9.5v5M6 12h12" />;
const BED = <path d="M3 18V7M3 13h18v5M21 13a3 3 0 0 0-3-3h-7v3M7 11.5h.01" />;
const WAVES = <path d="M3 9c2-2 4-2 6 0s4 2 6 0 4-2 6 0M3 15c2-2 4-2 6 0s4 2 6 0 4-2 6 0" />;
const FLAG = <path d="M5 21V4M5 4h11l-2 4 2 4H5" />;
const WALK = (
  <>
    <circle cx="13" cy="4.5" r="1.8" />
    <path d="M10 21l2.5-6.5L15 17v4M9 11l3-3 3 3 2.5 1.5M12 8l-1.5 6.5" />
  </>
);
const INTERVALS = <path d="M3 18V12h3v6M9 18V8h3v10M15 18V5h3v13M21 18H2" />;
const TREADMILL = <path d="M4 17h15l2-6M4 17l-1 3M19 17l1 3M14 7l2-3M7 11h7" />;
const DOTS = <path d="M6 12h.01M12 12h.01M18 12h.01" strokeWidth={3} />;

const GLYPHS: Record<string, ReactElement> = {
  easy_run: SHOE,
  long_run: SHOE,
  recovery: SHOE,
  tempo: SHOE,
  fartlek: SHOE,
  intervals: INTERVALS,
  hill_repeats: MOUNTAIN,
  trail_run: MOUNTAIN,
  race: FLAG,
  treadmill: TREADMILL,
  walk: WALK,
  strength: DUMBBELL,
  cross_training: WAVES,
  rest: BED,
  other: DOTS,
};

export function ActivityGlyph({ type, ...p }: IconProps & { type: string }) {
  return <Svg {...p}>{GLYPHS[type] ?? SHOE}</Svg>;
}
