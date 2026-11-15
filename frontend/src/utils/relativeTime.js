const UNITS = [
  ['year', 365 * 24 * 60 * 60 * 1000],
  ['month', 30 * 24 * 60 * 60 * 1000],
  ['day', 24 * 60 * 60 * 1000],
  ['hour', 60 * 60 * 1000],
  ['minute', 60 * 1000],
];

// "5 minutes ago", "3 days ago", "just now". Returns '' for a missing or invalid date.
export function formatRelativeTime(value, now = new Date()) {
  if (!value) return '';
  const then = new Date(value);
  if (Number.isNaN(then.getTime())) return '';
  const elapsed = new Date(now).getTime() - then.getTime();
  for (const [unit, size] of UNITS) {
    if (elapsed >= size) {
      const count = Math.floor(elapsed / size);
      return `${count} ${unit}${count === 1 ? '' : 's'} ago`;
    }
  }
  return 'just now';
}

// Posted time when the job has one, otherwise the saved time. `title` is the full date.
export function describeJobTime(job, now = new Date()) {
  const posted = Boolean(job?.postedAt);
  const value = posted ? job.postedAt : job?.createdAt;
  const relative = formatRelativeTime(value, now);
  if (!relative) return null;
  return {
    label: `${posted ? 'Posted' : 'Saved'} ${relative}`,
    title: new Date(value).toLocaleString(),
  };
}

const SOURCE_LABELS = {
  manual: 'Pasted manually',
  greenhouse: 'Greenhouse',
  lever: 'Lever',
  ashby: 'Ashby',
  workable: 'Workable',
  smartrecruiters: 'SmartRecruiters',
};

export function sourceLabel(source) {
  if (!source) return '';
  return SOURCE_LABELS[source] ?? source;
}

// Only http(s) links are safe to open from scraped data (blocks javascript: and data: URLs).
export function safeHttpUrl(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value.trim());
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}
