import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const KEY = 'authToken';

// Fresh module per test so the in-memory variable starts empty.
async function loadModule() {
  vi.resetModules();
  return import('./authToken');
}

describe('authToken', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns null when no token is stored', async () => {
    const { getToken } = await loadModule();
    expect(getToken()).toBeNull();
  });

  it('sets the token in memory and mirrors it to sessionStorage', async () => {
    const { getToken, setToken } = await loadModule();
    setToken('abc');
    expect(getToken()).toBe('abc');
    expect(window.sessionStorage.getItem(KEY)).toBe('abc');
  });

  it('reads from memory first', async () => {
    const { getToken, setToken } = await loadModule();
    setToken('abc');
    window.sessionStorage.setItem(KEY, 'other');
    expect(getToken()).toBe('abc');
  });

  it('clears the token from memory and sessionStorage', async () => {
    const { getToken, setToken, clearToken } = await loadModule();
    setToken('abc');
    clearToken();
    expect(getToken()).toBeNull();
    expect(window.sessionStorage.getItem(KEY)).toBeNull();
  });

  it('rehydrates memory from sessionStorage after a reload', async () => {
    window.sessionStorage.setItem(KEY, 'persisted');
    const { getToken } = await loadModule();
    expect(getToken()).toBe('persisted');

    // Memory now holds it, even if storage is later emptied.
    window.sessionStorage.removeItem(KEY);
    expect(getToken()).toBe('persisted');
  });

  it('keeps working in memory when sessionStorage throws', async () => {
    const { getToken, setToken, clearToken } = await loadModule();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(() => setToken('abc')).not.toThrow();
    expect(getToken()).toBe('abc');
    expect(() => clearToken()).not.toThrow();
    expect(getToken()).toBeNull();
  });
});
