import { ApiError } from './apiClient';
import { getToken } from '../utils/authToken';
import { hasMockProfile, isMockProfileEnabled } from './mockProfile';

// Temporary stand-in until the Spring Boot /jobs endpoints are wired up. State lives in memory
// only, so a page reload resets it; that is fine for a dev-only mock.
const MOCK_DELAY_MS = 400;
const MATCH_UNAVAILABLE = 'Matching is not available yet; your job is saved.';
const DAY_MS = 24 * 60 * 60 * 1000;
let storedJobs = [];
let nextId = 1;

export function isMockJobsEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_JOBS === 'true';
}

export function resetMockJobs() {
  storedJobs = [];
  nextId = 1;
}

async function delayAndAuthorize() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (!getToken()) throw new ApiError('Request failed with status 401', 401, null);
}

export async function mockMatchJob(input) {
  await delayAndAuthorize();
  // Only enforceable when the profile mock is also on; otherwise the real rule cannot be known.
  if (isMockProfileEnabled() && !hasMockProfile()) {
    throw new ApiError('Request failed with status 404', 404, {
      status: 404,
      message: 'Create your profile first.',
    });
  }
  const length = (input.description ?? '').trim().length;
  if (length < 20 || length > 20000) {
    throw new ApiError('Request failed with status 400', 400, {
      status: 400,
      message: 'Validation failed',
      fieldErrors: { description: 'Description must be between 20 and 20000 characters.' },
    });
  }
  const job = {
    id: nextId++,
    title: input.title ?? null,
    companyName: input.companyName ?? null,
    location: input.location ?? null,
    category: null,
    locations: [],
    remoteType: input.remoteType ?? null,
    source: 'manual',
    sourceUrl: input.sourceUrl ?? null,
    description: input.description.trim(),
    postedAt: null,
    createdAt: new Date().toISOString(),
  };
  storedJobs = [job, ...storedJobs];
  return { job: { ...job }, match: null, message: MATCH_UNAVAILABLE };
}

const SOURCES = ['greenhouse', 'lever', 'ashby', 'workable', 'smartrecruiters'];
const COMPANIES = ['Stripe', 'Palantir', 'Datadog', 'Notion', 'Canva', 'Shopify'];
const TITLES = [
  ['Senior Backend Engineer', 'Software Engineering'],
  ['Platform Engineer', 'IT & Infrastructure'],
  ['Frontend Engineer', 'Software Engineering'],
  ['Data Engineer', 'Data & ML'],
  ['Product Designer', 'Design'],
  ['Site Reliability Engineer', 'IT & Infrastructure'],
  ['Product Manager', 'Product'],
  ['Security Engineer', 'Security'],
  ['Account Executive', 'Sales'],
  ['Marketing Manager', 'Marketing'],
  ['Support Specialist', 'Customer Support'],
  ['Machine Learning Engineer', 'Data & ML'],
];
const place = (city, state, country) => ({ city, state, country });
const US = 'United States';
const UK = 'United Kingdom';
// Free-text location plus the structured rows the backend would parse from it.
const LOCATIONS = [
  { text: 'San Francisco, CA', locations: [place('San Francisco', 'CA', US)] },
  { text: 'London, UK', locations: [place('London', null, UK)] },
  { text: 'New York, NY, USA', locations: [place('New York', 'NY', US)] },
  { text: 'Toronto, ON, Canada', locations: [place('Toronto', 'ON', 'Canada')] },
  { text: 'Remote', locations: [] },
  { text: 'Bangalore, India', locations: [place('Bangalore', 'Karnataka', 'India')] },
  { text: 'Austin, TX', locations: [place('Austin', 'TX', US)] },
  { text: 'Singapore', locations: [place('Singapore', null, 'Singapore')] },
  { text: 'Berlin, Germany', locations: [place('Berlin', null, 'Germany')] },
  { text: 'Seattle, WA', locations: [place('Seattle', 'WA', US)] },
  { text: 'Dublin, Ireland', locations: [place('Dublin', null, 'Ireland')] },
  { text: 'US-Remote', locations: [place(null, null, US)] },
  {
    text: 'San Francisco, CA or New York, NY',
    locations: [place('San Francisco', 'CA', US), place('New York', 'NY', US)],
  },
  { text: 'Manchester, UK', locations: [place('Manchester', null, UK)] },
];
const REMOTE_TYPES = ['onsite', 'hybrid', 'remote', null];
const SCRAPED_COUNT = 36;

