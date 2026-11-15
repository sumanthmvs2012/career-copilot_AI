import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, get, put } from './apiClient';
import { listApplications } from './applicationsService';
import { mockGetProfile, mockUpdateProfile, resetMockProfile } from './mockProfile';
import { getProfile, updateProfile } from './profileService';
import { clearToken, getToken, setToken } from '../utils/authToken';

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PROFILE = {
  id: 1,
  fullName: 'Jane Doe',
  headline: 'Engineer',
  location: 'Berlin',
  remoteOk: true,
  autoSubmitEnabled: false,
};

describe('profileService', () => {
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

  it('GETs /profile with the bearer token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, PROFILE));

    await expect(getProfile()).resolves.toEqual(PROFILE);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/profile$/);
    expect(options.method).toBe('GET');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('PUTs a trimmed body to /profile', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, PROFILE));

    await updateProfile({
      fullName: '  Jane Doe ',
      headline: ' Engineer',
      location: 'Berlin ',
      remoteOk: true,
      autoSubmitEnabled: false,
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/profile$/);
    expect(options.method).toBe('PUT');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
    expect(JSON.parse(options.body)).toEqual({
      fullName: 'Jane Doe',
      headline: 'Engineer',
      location: 'Berlin',
      remoteOk: true,
      autoSubmitEnabled: false,
    });
  });

  it('surfaces a 404 as an ApiError and keeps the token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, { status: 404, message: 'No profile' }));

    const error = await getProfile().catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(getToken()).toBe('jwt-token');
  });

  it('keeps fieldErrors from a 400', async () => {
    const body = { status: 400, message: 'bad', fieldErrors: { fullName: 'required' } };
    fetchMock.mockResolvedValue(jsonResponse(400, body));

    const error = await updateProfile({
      fullName: 'x',
      headline: '',
      location: '',
    }).catch((e) => e);
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors).toEqual({ fullName: 'required' });
  });

  it('clears the token on a 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'expired' }));

    const error = await getProfile().catch((e) => e);
    expect(error.status).toBe(401);
    expect(getToken()).toBeNull();
  });

  it('does not send Authorization when there is no token', async () => {
    clearToken();
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await get('/anything');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('put helper sends a JSON body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await put('/anything', { a: 1 });
    const options = fetchMock.mock.calls[0][1];
    expect(options.method).toBe('PUT');
    expect(options.headers['Content-Type']).toBe('application/json');
  });
});

describe('mockProfile', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetMockProfile();
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

  it('returns 404 until a profile is saved, then returns it', async () => {
    expect((await settle(mockGetProfile())).status).toBe(404);

    const saved = await settle(
      mockUpdateProfile({ fullName: ' Jane ', headline: 'Dev', remoteOk: true }),
    );
    expect(saved).toMatchObject({ fullName: 'Jane', headline: 'Dev', remoteOk: true });
    expect(saved.autoSubmitEnabled).toBe(false);

    expect(await settle(mockGetProfile())).toMatchObject({ fullName: 'Jane' });
  });

  it('rejects an empty full name with fieldErrors', async () => {
    const error = await settle(mockUpdateProfile({ fullName: ' ' }));
    expect(error.status).toBe(400);
    expect(error.data.fieldErrors.fullName).toBeTruthy();
  });

  it('returns 401 without a token', async () => {
    clearToken();
    expect((await settle(mockGetProfile())).status).toBe(401);
  });
});

describe('applicationsService', () => {
  it('returns an empty list until the API exists', async () => {
    await expect(listApplications()).resolves.toEqual([]);
  });
});
