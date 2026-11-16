import { ApiError } from './apiClient';

// Dev-only in-memory mailbox (VITE_USE_MOCK_MAIL=true). Lost on reload. "Connecting" flips the
// state directly and returns a same-site authUrl instead of redirecting to Google.
let connected = false;
let reminders = null;

export function resetMockMail() {
  connected = false;
  reminders = null;
}

function seedReminders() {
  if (!reminders) {
    reminders = [
      {
        id: 1,
        applicationId: 2,
        jobTitle: 'Backend Developer',
        company: 'Globex Labs',
        dueAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
      },
    ];
  }
  return reminders;
}

export function mockGetMailConnection() {
  return Promise.resolve({
    configured: true,
    connection: connected
      ? { id: 1, provider: 'gmail', createdAt: new Date().toISOString() }
      : null,
  });
}

export function mockStartMailConnection() {
  if (connected) return Promise.reject(new ApiError('Request failed with status 409', 409, null));
  connected = true;
  return Promise.resolve({ authUrl: '/dashboard?mail=connected' });
}

export function mockDisconnectMail() {
  connected = false;
  return Promise.resolve(null);
}

export function mockSyncMail() {
  if (!connected) return Promise.reject(new ApiError('Request failed with status 404', 404, null));
  return Promise.resolve({ checked: 12, newReplies: 1 });
}

export function mockGetReminders() {
  return Promise.resolve(seedReminders().map((r) => ({ ...r })));
}

export function mockDismissReminder(id) {
  const list = seedReminders();
  if (!list.some((r) => r.id === id)) {
    return Promise.reject(new ApiError('Request failed with status 404', 404, null));
  }
  reminders = list.filter((r) => r.id !== id);
  return Promise.resolve(null);
}

export function mockGetApplicationEmails(applicationId) {
  return Promise.resolve([
    {
      id: 1,
      type: 'employer_reply',
      classification: 'interview',
      subject: `Re: your application ${applicationId}`,
      occurredAt: new Date().toISOString(),
      status: 'new',
    },
  ]);
}
