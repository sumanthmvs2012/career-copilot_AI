import { ApiError } from './apiClient';
import { getToken } from '../utils/authToken';

// Dev-only in-memory stand-in for /resumes. Only file metadata is kept, never file contents.
const MOCK_DELAY_MS = 400;
const MOCK_MAX_RESUMES = 2;
// Kept newest first; the base is always the newest or the one promoted after a delete.
let storedResumes = [];
let nextId = 1;

export function isMockResumeEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_RESUMES === 'true';
}

// Skills the mock "extracts" from any uploaded file; also used by the jobs mock to build matches.
export const MOCK_RESUME_SKILLS = ['Java', 'Spring Boot', 'SQL', 'React Native', 'Docker', 'Git'];

export function hasMockResume() {
  return storedResumes.length > 0;
}

export function resetMockResume() {
  storedResumes = [];
  nextId = 1;
}

async function delayAndAuthorize() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (!getToken()) throw new ApiError('Request failed with status 401', 401, null);
}

export async function mockListResumes() {
  await delayAndAuthorize();
  return storedResumes.map((r) => ({ ...r }));
}

export async function mockUploadResume(file) {
  await delayAndAuthorize();
  if (!file || file.size === 0) {
    throw new ApiError('Request failed with status 400', 400, {
      status: 400,
      message: 'We could not read text from that file.',
    });
  }
  if (storedResumes.length >= MOCK_MAX_RESUMES) {
    throw new ApiError('Request failed with status 409', 409, {
      status: 409,
      message: 'You already have 2 resumes. Delete one to upload another.',
    });
  }
  const created = {
    id: nextId++,
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    isBase: true,
    baseResumeId: null,
    textLength: 1200,
    skills: [...MOCK_RESUME_SKILLS],
    createdAt: new Date().toISOString(),
  };
  // The newest upload becomes the base.
  storedResumes = [created, ...storedResumes.map((r) => ({ ...r, isBase: false }))];
  return { ...created };
}

export async function mockDeleteResume(id) {
  await delayAndAuthorize();
  const target = storedResumes.find((r) => r.id === id);
  if (!target) throw new ApiError('Request failed with status 404', 404, null);
  storedResumes = storedResumes.filter((r) => r.id !== id);
  // If the base went away, the newest remaining resume (first in the list) takes over.
  if (target.isBase && storedResumes.length > 0) {
    storedResumes = storedResumes.map((r, i) => ({ ...r, isBase: i === 0 }));
  }
  return null;
}
