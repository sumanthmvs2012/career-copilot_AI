// Tailored copies carry the job they were made for; uploads have neither field.
export function isTailoredCopy(resume) {
  return resume?.jobId != null || resume?.baseResumeId != null;
}

// The resume to tailor: the uploaded (not tailored) one flagged isBase.
export function findBaseResume(resumes) {
  return (resumes ?? []).find((r) => r.isBase && !isTailoredCopy(r)) ?? null;
}

export function tailoredLabel(resume) {
  const title = resume.jobTitle || 'Untitled job';
  return resume.company ? `${title} at ${resume.company}` : title;
}
