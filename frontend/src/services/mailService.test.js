import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  disconnectMail,
  dismissReminder,
  getApplicationEmails,
  getMailConnection,
  getReminders,
  isMockMail,
  startMailConnection,
  syncMail,
} from './mailService';
import { resetMockMail } from './mockMail';
import { del, get, post, put } from './apiClient';
import { getToken, setToken } from '../utils/authToken';

vi.mock('./apiClient', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

describe('mailService (real API)', () => {
  beforeEach(() => {
    for (const fn of [get, post, put, del]) vi.mocked(fn).mockReset();
  });

  it('gets the connection with GET /mail-connections', async () => {
    vi.mocked(get).mockResolvedValue({ configured: true, connection: null });
    expect(isMockMail()).toBe(false);
    expect(await getMailConnection()).toEqual({ configured: true, connection: null });
    expect(get).toHaveBeenCalledWith('/mail-connections');
  });

  it('starts OAuth with POST /mail-connections and returns authUrl', async () => {
    vi.mocked(post).mockResolvedValue({ authUrl: 'https://accounts.example/auth' });
    expect(await startMailConnection()).toEqual({ authUrl: 'https://accounts.example/auth' });
    expect(post).toHaveBeenCalledWith('/mail-connections');
  });

  it('disconnects with DELETE /mail-connections/{id}', async () => {
    vi.mocked(del).mockResolvedValue(null);
    await disconnectMail(3);
    expect(del).toHaveBeenCalledWith('/mail-connections/3');
  });

  it('syncs with POST /mail-connections/sync', async () => {
    vi.mocked(post).mockResolvedValue({ checked: 4, newReplies: 1 });
    expect(await syncMail()).toEqual({ checked: 4, newReplies: 1 });
    expect(post).toHaveBeenCalledWith('/mail-connections/sync');
  });

  it('lists reminders and dismisses with PUT {status: "dismissed"}', async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1 }]);
    expect(await getReminders()).toEqual([{ id: 1 }]);
    expect(get).toHaveBeenCalledWith('/reminders');
    vi.mocked(put).mockResolvedValue(null);
    await dismissReminder(1);
    expect(put).toHaveBeenCalledWith('/reminders/1', { status: 'dismissed' });
  });

  it('gets application emails', async () => {
    vi.mocked(get).mockResolvedValue([]);
    await getApplicationEmails(9);
    expect(get).toHaveBeenCalledWith('/applications/9/emails');
  });

  it('passes 503 and 409 through and clears the token on 401', async () => {
    vi.mocked(post).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 503 }));
    await expect(startMailConnection()).rejects.toMatchObject({ status: 503 });
    vi.mocked(post).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 409 }));
    await expect(startMailConnection()).rejects.toMatchObject({ status: 409 });
    setToken('jwt');
    vi.mocked(get).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 401 }));
    await expect(getReminders()).rejects.toMatchObject({ status: 401 });
    expect(getToken()).toBeNull();
  });
});

describe('mailService (mock)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK_MAIL', 'true');
    resetMockMail();
    vi.mocked(get).mockReset();
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('connects, syncs and disconnects in memory', async () => {
    expect(isMockMail()).toBe(true);
    expect((await getMailConnection()).connection).toBeNull();
    await expect(syncMail()).rejects.toMatchObject({ status: 404 });
    const { authUrl } = await startMailConnection();
    expect(authUrl).toContain('mail=connected');
    const status = await getMailConnection();
    expect(status.connection).toMatchObject({ provider: 'gmail' });
    await expect(startMailConnection()).rejects.toMatchObject({ status: 409 });
    expect(await syncMail()).toEqual({
      checked: expect.any(Number),
      newReplies: expect.any(Number),
    });
    await disconnectMail(status.connection.id);
    expect((await getMailConnection()).connection).toBeNull();
    expect(get).not.toHaveBeenCalled();
  });

  it('dismisses a reminder and rejects an unknown one', async () => {
    const [first] = await getReminders();
    expect(first).toMatchObject({
      applicationId: expect.any(Number),
      jobTitle: expect.any(String),
    });
    await dismissReminder(first.id);
    expect(await getReminders()).toEqual([]);
    await expect(dismissReminder(999)).rejects.toMatchObject({ status: 404 });
  });

  it('returns application emails with the contract shape', async () => {
    const [email] = await getApplicationEmails(2);
    expect(Object.keys(email).sort()).toEqual(
      ['classification', 'id', 'occurredAt', 'status', 'subject', 'type'].sort(),
    );
  });
});
