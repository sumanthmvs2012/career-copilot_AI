import { get, post } from './apiClient';
import { clearToken } from '../utils/authToken';
import { mockGetInterviewQuestions, mockRegenerateInterviewQuestions } from './mockInterview';

// Real paths: GET and POST /jobs/{id}/interview-questions (see ARCHITECTURE.md).
// Resolves {jobId, jobTitle, company, questions: [{id, question, category}]}.
// Rejects with an ApiError; 404 means an unknown job or no access to it.
export function isMockInterview() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_INTERVIEW === 'true';
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

export function getInterviewQuestions(jobId) {
  return withAuthHandling(() =>
    isMockInterview()
      ? mockGetInterviewQuestions(jobId)
      : get(`/jobs/${encodeURIComponent(jobId)}/interview-questions`),
  );
}

// Replaces the stored list with a fresh one.
export function regenerateInterviewQuestions(jobId) {
  return withAuthHandling(() =>
    isMockInterview()
      ? mockRegenerateInterviewQuestions(jobId)
      : post(`/jobs/${encodeURIComponent(jobId)}/interview-questions`),
  );
}
