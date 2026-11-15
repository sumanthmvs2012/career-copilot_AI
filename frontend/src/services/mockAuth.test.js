import { afterEach, describe, expect, it, vi } from 'vitest';
import { login } from './authService';
import { DEMO_EMAIL, DEMO_PASSWORD, isMockAuthEnabled } from './mockAuth';

describe('mock auth', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('is off unless the flag is set', () => {
    vi.stubEnv('VITE_USE_MOCK_AUTH', 'false');
    expect(isMockAuthEnabled()).toBe(false);
  });

  it('is never on in a production build', () => {
    vi.stubEnv('VITE_USE_MOCK_AUTH', 'true');
    vi.stubEnv('DEV', false);
    expect(isMockAuthEnabled()).toBe(false);
  });

  it('returns a token for the demo account without calling the network', async () => {
    vi.stubEnv('VITE_USE_MOCK_AUTH', 'true');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(login(DEMO_EMAIL, DEMO_PASSWORD)).resolves.toMatch(/^mock-token-/);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses a wrong password with a 401', async () => {
    vi.stubEnv('VITE_USE_MOCK_AUTH', 'true');

    const error = await login(DEMO_EMAIL, 'wrong').catch((e) => e);
    expect(error.status).toBe(401);
  });
});
