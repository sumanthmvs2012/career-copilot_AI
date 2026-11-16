import { ApiError } from './apiClient';

// Dev-only in-memory interview questions (VITE_USE_MOCK_INTERVIEW=true). Mirrors the contract:
// {jobId, jobTitle, company, questions: [{id, question, category}]}. Lost on reload.
const store = new Map();
let generation = 0;

export function resetMockInterview() {
  store.clear();
  generation = 0;
}

function build(jobId) {
  const round = generation++;
  const questions = [
    ['technical', 'Walk me through how you would design a REST API for this role.'],
    ['technical', 'How do you debug a slow database query?'],
    ['technical', 'Which testing strategies do you rely on, and why?'],
    ['behavioral', 'Tell me about a time you disagreed with a teammate.'],
    ['behavioral', 'Describe a project that did not go as planned.'],
    ['role', 'What would your first 30 days in this role look like?'],
    ['role', 'How do you prioritise when everything is urgent?'],
    ['company', 'Why do you want to work at this company?'],
    ['company', 'What do you know about our product?'],
    ['resume_gap', 'This role uses Kafka. How quickly could you get up to speed?'],
  ].map(([category, question], index) => ({
    id: jobId * 1000 + round * 100 + index + 1,
    question,
    category,
  }));
  return { jobId, jobTitle: `Job ${jobId}`, company: 'Mock Company', questions };
}

export function mockGetInterviewQuestions(jobId) {
  const key = Number(jobId);
  if (!Number.isFinite(key)) {
    return Promise.reject(new ApiError('Request failed with status 404', 404, null));
  }
  if (!store.has(key)) store.set(key, build(key));
  return Promise.resolve(structuredClone(store.get(key)));
}

export function mockRegenerateInterviewQuestions(jobId) {
  const key = Number(jobId);
  if (!Number.isFinite(key)) {
    return Promise.reject(new ApiError('Request failed with status 404', 404, null));
  }
  store.set(key, build(key));
  return Promise.resolve(structuredClone(store.get(key)));
}
