export const EMPTY_PLACE_FILTERS = { category: '', country: '', state: '' };

// "City, ST, Country" per structured location joined by " · ", falling back to the raw text.
export function formatLocations(job) {
  const rows = (job.locations ?? [])
    .map((row) => [row.city, row.state, row.country].filter(Boolean).join(', '))
    .filter(Boolean);
  return rows.length > 0 ? rows.join(' · ') : job.location;
}

// Short form for a list row: the first `max` places as "City, State" (the country only when a row
// has nothing else), then "+N more". The full list stays available through formatLocations.
export function summarizeLocations(job, max = 2) {
  const seen = new Set();
  const places = [];
  for (const row of job.locations ?? []) {
    const label = [row.city, row.state].filter(Boolean).join(', ') || row.country;
    if (label && !seen.has(label)) {
      seen.add(label);
      places.push(label);
    }
  }
  if (places.length === 0) return job.location ?? '';
  const shown = places.slice(0, max).join(' · ');
  return places.length > max ? `${shown} · +${places.length - max} more` : shown;
}
