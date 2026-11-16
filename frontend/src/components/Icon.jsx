// Small inline SVG icons. Always decorative (aria-hidden): every use sits next to visible text.
const PATHS = {
  briefcase: 'M3 8h18v11H3zM9 8V5h6v3M3 13h18',
  file: 'M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 16h6',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c0-4 3.6-6 8-6s8 2 8 6',
  mail: 'M3 6h18v12H3zM3 7l9 7 9-7',
  spark: 'M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  plus: 'M12 5v14M5 12h14',
  swap: 'M7 7h12l-3-3M17 17H5l3 3',
  tag: 'M3 12V4h8l10 10-8 8zM7.5 8.5h.01',
  headline: 'M5 6h14M5 12h14M5 18h8',
  pin: 'M12 21s7-6 7-11a7 7 0 10-14 0c0 5 7 11 7 11zM12 12a2 2 0 100-4 2 2 0 000 4z',
  bulb: 'M9 18h6M10 21h4M12 3a6 6 0 00-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0012 3z',
  copy: 'M9 9h11v11H9zM5 15V4h11',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  target: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 16a4 4 0 100-8 4 4 0 000 8zM12 12h.01',
  chat: 'M4 5h16v11H9l-5 4z',
  bookmark: 'M6 3h12v18l-6-4-6 4z',
  chevron: 'M6 9l6 6 6-6',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  wand: 'M5 19L17 7M15 5l1-2 1 2 2 1-2 1-1 2-1-2-2-1zM6 5l.5-1 .5 1 1 .5-1 .5-.5 1-.5-1-1-.5z',
  inbox: 'M3 13l3-8h12l3 8v6H3zM3 13h5l1 3h6l1-3h5',
  sun: 'M12 16a4 4 0 100-8 4 4 0 000 8zM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5',
};

export default function Icon({ name, size = 18, className = '' }) {
  const d = PATHS[name];
  if (!d) return null;
  return (
    <svg
      className={`icon ${className}`.trim()}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={d} />
    </svg>
  );
}
