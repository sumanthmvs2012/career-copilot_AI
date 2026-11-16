import { ApiError } from './apiClient';
import { getToken } from '../utils/authToken';

// Dev-only in-memory stand-in for /resumes. Only file metadata is kept, never file contents.
const MOCK_DELAY_MS = 400;
const MOCK_MAX_RESUMES = 2;
// Kept newest first; the base is always the newest or the one promoted after a delete.
let storedResumes = [];
let nextId = 1;
// Tailored copies live apart from uploads so they never count toward the limit or the base.
let storedTailored = [];

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
  storedTailored = [];
  nextId = 1;
}

async function delayAndAuthorize() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (!getToken()) throw new ApiError('Request failed with status 401', 401, null);
}

export async function mockListResumes() {
  await delayAndAuthorize();
  const copies = storedTailored.map((copy) => {
    const summary = { ...copy };
    delete summary.parsedText;
    delete summary.suggestions;
    return summary;
  });
  return [...storedResumes.map((r) => ({ ...r })), ...copies];
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
  const copy = storedTailored.find((r) => r.id === id);
  if (copy) {
    storedTailored = storedTailored.filter((r) => r.id !== id);
    return null;
  }
  const target = storedResumes.find((r) => r.id === id);
  if (!target) throw new ApiError('Request failed with status 404', 404, null);
  storedResumes = storedResumes.filter((r) => r.id !== id);
  storedTailored = storedTailored.filter((r) => r.baseResumeId !== id);
  // If the base went away, the newest remaining resume (first in the list) takes over.
  if (target.isBase && storedResumes.length > 0) {
    storedResumes = storedResumes.map((r, i) => ({ ...r, isBase: i === 0 }));
  }
  return null;
}

function notFound() {
  return new ApiError('Request failed with status 404', 404, null);
}

export async function mockGetResume(id) {
  await delayAndAuthorize();
  const copy = storedTailored.find((r) => r.id === id);
  // Like the real API, GET does not return suggestions (only tailor does).
  if (copy) {
    const detail = { ...copy };
    delete detail.suggestions;
    return detail;
  }
  const upload = storedResumes.find((r) => r.id === id);
  if (!upload) throw notFound();
  return { ...upload, parsedText: `Sample text of ${upload.fileName}` };
}

async function findMockJob(jobId) {
  // Imported lazily because mockJobs already imports this module.
  const { mockListJobs } = await import('./mockJobs');
  const job = (await mockListJobs()).find((j) => String(j.id) === String(jobId));
  if (!job) throw notFound();
  return job;
}

function buildSuggestions(job) {
  return [
    { type: 'add_skill', text: 'Add Kubernetes to your resume if you have used it.' },
    { type: 'headline', text: `Use "${job.title}" as your headline.` },
    { type: 'location', text: 'Check that the job location fits yours.' },
  ];
}

export async function mockGetSuggestions(resumeId, jobId) {
  await delayAndAuthorize();
  const known =
    storedResumes.some((r) => r.id === resumeId) || storedTailored.some((r) => r.id === resumeId);
  if (!known) throw notFound();
  return { suggestions: buildSuggestions(await findMockJob(jobId)) };
}

export async function mockTailorResume(resumeId, jobId) {
  await delayAndAuthorize();
  const base = storedResumes.find((r) => r.id === resumeId);
  if (!base) throw notFound();
  const job = await findMockJob(jobId);
  const originalText = `Sample text of ${base.fileName}`;
  const parsedText = `${job.title}
Relevant skills: ${MOCK_RESUME_SKILLS.join(', ')}

${originalText}`;
  const fileName = job.companyName
    ? `Tailored - ${job.title} - ${job.companyName}.txt`
    : `Tailored - ${job.title}.txt`;
  const copy = {
    id: nextId++,
    fileName,
    contentType: 'text/plain',
    isBase: false,
    baseResumeId: base.id,
    jobId: job.id,
    jobTitle: job.title,
    company: job.companyName ?? null,
    textLength: parsedText.length,
    skills: [...MOCK_RESUME_SKILLS],
    createdAt: new Date().toISOString(),
    parsedText,
    suggestions: buildSuggestions(job),
  };
  // Tailoring the same resume for the same job again replaces the previous copy.
  storedTailored = [
    copy,
    ...storedTailored.filter((r) => !(r.baseResumeId === base.id && r.jobId === job.id)),
  ];
  return { ...copy };
}
