import { useEffect, useState } from 'react';
import { getJobFacets } from '../services/jobService';
import { EMPTY_PLACE_FILTERS } from '../utils/jobFilters';
import Button from './Button';
import Select from './Select';

const NO_FACETS = { categories: [], countries: [], states: [], cities: [] };

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

// Category, country, state and city filters fed by /jobs/facets. value and the onChange argument
// are {category, country, state, city}. Facet loading problems never block the job list.
export default function JobFilters({
  scope = 'scraped',
  value,
  onChange,
  compact = false,
  showClear = true,
}) {
  const { category, country, state, city } = value;
  const [facets, setFacets] = useState(NO_FACETS);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  // Once per scope/country/state; a stale response is dropped.
  useEffect(() => {
    let current = true;
    setLoading(true);
    setFailed(false);
    getJobFacets({ scope: scope || 'scraped', country, state })
      .then((result) => {
        if (!current) return;
        setFacets({ ...NO_FACETS, ...result });
        setLoading(false);
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
  }, [scope, country, state, reloadCount]);

  const hasFilters = Boolean(category || country || state || city);

  return (
    <div className={compact ? 'job-filters job-filters--compact' : 'job-filters'} aria-busy={loading}>
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
          options={toOptions('All states', facets.states, state)}
          value={state}
          disabled={!country}
          onChange={(event) => onChange({ ...value, state: event.target.value, city: '' })}
        />
        <Select
          label="City"
          options={toOptions('All cities', facets.cities, city)}
          value={city}
          disabled={!country}
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
