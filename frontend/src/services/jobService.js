import { get, post } from './apiClient';
import { clearToken } from '../utils/authToken';
import {
  isMockJobsEnabled,
  mockGetStoredMatch,
  mockJobFacets,
  mockListJobs,
  mockMatchJobById,
  mockMatchPastedJob,
} from './mockJobs';

// A 401 means the token is missing or expired, so drop it here; pages then redirect to /login.
async function withAuthHandling(call) {
  try {
    return await call();
  } catch (error) {
    if (error?.status === 401) clearToken();
    throw error;
  }
}

// Optional text fields are trimmed and omitted when blank, as the backend contract allows.
function optional(value) {
  const trimmed = (value ?? '').trim();
  return trimmed || undefined;
}

// Resolves {job, match, message}; match is null (with a message) when there is no base resume.
// Rejects with an ApiError: 404 means no profile yet, 400 carries data.fieldErrors.
export function matchPastedJob({
  title,
  companyName,
  location,
  remoteType,
  sourceUrl,
  description,
}) {
  const body = {
    title: optional(title),
    companyName: optional(companyName),
    description: description.trim(),
    location: optional(location),
    remoteType: optional(remoteType),
    sourceUrl: optional(sourceUrl),
  };
  return withAuthHandling(() =>
    isMockJobsEnabled() ? mockMatchPastedJob(body) : post('/jobs/match', body),
  );
}

// Resolves jobs, newest first; may be []. scope: 'pasted' (the user's own) or 'scraped'
// (omit for both). Filters follow ARCHITECTURE.md "Job filters". Empty values are never sent;
// text is trimmed; URLSearchParams does the URL encoding.
export function listJobs({
  scope,
  limit,
  offset,
  q,
  source,
  remoteType,
  location,
  postedWithin,
  category,
  country,
  state,
} = {}) {
  const filters = {
    scope: optional(scope),
    limit: limit ? String(limit) : undefined,
    offset: offset ? String(offset) : undefined,
    q: optional(q),
    source: optional(source),
    remoteType: optional(remoteType),
    location: optional(location),
    postedWithin: postedWithin ? String(postedWithin) : undefined,
    category: optional(category),
    country: optional(country),
    state: optional(state),
  };
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value);
  }
  const query = params.toString();
  return withAuthHandling(() =>
    isMockJobsEnabled()
      ? mockListJobs({ ...filters, limit, offset })
      : get(query ? `/jobs?${query}` : '/jobs'),
  );
}

// Resolves {categories, countries, states}, each [{value, count}] most common first.
// Accepts every filter listJobs does (except paging); each list is computed server-side with all
// active filters applied except its own dimension. Empty values are never sent.
export function getJobFacets({
  scope = 'scraped',
  q,
  category,
  source,
  remoteType,
  location,
  postedWithin,
  country,
  state,
} = {}) {
  const filters = {
    scope: optional(scope),
    q: optional(q),
    category: optional(category),
    source: optional(source),
    remoteType: optional(remoteType),
    location: optional(location),
    postedWithin: postedWithin ? String(postedWithin) : undefined,
    country: optional(country),
    state: optional(state),
  };
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value);
  }
  return withAuthHandling(() =>
    isMockJobsEnabled() ? mockJobFacets(filters) : get(`/jobs/facets?${params.toString()}`),
  );
}

// Resolves the match result for a job the caller can see, saving it (replaces any earlier one).
// Rejects with an ApiError: 409 means no base resume yet, 404 an unknown job or no profile.
export function matchJob(id) {
  return withAuthHandling(() =>
    isMockJobsEnabled() ? mockMatchJobById(id) : post(`/jobs/${encodeURIComponent(id)}/match`),
  );
}

// Resolves the stored match result for a job, or null when none exists yet (404).
export async function getStoredMatch(id) {
  try {
    return await withAuthHandling(() =>
      isMockJobsEnabled() ? mockGetStoredMatch(id) : get(`/jobs/${encodeURIComponent(id)}/match`),
    );
  } catch (error) {
    if (error?.status === 404) return null;
    throw error;
  }
}
