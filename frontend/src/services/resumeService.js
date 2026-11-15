import { ApiError, del, get, upload } from './apiClient';
import { clearToken } from '../utils/authToken';
import {
  isMockResumeEnabled,
  mockDeleteResume,
  mockListResumes,
  mockUploadResume,
} from './mockResume';

export const MAX_RESUMES = 2;
export const MAX_RESUME_BYTES = 5 * 1024 * 1024;
const ALLOWED_EXTENSIONS = ['.pdf', '.docx'];

// Returns a short user-facing message, or '' when the file is acceptable.
export function validateResumeFile(file) {
  const name = (file?.name ?? '').toLowerCase();
  if (!ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext))) return 'Upload a PDF or DOCX file.';
  if (file.size > MAX_RESUME_BYTES) return 'The file must be 5 MB or smaller.';
  return '';
}

// A 401 means the token is missing or expired, so drop it here; pages then redirect to /login.
async function withAuthHandling(call) {
  try {
    return await call();
  } catch (error) {
    if (error?.status === 401) clearToken();
    throw error;
  }
}

// Base resume first; may be empty.
export function listResumes() {
  return withAuthHandling(() => (isMockResumeEnabled() ? mockListResumes() : get('/resumes')));
}

// Resolves to null on success; 404 means the resume does not exist or is not yours.
export function deleteResume(id) {
  return withAuthHandling(() =>
    isMockResumeEnabled() ? mockDeleteResume(id) : del(`/resumes/${id}`),
  );
}

// Rejects with an ApiError; 404 means no profile yet, 400 a rejected file, 409 the 2-resume limit. A failed client-side
// check rejects with status 400 and sends no request.
export function uploadResume(file) {
  const message = validateResumeFile(file);
  if (message) return Promise.reject(new ApiError(message, 400, { status: 400, message }));
  return withAuthHandling(() => {
    if (isMockResumeEnabled()) return mockUploadResume(file);
    const formData = new FormData();
    formData.append('file', file);
    return upload('/resumes', formData);
  });
}