// 36 deterministic board-style postings, newest first (about 0.8 days apart).
function scrapedJobs() {
  const now = Date.now();
  return Array.from({ length: SCRAPED_COUNT }, (_, i) => {
    const source = SOURCES[i % SOURCES.length];
    const postedAt = new Date(now - (i + 1) * 0.8 * DAY_MS).toISOString();
    const [title, category] = TITLES[i % TITLES.length];
    const { text, locations } = LOCATIONS[(i * 3) % LOCATIONS.length];
    return {
      id: 9001 + i,
      title,
      category,
      companyName: COMPANIES[(i * 5) % COMPANIES.length],
      location: text,
      locations: locations.map((row) => ({ ...row })),
      remoteType: REMOTE_TYPES[i % REMOTE_TYPES.length],
      source,
      sourceUrl: `https://jobs.example.com/${source}/${9001 + i}`,
      description: `Role ${i + 1}: build and run production services.
Work with a small, senior team.`,
      postedAt,
      createdAt: postedAt,
    };
  });
}

function contains(value, text) {
  return (value ?? '').toLowerCase().includes(text.toLowerCase());
}

const same = (a, b) => (a ?? '').toLowerCase() === (b ?? '').toLowerCase();

// country/state/city must all match on the same location row.
function matchesPlace(job, { country, state, city }) {
  if (!country && !state && !city) return true;
  return (job.locations ?? []).some(
    (row) =>
      (!country || same(row.country, country)) &&
      (!state || same(row.state, state)) &&
      (!city || same(row.city, city)),
  );
}

function matchesFilters(
  job,
  { q, source, remoteType, location, postedWithin, category, country, state, city },
) {
  const postedMs = Date.parse(job.postedAt || job.createdAt);
  return (
    (!category || same(job.category, category)) &&
    matchesPlace(job, { country, state, city }) &&
    (!q || contains(job.title, q) || contains(job.companyName, q)) &&
    (!source || job.source === source) &&
    (!remoteType || job.remoteType === remoteType) &&
    (!location || contains(job.location, location)) &&
    (!postedWithin || postedMs >= Date.now() - Number(postedWithin) * DAY_MS)
  );
}

// scope 'scraped' serves the sample board postings, 'pasted' the saved ones, none both.
// Filters (q, source, remoteType, location, postedWithin) combine with AND, then offset/limit page.
export async function mockListJobs({ scope, limit, offset, ...filters } = {}) {
  await delayAndAuthorize();
  const pasted = scope === 'scraped' ? [] : storedJobs;
  const scraped = scope === 'pasted' ? [] : scrapedJobs();
  const rows = [...pasted, ...scraped]
    .filter((job) => matchesFilters(job, filters))
    .sort(
      (a, b) =>
        Date.parse(b.postedAt || b.createdAt) - Date.parse(a.postedAt || a.createdAt),
    );
  const start = Number(offset) || 0;
  const end = limit ? start + Number(limit) : rows.length;
  return rows.slice(start, end).map((job) => ({ ...job }));
}

// Counts distinct jobs per value, most common first (ties alphabetical).
function tally(entries) {
  const counts = new Map();
  for (const values of entries) {
    for (const value of new Set(values.filter(Boolean))) {
      counts.set(value, (counts.get(value) ?? 0) + 1);
    }
  }
  return [...counts]
    .map(([value, count]) => ({ value, count }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
}

// Facets for the jobs visible in the scope. Cascading: states follow the country, cities follow
// the country and state; cities are capped at 100 like the backend.
export async function mockJobFacets({ scope = 'scraped', country, state } = {}) {
  await delayAndAuthorize();
  const jobs = scope === 'pasted' ? storedJobs : scrapedJobs();
  const rows = (job) => job.locations ?? [];
  const inCountry = (row) => !country || same(row.country, country);
  const inState = (row) => !state || same(row.state, state);
  return {
    categories: tally(jobs.map((job) => [job.category])),
    countries: tally(jobs.map((job) => rows(job).map((row) => row.country))),
    states: tally(jobs.map((job) => rows(job).filter(inCountry).map((row) => row.state))),
    cities: tally(
      jobs.map((job) =>
        rows(job)
          .filter((row) => inCountry(row) && inState(row))
          .map((row) => row.city),
      ),
    ).slice(0, 100),
  };
}
