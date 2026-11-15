export const EMPTY_PLACE_FILTERS = { category: '', country: '', state: '', city: '' };

// "City, ST, Country" per structured location joined by " · ", falling back to the raw text.
export function formatLocations(job) {
  const rows = (job.locations ?? [])
    .map((row) => [row.city, row.state, row.country].filter(Boolean).join(', '))
    .filter(Boolean);
  return rows.length > 0 ? rows.join(' · ') : job.location;
}
