import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiClient';
import { getJobFacets, getStoredMatch, listJobs, matchJob, matchPastedJob } from './jobService';
import {
  mockGetStoredMatch,
  mockJobFacets,
  mockListJobs,
  mockMatchJobById,
  mockMatchPastedJob,
  resetMockJobs,
} from './mockJobs';
import { clearToken, getToken, setToken } from '../utils/authToken';

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const DESCRIPTION = 'Build and run backend services in Java.';

describe('jobService', () => {
  let fetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    setToken('jwt-token');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearToken();
  });

  it('POSTs a trimmed body to /jobs/match and omits blank optionals', async () => {
    const payload = { job: { id: 1 }, match: null, message: 'x' };
    fetchMock.mockResolvedValue(jsonResponse(201, payload));

    await expect(
      matchPastedJob({
        title: '  Engineer ',
        companyName: '',
        location: '   ',
        remoteType: 'hybrid',
        sourceUrl: '',
        description: `  ${DESCRIPTION} `,
      }),
    ).resolves.toEqual(payload);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/jobs\/match$/);
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
    expect(JSON.parse(options.body)).toEqual({
      title: 'Engineer',
      remoteType: 'hybrid',
      description: DESCRIPTION,
    });
  });

  it('POSTs /jobs/{id}/match with the bearer token and resolves the result', async () => {
    const result = { jobId: 7, score: 50 };
    fetchMock.mockResolvedValue(jsonResponse(200, result));
    await expect(matchJob(7)).resolves.toEqual(result);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/jobs\/7\/match$/);
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('surfaces the 409 message when there is no resume', async () => {
    const body = { status: 409, message: 'Upload a resume first to see your match.' };
    fetchMock.mockResolvedValue(jsonResponse(409, body));
    const error = await matchJob(7).catch((e) => e);
    expect(error.status).toBe(409);
    expect(error.data.message).toBe(body.message);
  });

  it('GETs the stored match and maps a 404 to null', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { jobId: 7, score: 10 }));
    await expect(getStoredMatch(7)).resolves.toEqual({ jobId: 7, score: 10 });
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/jobs\/7\/match$/);
    expect(fetchMock.mock.calls[0][1].method).toBe('GET');

    fetchMock.mockResolvedValueOnce(jsonResponse(404, { status: 404, message: 'No match.' }));
    await expect(getStoredMatch(7)).resolves.toBeNull();
  });

  it('rethrows other errors from the stored match and clears the token on 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse(500, { status: 500, message: 'boom' }));
    expect((await getStoredMatch(7).catch((e) => e)).status).toBe(500);
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'expired' }));
    expect((await getStoredMatch(7).catch((e) => e)).status).toBe(401);
    expect(getToken()).toBeNull();
  });

  it('GETs /jobs with the bearer token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []));

    await expect(listJobs()).resolves.toEqual([]);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/jobs$/);
    expect(options.method).toBe('GET');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('sends scope and limit as query params and omits them when absent', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, []));
    await listJobs({ scope: 'scraped', limit: 10 });
    await listJobs({ scope: 'pasted' });
    await listJobs({});
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/jobs\?scope=scraped&limit=10$/);
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs\?scope=pasted$/);
    expect(fetchMock.mock.calls[2][0]).toMatch(/\/jobs$/);
  });

  it('sends trimmed, encoded filters and omits empty ones', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, []));
    await listJobs({
      scope: 'scraped',
      limit: 20,
      offset: 40,
      q: '  c++ & dev ',
      source: 'lever',
      remoteType: 'remote',
      location: ' São Paulo ',
      postedWithin: 7,
    });
    await listJobs({ q: '   ', source: '', remoteType: '', location: '', offset: 0 });

    const params = new URL(fetchMock.mock.calls[0][0]).searchParams;
    expect(Object.fromEntries(params)).toEqual({
      scope: 'scraped',
      limit: '20',
      offset: '40',
      q: 'c++ & dev',
      source: 'lever',
      remoteType: 'remote',
      location: 'São Paulo',
      postedWithin: '7',
    });
    expect(fetchMock.mock.calls[0][0]).toContain('q=c%2B%2B+%26+dev');
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs$/);
  });

  it('sends category, country and state and omits empty ones', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, []));
    await listJobs({
      scope: 'scraped',
      category: ' Data & ML ',
      country: 'United States',
      state: 'CA',
    });
    await listJobs({ category: '', country: '  ', state: '' });
    const params = new URL(fetchMock.mock.calls[0][0]).searchParams;
    expect(Object.fromEntries(params)).toEqual({
      scope: 'scraped',
      category: 'Data & ML',
      country: 'United States',
      state: 'CA',
    });
    expect(fetchMock.mock.calls[0][0]).toContain('category=Data+%26+ML');
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs$/);
  });

  it('GETs /jobs/facets with scope scraped by default and sends every active filter', async () => {
    const facets = { categories: [], countries: [], states: [] };
    fetchMock.mockImplementation(async () => jsonResponse(200, facets));
    await expect(getJobFacets()).resolves.toEqual(facets);
    await getJobFacets({ scope: 'pasted', country: ' Canada ', state: '' });
    await getJobFacets({
      scope: 'scraped',
      q: ' engineer ',
      category: 'Data & ML',
      source: 'lever',
      remoteType: 'remote',
      location: 'Berlin',
      postedWithin: 7,
      country: 'United States',
      state: 'CA',
    });

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/jobs\/facets\?scope=scraped$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-token');
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs\/facets\?scope=pasted&country=Canada$/);
    const url = new URL(fetchMock.mock.calls[2][0]);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      scope: 'scraped',
      q: 'engineer',
      category: 'Data & ML',
      source: 'lever',
      remoteType: 'remote',
      location: 'Berlin',
      postedWithin: '7',
      country: 'United States',
      state: 'CA',
    });
  });

  it('surfaces a 404 and keeps the token', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(404, { status: 404, message: 'Create your profile first.' }),
    );
    const error = await matchPastedJob({ description: DESCRIPTION }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(getToken()).toBe('jwt-token');
  });

  it('keeps fieldErrors from a 400', async () => {
    const body = { status: 400, message: 'bad', fieldErrors: { description: 'too short' } };
    fetchMock.mockResolvedValue(jsonResponse(400, body));
    const error = await matchPastedJob({ description: DESCRIPTION }).catch((e) => e);
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors).toEqual({ description: 'too short' });
  });

  it.each([
    ['matchPastedJob', () => matchPastedJob({ description: DESCRIPTION })],
    ['listJobs', () => listJobs()],
    ['getJobFacets', () => getJobFacets()],
  ])('%s clears the token on a 401', async (_name, call) => {
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'expired' }));
    const error = await call().catch((e) => e);
    expect(error.status).toBe(401);
    expect(getToken()).toBeNull();
  });
});

