import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './apiClient';
import { login } from './authService';

function jsonResponse(status, body) {
  return new Response(body === undefined ? '' : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiClient and authService', () => {
  let fetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts credentials as JSON to /auth/login and returns the token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { token: 'jwt-token' }));

    await expect(login('jane@example.com', 'secret')).resolves.toBe('jwt-token');

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/auth\/login$/);
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(options.body)).toEqual({ email: 'jane@example.com', password: 'secret' });
  });

  it.each([400, 401, 429, 500])('throws an ApiError carrying status %i', async (status) => {
    fetchMock.mockResolvedValue(jsonResponse(status, { message: 'nope' }));

    const error = await login('jane@example.com', 'secret').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(status);
    expect(error.data).toEqual({ message: 'nope' });
  });

  it('turns a network failure into status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await request('/anything').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });

  it('handles an empty error body', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 503 }));

    const error = await request('/anything').catch((e) => e);
    expect(error.status).toBe(503);
    expect(error.data).toBeNull();
  });

  it('throws when a successful response has no token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await expect(login('jane@example.com', 'secret')).rejects.toThrow(/token/);
  });
});
