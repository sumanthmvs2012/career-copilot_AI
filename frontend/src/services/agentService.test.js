import { beforeEach, describe, expect, it, vi } from 'vitest';
import { isMockAgent, runAgent } from './agentService';
import { mockRunAgent } from './mockAgent';
import { post } from './apiClient';
import { getToken, setToken } from '../utils/authToken';

vi.mock('./apiClient', async (importOriginal) => ({
  ...(await importOriginal()),
  post: vi.fn(),
}));

describe('agentService', () => {
  beforeEach(() => {
    vi.mocked(post).mockReset();
  });

  it('runs the agent with POST /agent/run', async () => {
    vi.mocked(post).mockResolvedValue({ considered: 5, draftsCreated: 2 });
    expect(isMockAgent()).toBe(false);
    expect(await runAgent()).toEqual({ considered: 5, draftsCreated: 2 });
    expect(post).toHaveBeenCalledWith('/agent/run');
  });

  it('passes 404 and 409 through', async () => {
    vi.mocked(post).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 409 }));
    await expect(runAgent()).rejects.toMatchObject({ status: 409 });
  });

  it('clears the token on 401', async () => {
    setToken('jwt');
    vi.mocked(post).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 401 }));
    await expect(runAgent()).rejects.toMatchObject({ status: 401 });
    expect(getToken()).toBeNull();
  });

  it('the mock returns counts', async () => {
    vi.useFakeTimers();
    const promise = mockRunAgent();
    await vi.advanceTimersByTimeAsync(1000);
    expect(await promise).toEqual({ considered: 12, draftsCreated: 3 });
    vi.useRealTimers();
  });
});