describe('mockJobs', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetMockJobs();
    setToken('mock-token');
  });

  afterEach(() => {
    vi.useRealTimers();
    clearToken();
  });

  async function settle(promise) {
    const result = promise.catch((e) => e);
    await vi.runAllTimersAsync();
    return result;
  }

  it('saves a job with a null match and lists newest first', async () => {
    expect(await settle(mockListJobs({ scope: 'pasted' }))).toEqual([]);

    const first = await settle(mockMatchPastedJob({ title: 'A', description: DESCRIPTION }));
    expect(first.match).toMatchObject({ jobId: first.job.id, matchedSkills: ['Java'] });
    expect(first.message).toBe('Your job is saved and matched.');
    expect(first.job).toMatchObject({ title: 'A', source: 'manual' });

    await settle(mockMatchPastedJob({ title: 'B', description: DESCRIPTION }));
    const rows = await settle(mockListJobs({ scope: 'pasted' }));
    expect(rows.map((j) => j.title)).toEqual(['B', 'A']);
  });

  it('matches a scraped job, stores it, and 404s/409s like the backend', async () => {
    expect((await settle(mockGetStoredMatch(9001))).status).toBe(404);
    const match = await settle(mockMatchJobById(9001));
    expect(match).toMatchObject({ jobId: 9001, resumeId: 1 });
    expect(match.score).toBeGreaterThanOrEqual(0);
    expect(match.score).toBeLessThanOrEqual(100);
    expect(await settle(mockGetStoredMatch(9001))).toMatchObject({ jobId: 9001 });
    expect((await settle(mockMatchJobById(123456))).status).toBe(404);
  });

  it('serves scraped-style jobs for scope scraped and pasted ones for pasted', async () => {
    await settle(mockMatchPastedJob({ title: 'Mine', description: DESCRIPTION }));

    const scraped = await settle(mockListJobs({ scope: 'scraped' }));
    expect(scraped.length).toBeGreaterThan(1);
    for (const job of scraped) {
      expect(['greenhouse', 'lever', 'ashby', 'workable', 'smartrecruiters']).toContain(job.source);
      expect(job.postedAt).toBeTruthy();
      expect(job.sourceUrl).toMatch(/^https:/);
    }
    expect(await settle(mockListJobs({ scope: 'scraped', limit: 1 }))).toHaveLength(1);

    const pasted = await settle(mockListJobs({ scope: 'pasted' }));
    expect(pasted.map((j) => j.title)).toEqual(['Mine']);
  });

  it('has 30+ scraped jobs and pages them with offset and limit', async () => {
    const all = await settle(mockListJobs({ scope: 'scraped' }));
    expect(all.length).toBeGreaterThanOrEqual(30);

    const page1 = await settle(mockListJobs({ scope: 'scraped', limit: 20 }));
    const page2 = await settle(mockListJobs({ scope: 'scraped', limit: 20, offset: 20 }));
    expect(page1).toHaveLength(20);
    expect(page2.length).toBe(all.length - 20);
    expect([...page1, ...page2].map((j) => j.id)).toEqual(all.map((j) => j.id));
  });

  it('filters in memory by q, source, remoteType, location and postedWithin', async () => {
    const all = await settle(mockListJobs({ scope: 'scraped' }));

    const bySource = await settle(mockListJobs({ scope: 'scraped', source: 'lever' }));
    expect(bySource.length).toBeGreaterThan(0);
    expect(bySource.every((j) => j.source === 'lever')).toBe(true);

    const byRemote = await settle(mockListJobs({ scope: 'scraped', remoteType: 'hybrid' }));
    expect(byRemote.length).toBeGreaterThan(0);
    expect(byRemote.every((j) => j.remoteType === 'hybrid')).toBe(true);

    const byQ = await settle(mockListJobs({ scope: 'scraped', q: 'STRIPE' }));
    expect(byQ.length).toBeGreaterThan(0);
    expect(byQ.every((j) => /stripe/i.test(`${j.title} ${j.companyName}`))).toBe(true);

    const byLocation = await settle(mockListJobs({ scope: 'scraped', location: 'dublin' }));
    expect(byLocation.length).toBeGreaterThan(0);
    expect(byLocation.every((j) => /dublin/i.test(j.location))).toBe(true);

    const recent = await settle(mockListJobs({ scope: 'scraped', postedWithin: 1 }));
    const week = await settle(mockListJobs({ scope: 'scraped', postedWithin: 7 }));
    expect(recent.length).toBeGreaterThan(0);
    expect(week.length).toBeGreaterThan(recent.length);
    expect(week.length).toBeLessThan(all.length);

    const combined = await settle(
      mockListJobs({ scope: 'scraped', source: 'lever', remoteType: 'hybrid', q: 'zzz-none' }),
    );
    expect(combined).toEqual([]);
  });

  it('gives scraped jobs categories and structured locations', async () => {
    const jobs = await settle(mockListJobs({ scope: 'scraped' }));
    expect(jobs.every((j) => j.category)).toBe(true);
    const countries = new Set(jobs.flatMap((j) => j.locations.map((l) => l.country)));
    for (const country of [
      'United States',
      'United Kingdom',
      'Canada',
      'India',
      'Singapore',
      'Germany',
    ]) {
      expect(countries).toContain(country);
    }
    expect(jobs.some((j) => j.locations.length === 0 && /remote/i.test(j.location))).toBe(true);
  });

  it('filters by category, country and state (both on the same location)', async () => {
    const byCategory = await settle(mockListJobs({ scope: 'scraped', category: 'design' }));
    expect(byCategory.length).toBeGreaterThan(0);
    expect(byCategory.every((j) => j.category === 'Design')).toBe(true);

    const byCountry = await settle(mockListJobs({ scope: 'scraped', country: 'Canada' }));
    expect(byCountry.length).toBeGreaterThan(0);
    expect(byCountry.every((j) => j.locations.some((l) => l.country === 'Canada'))).toBe(true);

    const byState = await settle(
      mockListJobs({ scope: 'scraped', country: 'United States', state: 'NY' }),
    );
    expect(byState.length).toBeGreaterThan(0);
    expect(
      byState.every((j) =>
        j.locations.some((l) => l.country === 'United States' && l.state === 'NY'),
      ),
    ).toBe(true);

    expect(
      await settle(mockListJobs({ scope: 'scraped', country: 'Canada', state: 'CA' })),
    ).toEqual([]);
  });

  it('builds facets with counts, most common first, and cascades states', async () => {
    const all = await settle(mockListJobs({ scope: 'scraped' }));
    const facets = await settle(mockJobFacets({ scope: 'scraped' }));
    const total = facets.categories.reduce((sum, c) => sum + c.count, 0);
    expect(total).toBe(all.length);
    const counts = facets.categories.map((c) => c.count);
    expect(counts).toEqual([...counts].sort((a, b) => b - a));
    const us = facets.countries.find((c) => c.value === 'United States');
    expect(us.count).toBe(
      all.filter((j) => j.locations.some((l) => l.country === 'United States')).length,
    );

    const usFacets = await settle(mockJobFacets({ scope: 'scraped', country: 'United States' }));
    const stateNames = usFacets.states.map((s) => s.value);
    expect(stateNames).toEqual(expect.arrayContaining(['CA', 'NY']));
    expect(stateNames).not.toContain('ON');
    expect(usFacets.cities).toBeUndefined();

    const ukFacets = await settle(mockJobFacets({ scope: 'scraped', country: 'United Kingdom' }));
    expect(ukFacets.states).toEqual([]);
  });

  it('cross-filters the mock facets: each list ignores only its own dimension', async () => {
    const all = await settle(mockJobFacets({ scope: 'scraped' }));
    const design = await settle(mockJobFacets({ scope: 'scraped', category: 'Design' }));
    expect(design.categories).toEqual(all.categories);
    const designJobs = await settle(mockListJobs({ scope: 'scraped', category: 'Design' }));
    const designCountries = new Set(designJobs.flatMap((j) => j.locations.map((l) => l.country)));
    expect(new Set(design.countries.map((c) => c.value))).toEqual(designCountries);
    expect(designCountries.size).toBeLessThan(all.countries.length);

    const canada = await settle(mockJobFacets({ scope: 'scraped', country: 'Canada' }));
    expect(canada.states.map((s) => s.value)).toEqual(['ON']);
    expect(canada.countries.length).toBe(all.countries.length);

    const none = await settle(mockJobFacets({ scope: 'scraped', q: 'no-such-title' }));
    expect(none).toEqual({ categories: [], countries: [], states: [] });
  });

  it('has no facets for an empty pasted scope and requires a token', async () => {
    expect(await settle(mockJobFacets({ scope: 'pasted' }))).toEqual({
      categories: [],
      countries: [],
      states: [],
    });
    clearToken();
    expect((await settle(mockJobFacets())).status).toBe(401);
  });

  it('rejects a short description with fieldErrors', async () => {
    const error = await settle(mockMatchPastedJob({ description: 'short' }));
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors.description).toBeTruthy();
  });

  it('returns 401 without a token', async () => {
    clearToken();
    expect((await settle(mockListJobs())).status).toBe(401);
  });
});
