import { del, get, post, put } from './apiClient';
import { clearToken } from '../utils/authToken';
import {
  mockDismissReminder,
  mockDisconnectMail,
  mockGetApplicationEmails,
  mockGetMailConnection,
  mockGetReminders,
  mockStartMailConnection,
  mockSyncMail,
} from './mockMail';

// Real paths (see ARCHITECTURE.md): GET/POST /mail-connections, DELETE /mail-connections/{id},
// POST /mail-connections/sync, GET /reminders, PUT /reminders/{id}, GET /applications/{id}/emails.
// Only subject, time and type are ever stored by the backend; nothing from a mail body is handled here.
export function isMockMail() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_MAIL === 'true';
}

// A 401 means the token is missing or expired, so drop it; pages then redirect to /login.
async function withAuthHandling(call) {
  try {
    return await call();
  } catch (error) {
    if (error?.status === 401) clearToken();
    throw error;
  }
}

// Resolves {configured, connection}; connection is null or {id, provider, createdAt}.
export function getMailConnection() {
  return withAuthHandling(() =>
    isMockMail() ? mockGetMailConnection() : get('/mail-connections'),
  );
}

// Resolves {authUrl}. 503 means the server has no mailbox setup; 409 means already connected.
// The caller sends the browser to authUrl with window.location.assign.
export function startMailConnection() {
  return withAuthHandling(() =>
    isMockMail() ? mockStartMailConnection() : post('/mail-connections'),
  );
}

export function disconnectMail(id) {
  return withAuthHandling(() =>
    isMockMail() ? mockDisconnectMail(id) : del(`/mail-connections/${encodeURIComponent(id)}`),
  );
}

// Resolves {checked, newReplies}.
export function syncMail() {
  return withAuthHandling(() => (isMockMail() ? mockSyncMail() : post('/mail-connections/sync')));
}

// Resolves [{id, applicationId, jobTitle, company, dueAt}].
export function getReminders() {
  return withAuthHandling(() => (isMockMail() ? mockGetReminders() : get('/reminders')));
}

export function dismissReminder(id) {
  return withAuthHandling(() =>
    isMockMail()
      ? mockDismissReminder(id)
      : put(`/reminders/${encodeURIComponent(id)}`, { status: 'dismissed' }),
  );
}

// Resolves [{id, type, classification, subject, occurredAt, status}].
export function getApplicationEmails(applicationId) {
  return withAuthHandling(() =>
    isMockMail()
      ? mockGetApplicationEmails(applicationId)
      : get(`/applications/${encodeURIComponent(applicationId)}/emails`),
  );
}
