import { ApiError, get, post, put } from './apiClient';
import { clearToken } from '../utils/authToken';

// Real path: GET/POST /applications and PUT /applications/{id} (see ARCHITECTURE.md).
// Row shape: {id, jobId, jobTitle, company, resumeId, status, coverLetter, appliedAt, submittedBy,
// emailSent, emailSentAt, createdAt, updatedAt}.
// In dev with VITE_USE_MOCK_APPLICATIONS=true an in-memory list is used instead (resets on reload).
export const APPLICATION_STATUSES = [
  'draft',
  'applied',
  'interviewing',
  'offer',
  'rejected',
  'withdrawn',
];

export function isMockApplications() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_APPLICATIONS === 'true';
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

const DAY = 24 * 60 * 60 * 1000;

function daysAgo(days, hours = 0) {
  return new Date(Date.now() - days * DAY - hours * 60 * 60 * 1000).toISOString();
}

function sampleApplications() {
  const row = (id, jobTitle, company, status, days, emailSent) => ({
    id,
    jobId: 9000 + id,
    jobTitle,
    company,
    resumeId: null,
    status,
    coverLetter: null,
    appliedAt: status === 'draft' ? null : daysAgo(days),
    submittedBy: 'user',
    emailSent,
    emailSentAt: emailSent ? daysAgo(days, -1) : null,
    emailClassification: emailSent ? (status === 'rejected' ? 'rejection' : 'other') : null,
    createdAt: daysAgo(days + 1),
    updatedAt: daysAgo(days),
  });
  return [
    row(1, 'Frontend Engineer', 'Acme Corp', 'draft', 1, false),
    row(2, 'Backend Developer', 'Globex Labs', 'applied', 3, true),
    row(3, 'Full Stack Engineer', 'Initech Software', 'applied', 6, true),
    row(4, 'Platform Engineer', 'Umbrella Systems', 'interviewing', 9, true),
    row(5, 'Data Engineer', 'Hooli Analytics', 'interviewing', 12, true),
    row(6, 'Software Engineer II', 'Stark Works', 'offer', 16, true),
    row(7, 'QA Automation Engineer', 'Wayne Digital', 'rejected', 20, true),
    row(8, 'DevOps Engineer', 'Soylent Cloud', 'rejected', 24, false),
    row(9, 'React Developer', 'Pied Piper Studio', 'withdrawn', 27, false),
    row(10, 'Java Developer', 'Vandelay Tech', 'draft', 2, false),
  ];
}

let mockRows = null;
let mockNextId = 100;

export function resetMockApplications() {
  mockRows = null;
  mockNextId = 100;
}

function mockStore() {
  if (!mockRows) mockRows = sampleApplications();
  return mockRows;
}

function mockFail(status, message) {
  return new ApiError(`Request failed with status ${status}`, status, { status, message });
}

export function listApplications() {
  return withAuthHandling(async () => {
    if (!isMockApplications()) return get('/applications');
    return mockStore().map((row) => ({ ...row }));
  });
}

export function createApplication({ jobId, resumeId, status = 'draft', coverLetter } = {}) {
  const body = { jobId, resumeId, status, coverLetter };
  return withAuthHandling(async () => {
    if (!isMockApplications()) return post('/applications', body);
    const rows = mockStore();
    if (rows.some((row) => row.jobId === jobId)) throw mockFail(409, 'You already track this job.');
    const now = new Date().toISOString();
    const row = {
      id: mockNextId++,
      jobId,
      jobTitle: `Job ${jobId}`,
      company: null,
      resumeId: resumeId ?? null,
      status,
      coverLetter: coverLetter ?? null,
      appliedAt: status === 'applied' ? now : null,
      submittedBy: 'user',
      emailSent: false,
      emailSentAt: null,
      createdAt: now,
      updatedAt: now,
    };
    mockRows = [row, ...rows];
    return { ...row };
  });
}

export function updateApplication(id, { status, coverLetter } = {}) {
  const body = {};
  if (status !== undefined) body.status = status;
  if (coverLetter !== undefined) body.coverLetter = coverLetter;
  return withAuthHandling(async () => {
    if (!isMockApplications()) return put(`/applications/${id}`, body);
    const rows = mockStore();
    const row = rows.find((r) => r.id === id);
    if (!row) throw mockFail(404, 'Application not found.');
    if (
      Object.keys(body).length === 0 ||
      (status !== undefined && !APPLICATION_STATUSES.includes(status))
    ) {
      throw mockFail(400, 'Validation failed');
    }
    const now = new Date().toISOString();
    if (status !== undefined) {
      if (status === 'applied' && !row.appliedAt) row.appliedAt = now;
      row.status = status;
    }
    if (coverLetter !== undefined) row.coverLetter = coverLetter;
    row.updatedAt = now;
    return { ...row };
  });
}

// Submits a draft (the cover letter is saved first). Rejects with an ApiError: 409 means the
// application is not a draft any more, 404 an unknown application. This records the submission in
// the app only; it does not contact the employer.
export function submitApplication(id, coverLetter) {
  const body = coverLetter === undefined ? undefined : { coverLetter };
  return withAuthHandling(async () => {
    if (!isMockApplications()) return post(`/applications/${id}/submit`, body);
    const row = mockStore().find((r) => r.id === id);
    if (!row) throw mockFail(404, 'Application not found.');
    if (row.status !== 'draft') throw mockFail(409, 'Only a draft can be submitted.');
    const now = new Date().toISOString();
    if (coverLetter !== undefined) row.coverLetter = coverLetter;
    row.status = 'applied';
    row.appliedAt = now;
    row.submittedBy = 'user';
    row.updatedAt = now;
    return { ...row };
  });
}
