import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiClient';
import { getJobFacets, listJobs, matchJob } from './jobService';
import { mockJobFacets, mockListJobs, mockMatchJob, resetMockJobs } from './mockJobs';
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
      matchJob({
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

  it('sends category, country, state and city and omits empty ones', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, []));
    await listJobs({
      scope: 'scraped',
      category: ' Data & ML ',
      country: 'United States',
      state: 'CA',
      city: 'San Francisco',
    });
    await listJobs({ category: '', country: '  ', state: '', city: '' });
    const params = new URL(fetchMock.mock.calls[0][0]).searchParams;
    expect(Object.fromEntries(params)).toEqual({
      scope: 'scraped',
      category: 'Data & ML',
      country: 'United States',
      state: 'CA',
      city: 'San Francisco',
    });
    expect(fetchMock.mock.calls[0][0]).toContain('category=Data+%26+ML');
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs$/);
  });

  it('GETs /jobs/facets with scope scraped by default and optional country and state', async () => {
    const facets = { categories: [], countries: [], states: [], cities: [] };
    fetchMock.mockImplementation(async () => jsonResponse(200, facets));
    await expect(getJobFacets()).resolves.toEqual(facets);
    await getJobFacets({ scope: 'pasted', country: ' Canada ', state: '' });
    await getJobFacets({ country: 'United States', state: 'CA' });

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/jobs\/facets\?scope=scraped$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-token');
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/jobs\/facets\?scope=pasted&country=Canada$/);
    expect(fetchMock.mock.calls[2][0]).toMatch(
      /\/jobs\/facets\?scope=scraped&country=United\+States&state=CA$/,
    );
  });

  it('surfaces a 404 and keeps the token', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(404, { status: 404, message: 'Create your profile first.' }),
    );
    const error = await matchJob({ description: DESCRIPTION }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(getToken()).toBe('jwt-token');
  });

  it('keeps fieldErrors from a 400', async () => {
    const body = { status: 400, message: 'bad', fieldErrors: { description: 'too short' } };
    fetchMock.mockResolvedValue(jsonResponse(400, body));
    const error = await matchJob({ description: DESCRIPTION }).catch((e) => e);
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors).toEqual({ description: 'too short' });
  });

  it.each([
    ['matchJob', () => matchJob({ description: DESCRIPTION })],
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

    const first = await settle(mockMatchJob({ title: 'A', description: DESCRIPTION }));
    expect(first.match).toBeNull();
    expect(first.message).toBe('Matching is not available yet; your job is saved.');
    expect(first.job).toMatchObject({ title: 'A', source: 'manual' });

    await settle(mockMatchJob({ title: 'B', description: DESCRIPTION }));
    const rows = await settle(mockListJobs({ scope: 'pasted' }));
    expect(rows.map((j) => j.title)).toEqual(['B', 'A']);
  });

  it('serves scraped-style jobs for scope scraped and pasted ones for pasted', async () => {
    await settle(mockMatchJob({ title: 'Mine', description: DESCRIPTION }));

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

  it('filters by category, country, state and city (all on the same location)', async () => {
    const byCategory = await settle(mockListJobs({ scope: 'scraped', category: 'design' }));
    expect(byCategory.length).toBeGreaterThan(0);
    expect(byCategory.every((j) => j.category === 'Design')).toBe(true);

    const byCountry = await settle(mockListJobs({ scope: 'scraped', country: 'Canada' }));
    expect(byCountry.length).toBeGreaterThan(0);
    expect(byCountry.every((j) => j.locations.some((l) => l.country === 'Canada'))).toBe(true);

    const byCity = await settle(
      mockListJobs({ scope: 'scraped', country: 'United States', state: 'NY', city: 'New York' }),
    );
    expect(byCity.length).toBeGreaterThan(0);
    expect(
      byCity.every((j) => j.locations.some((l) => l.state === 'NY' && l.city === 'New York')),
    ).toBe(true);

    expect(await settle(mockListJobs({ scope: 'scraped', country: 'Canada', state: 'CA' }))).toEqual(
      [],
    );
  });

  it('builds facets with counts, most common first, and cascades states and cities', async () => {
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
    expect(usFacets.cities.map((c) => c.value)).toContain('Seattle');

    const nyFacets = await settle(
      mockJobFacets({ scope: 'scraped', country: 'United States', state: 'NY' }),
    );
    expect(nyFacets.cities.map((c) => c.value)).toEqual(['New York']);

    const ukFacets = await settle(mockJobFacets({ scope: 'scraped', country: 'United Kingdom' }));
    expect(ukFacets.states).toEqual([]);
    expect(ukFacets.cities.map((c) => c.value)).toEqual(
      expect.arrayContaining(['London', 'Manchester']),
    );
  });

  it('has no facets for an empty pasted scope and requires a token', async () => {
    expect(await settle(mockJobFacets({ scope: 'pasted' }))).toEqual({
      categories: [],
      countries: [],
      states: [],
      cities: [],
    });
    clearToken();
    expect((await settle(mockJobFacets())).status).toBe(401);
  });

  it('rejects a short description with fieldErrors', async () => {
    const error = await settle(mockMatchJob({ description: 'short' }));
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors.description).toBeTruthy();
  });

  it('returns 401 without a token', async () => {
    clearToken();
    expect((await settle(mockListJobs())).status).toBe(401);
  });
});
