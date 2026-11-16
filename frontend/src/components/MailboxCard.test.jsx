import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MailboxCard from './MailboxCard';
import {
  disconnectMail,
  dismissReminder,
  getMailConnection,
  getReminders,
  startMailConnection,
  syncMail,
} from '../services/mailService';

vi.mock('../services/mailService', () => ({
  getMailConnection: vi.fn(),
  startMailConnection: vi.fn(),
  disconnectMail: vi.fn(),
  syncMail: vi.fn(),
  getReminders: vi.fn(),
  dismissReminder: vi.fn(),
}));

const CONNECTED = {
  configured: true,
  connection: { id: 3, provider: 'gmail', createdAt: '2026-11-01T00:00:00Z' },
};
const REMINDER = {
  id: 11,
  applicationId: 2,
  jobTitle: 'Backend Developer',
  company: 'Globex',
  dueAt: '2026-11-10T00:00:00Z',
};
const apiError = (status) => Object.assign(new Error(`status ${status}`), { status });

function setup(props = {}) {
  const user = userEvent.setup();
  const handlers = { onUnauthorized: vi.fn(), onSynced: vi.fn() };
  render(<MailboxCard {...handlers} {...props} />);
  return { user, ...handlers };
}

describe('MailboxCard', () => {
  beforeEach(() => {
    vi.mocked(getMailConnection)
      .mockReset()
      .mockResolvedValue({ configured: true, connection: null });
    vi.mocked(getReminders).mockReset().mockResolvedValue([]);
    vi.mocked(startMailConnection).mockReset();
    vi.mocked(disconnectMail).mockReset();
    vi.mocked(syncMail).mockReset();
    vi.mocked(dismissReminder).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows loading first', () => {
    vi.mocked(getMailConnection).mockReturnValue(new Promise(() => {}));
    setup();
    expect(screen.getByText('Loading mailbox…')).toBeInTheDocument();
  });

  it('says so when the server has no mailbox setup', async () => {
    vi.mocked(getMailConnection).mockResolvedValue({ configured: false, connection: null });
    setup();
    expect(
      await screen.findByText('Mailbox connection is not set up on this server.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Connect Gmail/ })).not.toBeInTheDocument();
  });

  it('offers a read-only connect with a privacy note and redirects to authUrl', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    vi.mocked(startMailConnection).mockResolvedValue({ authUrl: 'https://accounts.example/auth' });
    const { user } = setup();

    expect(await screen.findByText(/only the subject, time and type/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Connect Gmail (read-only)' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://accounts.example/auth'));
    vi.unstubAllGlobals();
  });

  it('shows the not-set-up message when connecting returns 503', async () => {
    vi.mocked(startMailConnection).mockRejectedValue(apiError(503));
    const { user } = setup();
    await user.click(await screen.findByRole('button', { name: 'Connect Gmail (read-only)' }));
    expect(
      await screen.findByText('Mailbox connection is not set up on this server.'),
    ).toBeInTheDocument();
  });

  it('shows a generic error when connecting fails', async () => {
    vi.mocked(startMailConnection).mockRejectedValue(apiError(500));
    const { user } = setup();
    await user.click(await screen.findByRole('button', { name: 'Connect Gmail (read-only)' }));
    expect(await screen.findByText(/could not start the connection/)).toBeInTheDocument();
    expect(console.error).toHaveBeenCalledWith('Starting mailbox connection failed', {
      status: 500,
    });
  });

  it('reloads the state when connecting returns 409', async () => {
    vi.mocked(getMailConnection)
      .mockResolvedValueOnce({ configured: true, connection: null })
      .mockResolvedValueOnce(CONNECTED);
    vi.mocked(startMailConnection).mockRejectedValue(apiError(409));
    const { user } = setup();
    await user.click(await screen.findByRole('button', { name: 'Connect Gmail (read-only)' }));
    expect(await screen.findByText('Connected')).toBeInTheDocument();
  });

  it('shows Connected, and Check now reports new replies', async () => {
    vi.mocked(getMailConnection).mockResolvedValue(CONNECTED);
    vi.mocked(syncMail).mockResolvedValue({ checked: 9, newReplies: 1 });
    const { user, onSynced } = setup();

    expect(await screen.findByText('Connected')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Check now' }));

    expect(await screen.findByText('1 new reply')).toBeInTheDocument();
    expect(onSynced).toHaveBeenCalled();
  });

  it('reports zero replies and sync errors', async () => {
    vi.mocked(getMailConnection).mockResolvedValue(CONNECTED);
    vi.mocked(syncMail)
      .mockResolvedValueOnce({ checked: 2, newReplies: 0 })
      .mockRejectedValueOnce(apiError(500));
    const { user } = setup();
    await user.click(await screen.findByRole('button', { name: 'Check now' }));
    expect(await screen.findByText('0 new replies')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Check now' }));
    expect(await screen.findByText(/could not check your mailbox/)).toBeInTheDocument();
  });

  it('disconnects in two steps', async () => {
    vi.mocked(getMailConnection).mockResolvedValue(CONNECTED);
    vi.mocked(disconnectMail).mockResolvedValue(null);
    const { user } = setup();

    await user.click(await screen.findByRole('button', { name: 'Disconnect' }));
    expect(disconnectMail).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Yes, disconnect' }));

    expect(disconnectMail).toHaveBeenCalledWith(3);
    expect(await screen.findByText('Mailbox disconnected.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Connect Gmail (read-only)' })).toBeInTheDocument();
  });

  it('cancels a disconnect', async () => {
    vi.mocked(getMailConnection).mockResolvedValue(CONNECTED);
    const { user } = setup();
    await user.click(await screen.findByRole('button', { name: 'Disconnect' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(disconnectMail).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Disconnect' })).toBeInTheDocument();
  });

  it('lists reminders and dismisses one', async () => {
    vi.mocked(getReminders).mockResolvedValue([REMINDER]);
    vi.mocked(dismissReminder).mockResolvedValue(null);
    const { user } = setup();

    expect(await screen.findByText('Backend Developer')).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', { name: 'Dismiss reminder for Backend Developer' }),
    );

    expect(dismissReminder).toHaveBeenCalledWith(11);
    await waitFor(() => expect(screen.queryByText('Backend Developer')).not.toBeInTheDocument());
    expect(screen.getByText('No reminders right now.')).toBeInTheDocument();
  });

  it('shows errors for the mailbox and reminders separately, with retry', async () => {
    vi.mocked(getMailConnection).mockRejectedValueOnce(apiError(500));
    vi.mocked(getReminders).mockRejectedValueOnce(apiError(500));
    const { user } = setup();

    expect(await screen.findByText(/could not load your mailbox/)).toBeInTheDocument();
    expect(await screen.findByText(/could not load your reminders/)).toBeInTheDocument();
    const retries = screen.getAllByRole('button', { name: 'Try again' });
    await user.click(retries[0]);
    expect(await screen.findByRole('button', { name: 'Connect Gmail (read-only)' })).toBeVisible();
  });

  it('renders the redirect notice', async () => {
    setup({ notice: { kind: 'success', text: 'Gmail is connected.' } });
    expect(await screen.findByText('Gmail is connected.')).toBeInTheDocument();
  });

  it('hands a 401 to onUnauthorized', async () => {
    vi.mocked(getMailConnection).mockRejectedValue(apiError(401));
    const { onUnauthorized } = setup();
    await waitFor(() => expect(onUnauthorized).toHaveBeenCalled());
  });
});
