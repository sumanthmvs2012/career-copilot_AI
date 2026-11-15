import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import JobDetails from '../components/JobDetails';
import JobFilters from '../components/JobFilters';
import Select from '../components/Select';
import TextInput from '../components/TextInput';
import { listJobs } from '../services/jobService';
import { clearToken } from '../utils/authToken';

const PAGE_SIZE = 20;
const DEBOUNCE_MS = 300;
const MAX_TEXT_LENGTH = 100;

const SOURCE_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'greenhouse', label: 'Greenhouse' },
  { value: 'lever', label: 'Lever' },
  { value: 'ashby', label: 'Ashby' },
  { value: 'workable', label: 'Workable' },
  { value: 'smartrecruiters', label: 'SmartRecruiters' },
];
const REMOTE_OPTIONS = [
  { value: '', label: 'Any' },
  { value: 'remote', label: 'Remote' },
  { value: 'hybrid', label: 'Hybrid' },
  { value: 'onsite', label: 'On-site' },
];
const POSTED_OPTIONS = [
  { value: '', label: 'Any time' },
  { value: '1', label: 'Last 24 hours' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
];
// "All jobs" sends no scope, which the backend treats as both kinds.
const SCOPE_OPTIONS = [
  { value: '', label: 'All jobs' },
  { value: 'scraped', label: 'Collected from boards' },
  { value: 'pasted', label: 'My pasted jobs' },
];

const allowed = (options, value) => (options.some((o) => o.value === value) ? value : '');

// Reads the filters from the URL; unknown select values fall back to "any".
function readFilters(searchParams) {
  return {
    q: (searchParams.get('q') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
    location: (searchParams.get('location') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
    source: allowed(SOURCE_OPTIONS, searchParams.get('source') ?? ''),
    remoteType: allowed(REMOTE_OPTIONS, searchParams.get('remoteType') ?? ''),
    postedWithin: allowed(POSTED_OPTIONS, searchParams.get('postedWithin') ?? ''),
    scope: allowed(SCOPE_OPTIONS, searchParams.get('scope') ?? ''),
    category: (searchParams.get('category') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
    country: (searchParams.get('country') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
    state: (searchParams.get('state') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
    city: (searchParams.get('city') ?? '').trim().slice(0, MAX_TEXT_LENGTH),
  };
}

export default function JobsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readFilters(searchParams);
  const { q, location, source, remoteType, postedWithin, scope, category, country, state, city } =
    filters;

  const [qInput, setQInput] = useState(q);
  const [locationInput, setLocationInput] = useState(location);
  const [status, setStatus] = useState('loading');
  const [jobs, setJobs] = useState([]);
  const [lastPageSize, setLastPageSize] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);
  // Bumped on every new search; a response only counts if its id is still the latest.
  const requestId = useRef(0);

  const goToLogin = useCallback(() => {
    clearToken();
    navigate('/login', { replace: true });
  }, [navigate]);

  const updateParam = useCallback(
    (key, value) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (value) next.set(key, value);
          else next.delete(key);
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  // Debounce the two text fields into the URL (which is what triggers the search).
  useEffect(() => {
    const timer = setTimeout(() => {
      const nextQ = qInput.trim();
      const nextLocation = locationInput.trim();
      if (nextQ === q && nextLocation === location) return;
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (nextQ) next.set('q', nextQ);
          else next.delete('q');
          if (nextLocation) next.set('location', nextLocation);
          else next.delete('location');
          return next;
        },
        { replace: true },
      );
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [qInput, locationInput, q, location, setSearchParams]);

  // First page: runs on mount, on every filter change and on retry.
  useEffect(() => {
    const id = ++requestId.current;
    setStatus('loading');
    setMoreError(false);
    setLoadingMore(false);
    listJobs({
      scope,
      limit: PAGE_SIZE,
      offset: 0,
      q,
      source,
      remoteType,
      location,
      postedWithin,
      category,
      country,
      state,
      city,
    })
      .then((rows) => {
        if (id !== requestId.current) return;
        setJobs(rows);
        setLastPageSize(rows.length);
        setStatus(rows.length === 0 ? 'empty' : 'success');
      })
      .catch((error) => {
        if (id !== requestId.current) return;
        if (error?.status === 401) return goToLogin();
        console.error('Loading jobs failed', { status: error?.status ?? 'unknown' });
        setStatus('error');
      });
  }, [
    scope,
    q,
    source,
    remoteType,
    location,
    postedWithin,
    category,
    country,
    state,
    city,
    reloadCount,
    goToLogin,
  ]);

  async function loadMore() {
    const id = requestId.current;
    setLoadingMore(true);
    setMoreError(false);
    try {
      const rows = await listJobs({
        scope,
        limit: PAGE_SIZE,
        offset: jobs.length,
        q,
        source,
        remoteType,
        location,
        postedWithin,
        category,
        country,
        state,
        city,
      });
      if (id !== requestId.current) return;
      setJobs((prev) => [...prev, ...rows]);
      setLastPageSize(rows.length);
      setLoadingMore(false);
    } catch (error) {
      if (id !== requestId.current) return;
      if (error?.status === 401) return goToLogin();
      console.error('Loading more jobs failed', { status: error?.status ?? 'unknown' });
      setMoreError(true);
      setLoadingMore(false);
    }
  }

  function updatePlace(next) {
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        for (const key of ['category', 'country', 'state', 'city']) {
          if (next[key]) params.set(key, next[key]);
          else params.delete(key);
        }
        return params;
      },
      { replace: true },
    );
  }

  function clearFilters() {
    setQInput('');
    setLocationInput('');
    setSearchParams({}, { replace: true });
  }

  const hasFilters = Object.values(filters).some(Boolean) || qInput !== '' || locationInput !== '';

  return (
    <main className="page jobs-page">
      <header className="page__header">
        <h1>Jobs</h1>
        <Link className="btn btn--secondary" to="/dashboard">
          Back to dashboard
        </Link>
      </header>

      <section className="card jobs-filters" aria-labelledby="filters-heading">
        <h2 id="filters-heading">Filters</h2>
        <form role="search" onSubmit={(event) => event.preventDefault()}>
          <div className="jobs-filters__grid">
            <TextInput
              label="Search"
              type="search"
              value={qInput}
              maxLength={MAX_TEXT_LENGTH}
              placeholder="Title or company"
              onChange={(event) => setQInput(event.target.value)}
            />
            <TextInput
              label="Location"
              value={locationInput}
              maxLength={MAX_TEXT_LENGTH}
              onChange={(event) => setLocationInput(event.target.value)}
            />
            <Select
              label="Source"
              options={SOURCE_OPTIONS}
              value={source}
              onChange={(event) => updateParam('source', event.target.value)}
            />
            <Select
              label="Remote type"
              options={REMOTE_OPTIONS}
              value={remoteType}
              onChange={(event) => updateParam('remoteType', event.target.value)}
            />
            <Select
              label="Posted within"
              options={POSTED_OPTIONS}
              value={postedWithin}
              onChange={(event) => updateParam('postedWithin', event.target.value)}
            />
            <Select
              label="Scope"
              options={SCOPE_OPTIONS}
              value={scope}
              onChange={(event) => updateParam('scope', event.target.value)}
            />
          </div>
          <JobFilters
            scope={scope}
            value={{ category, country, state, city }}
            onChange={updatePlace}
            others={{ q, location, source, remoteType, postedWithin }}
            showClear={false}
          />
          <Button type="button" variant="secondary" onClick={clearFilters} disabled={!hasFilters}>
            Clear filters
          </Button>
        </form>
      </section>

      <section className="card jobs-results" aria-labelledby="results-heading">
        <h2 id="results-heading">Results</h2>
        {status === 'loading' && <p role="status">Loading jobs…</p>}
        {status === 'error' && (
          <>
            <Alert variant="error">We could not load jobs. Please try again.</Alert>
            <Button variant="secondary" onClick={() => setReloadCount((n) => n + 1)}>
              Try again
            </Button>
          </>
        )}
        {status === 'empty' && (
          <>
            <p>No jobs match these filters.</p>
            <Button variant="secondary" onClick={clearFilters}>
              Clear filters
            </Button>
          </>
        )}
        {status === 'success' && (
          <>
            <p role="status" className="muted">
              Showing {jobs.length} {jobs.length === 1 ? 'job' : 'jobs'}
            </p>
            <ul className="job-list">
              {jobs.map((job) => (
                <li key={job.id}>
                  <JobDetails job={job} />
                </li>
              ))}
            </ul>
            {moreError && (
              <Alert variant="error">We could not load more jobs. Please try again.</Alert>
            )}
            {lastPageSize === PAGE_SIZE && (
              <Button variant="secondary" onClick={loadMore} loading={loadingMore}>
                Load more
              </Button>
            )}
          </>
        )}
      </section>
    </main>
  );
}
