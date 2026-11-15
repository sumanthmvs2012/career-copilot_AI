import { useEffect, useRef, useState } from 'react';
import { getJobFacets } from '../services/jobService';
import { EMPTY_PLACE_FILTERS } from '../utils/jobFilters';
import Button from './Button';
import Select from './Select';

const NO_FACETS = { categories: [], countries: [], states: [], cities: [] };
const TEXT_DEBOUNCE_MS = 300;
const PLACE_KEYS = ['category', 'country', 'state', 'city'];

function toOptions(allLabel, facets, current) {
  const options = [
    { value: '', label: allLabel },
    ...facets.map(({ value, count }) => ({ value, label: `${value} (${count})` })),
  ];
  // Keep a value that came from the URL visible even before its facets have loaded.
  if (current && !options.some((option) => option.value === current)) {
    options.push({ value: current, label: current });
  }
  return options;
}

const has = (list, selected) =>
  list.some((entry) => (entry.value ?? '').toLowerCase() === selected.toLowerCase());

// Drops selections that are no longer offered; a removed parent also clears its children.
function pruneSelection(value, facets) {
  const next = { ...value };
  if (next.category && !has(facets.categories, next.category)) next.category = '';
  if (next.country && !has(facets.countries, next.country)) next.country = '';
  if (!next.country) {
    next.state = '';
    next.city = '';
  }
  if (next.state && !has(facets.states, next.state)) next.state = '';
  if (next.city && !has(facets.cities, next.city)) next.city = '';
  return next;
}

// Delays a changing text value; the first value is used immediately.
function useDebounced(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

// Category, country, state and city filters fed by /jobs/facets. value and the onChange argument
// are {category, country, state, city}. `others` carries the page's other active filters
// (q, location, source, remoteType, postedWithin) so every facet list reflects all of them except
// its own dimension. Facet loading problems never block the job list.
export default function JobFilters({
  scope = 'scraped',
  value,
  onChange,
  others = {},
  compact = false,
  showClear = true,
}) {
  const { category, country, state, city } = value;
  const { source = '', remoteType = '', postedWithin = '' } = others;
  const q = useDebounced(others.q ?? '', TEXT_DEBOUNCE_MS);
  const location = useDebounced(others.location ?? '', TEXT_DEBOUNCE_MS);
  const [facets, setFacets] = useState(NO_FACETS);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);
  const latest = useRef({ value, onChange });
  useEffect(() => {
    latest.current = { value, onChange };
  });

  // Refetch whenever any filter changes; a stale response is dropped.
  useEffect(() => {
    let current = true;
    setLoading(true);
    setFailed(false);
    getJobFacets({
      scope: scope || 'scraped',
      ...(q && { q }),
      ...(location && { location }),
      ...(source && { source }),
      ...(remoteType && { remoteType }),
      ...(postedWithin && { postedWithin }),
      category,
      country,
      state,
      city,
    })
      .then((result) => {
        if (!current) return;
        const loaded = { ...NO_FACETS, ...result };
        setFacets(loaded);
        setLoading(false);
        const selected = latest.current.value;
        const pruned = pruneSelection(selected, loaded);
        if (PLACE_KEYS.some((key) => pruned[key] !== selected[key])) {
          latest.current.onChange(pruned);
        }
      })
      .catch((error) => {
        if (!current) return;
        console.error('Loading job filters failed', { status: error?.status ?? 'unknown' });
        setFailed(true);
        setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [
    scope,
    q,
    location,
    source,
    remoteType,
    postedWithin,
    category,
    country,
    state,
    city,
    reloadCount,
  ]);

  const stateDisabled = !country || facets.states.length === 0;
  const cityDisabled = !country || facets.cities.length === 0;
  let stateAll = 'All states';
  if (!country) stateAll = 'Select a country first';
  else if (stateDisabled) stateAll = 'No states available';
  let cityAll = 'All cities';
  if (!country) cityAll = 'Select a country first';
  else if (cityDisabled) cityAll = 'No cities available';

  const hasFilters = Boolean(category || country || state || city);

  return (
    <div
      className={compact ? 'job-filters job-filters--compact' : 'job-filters'}
      aria-busy={loading}
    >
      <div className="job-filters__grid">
        <Select
          label="Category"
          options={toOptions('All categories', facets.categories, category)}
          value={category}
          onChange={(event) => onChange({ ...value, category: event.target.value })}
        />
        <Select
          label="Country"
          options={toOptions('All countries', facets.countries, country)}
          value={country}
          onChange={(event) =>
            onChange({ ...value, country: event.target.value, state: '', city: '' })
          }
        />
        <Select
          label="State"
          options={toOptions(stateAll, facets.states, state)}
          value={state}
          disabled={stateDisabled}
          onChange={(event) => onChange({ ...value, state: event.target.value, city: '' })}
        />
        <Select
          label="City"
          options={toOptions(cityAll, facets.cities, city)}
          value={city}
          disabled={cityDisabled}
          onChange={(event) => onChange({ ...value, city: event.target.value })}
        />
      </div>
      {failed && (
        <p className="job-filters__note muted">
          Filter options could not be loaded.{' '}
          <button
            type="button"
            className="job-item__toggle"
            onClick={() => setReloadCount((n) => n + 1)}
          >
            Retry
          </button>
        </p>
      )}
      {showClear && (
        <Button
          type="button"
          variant="secondary"
          disabled={!hasFilters}
          onClick={() => onChange({ ...EMPTY_PLACE_FILTERS })}
        >
          Clear filters
        </Button>
      )}
    </div>
  );
}
