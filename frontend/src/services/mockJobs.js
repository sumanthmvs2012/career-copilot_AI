import { ApiError } from './apiClient';
import { getToken } from '../utils/authToken';
import { hasMockProfile, isMockProfileEnabled } from './mockProfile';
import { MOCK_RESUME_SKILLS, hasMockResume, isMockResumeEnabled } from './mockResume';

// Temporary stand-in until the Spring Boot /jobs endpoints are wired up. State lives in memory
// only, so a page reload resets it; that is fine for a dev-only mock.
const MOCK_DELAY_MS = 400;
const NO_RESUME_MESSAGE = 'Upload a resume to see your match.';
const NO_RESUME_ERROR = 'Upload a resume first to see your match.';
const DAY_MS = 24 * 60 * 60 * 1000;
let storedJobs = [];
let nextId = 1;
const storedMatches = new Map();

export function isMockJobsEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_JOBS === 'true';
}

export function resetMockJobs() {
  storedJobs = [];
  nextId = 1;
  storedMatches.clear();
}

async function delayAndAuthorize() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (!getToken()) throw new ApiError('Request failed with status 401', 401, null);
}

export async function mockMatchPastedJob(input) {
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
  if (!hasBaseResume()) return { job: { ...job }, match: null, message: NO_RESUME_MESSAGE };
  const match = buildMatch(job);
  storedMatches.set(job.id, match);
  return { job: { ...job }, match: { ...match }, message: 'Your job is saved and matched.' };
}

// Without the resume mock there is nothing to check, so assume a base resume exists.
function hasBaseResume() {
  return isMockResumeEnabled() ? hasMockResume() : true;
}

const KNOWN_SKILLS = [
  'Java',
  'Spring Boot',
  'SQL',
  'React',
  'React Native',
  'Docker',
  'Git',
  'Kubernetes',
  'Python',
  'TypeScript',
  'AWS',
  'Figma',
];
const RELATED = { React: 'React Native' };
const STOP = new Set([
  'with',
  'work',
  'team',
  'small',
  'senior',
  'build',
  'production',
  'services',
]);

function findSkills(text) {
  const lower = (text ?? '').toLowerCase();
  return KNOWN_SKILLS.filter((skill) => new RegExp(`\\b${skill.toLowerCase()}\\b`).test(lower));
}

// A plausible stand-in for the backend's keyword match; same shape as ARCHITECTURE.md.
function buildMatch(job) {
  const jobSkills = findSkills(`${job.title ?? ''} ${job.description ?? ''}`);
  const have = new Set(MOCK_RESUME_SKILLS.map((s) => s.toLowerCase()));
  const matchedSkills = jobSkills.filter((s) => have.has(s.toLowerCase()));
  const rest = jobSkills.filter((s) => !have.has(s.toLowerCase()));
  const nearMatchedSkills = rest
    .filter((s) => RELATED[s] && have.has(RELATED[s].toLowerCase()))
    .map((s) => ({ jobSkill: s, resumeSkill: RELATED[s] }));
  const words = new Set((job.description ?? '').toLowerCase().match(/[a-z]{5,}/g) ?? []);
  const missingKeywords = [...words].filter((w) => !STOP.has(w)).slice(0, 5);
  const remote = job.remoteType === 'remote' || /remote/i.test(job.location ?? '');
  let locationFit = { status: 'unknown', note: 'We could not compare this job location.' };
  if (remote) locationFit = { status: 'match', note: 'This job is remote.' };
  else if (job.location)
    locationFit = { status: 'partial', note: 'Check that you can work from this location.' };
  return {
    jobId: job.id,
    resumeId: 1,
    score: jobSkills.length ? Math.round((100 * matchedSkills.length) / jobSkills.length) : 0,
    matchedSkills,
    missingSkills: rest,
    nearMatchedSkills,
    missingKeywords,
    suggestedHeadline: job.title ?? null,
    locationFit,
    createdAt: new Date().toISOString(),
  };
}

function findVisibleJob(id) {
  return [...storedJobs, ...scrapedJobs()].find((job) => job.id === Number(id));
}

function notFound() {
  return new ApiError('Request failed with status 404', 404, {
    status: 404,
    message: 'Not found.',
  });
}

// POST /jobs/{id}/match: 404 for an unknown job, 409 without a base resume; replaces any stored result.
export async function mockMatchJobById(id) {
  await delayAndAuthorize();
  const job = findVisibleJob(id);
  if (!job) throw notFound();
  if (!hasBaseResume()) {
    throw new ApiError('Request failed with status 409', 409, {
      status: 409,
      message: NO_RESUME_ERROR,
    });
  }
  const match = buildMatch(job);
  storedMatches.set(job.id, match);
  return { ...match };
}

// GET /jobs/{id}/match: the stored result, or 404 when none yet.
export async function mockGetStoredMatch(id) {
  await delayAndAuthorize();
  const match = storedMatches.get(Number(id));
  if (!match) throw notFound();
  return { ...match };
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
const SKILLS_BY_TITLE = [
  'Java, Spring Boot, SQL, Docker',
  'Kubernetes, Docker, AWS, Git',
  'React, TypeScript, Git',
  'Python, SQL, AWS',
  'Figma',
  'Kubernetes, AWS, Python',
  'SQL',
  'Python, Docker',
  'SQL',
  'Git',
  'SQL',
  'Python, SQL, Docker',
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
Work with a small, senior team.
Requirements: ${SKILLS_BY_TITLE[i % TITLES.length]}.`,
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
    .sort((a, b) => Date.parse(b.postedAt || b.createdAt) - Date.parse(a.postedAt || a.createdAt));
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

// Facets for the jobs visible in the scope, cross-filtered like the backend: each list applies
// every active filter except its own dimension. Cities are capped at 100.
export async function mockJobFacets({ scope = 'scraped', ...filters } = {}) {
  await delayAndAuthorize();
  const jobs = scope === 'pasted' ? storedJobs : scrapedJobs();
  // Values of `field` from jobs/rows that match every filter except `field`'s own.
  const values = (field) => {
    const rest = { ...filters, [field]: undefined };
    return jobs
      .filter((job) => matchesFilters(job, rest))
      .map((job) => {
        if (field === 'category') return [job.category];
        return (job.locations ?? [])
          .filter((row) => matchesPlace({ locations: [row] }, rest))
          .map((row) => row[field]);
      });
  };
  return {
    categories: tally(values('category')),
    countries: tally(values('country')),
    states: tally(values('state')),
    cities: tally(values('city')).slice(0, 100),
  };
}
