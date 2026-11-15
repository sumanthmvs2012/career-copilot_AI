// There is no applications API yet. Replace the body with a GET /applications call when the backend
// ships it. Expected row shape: {id, jobTitle, company, status, appliedAt, emailSent, emailSentAt}.
// In dev with VITE_USE_MOCK_APPLICATIONS=true a sample list is returned so the table can be previewed.
export function isMockApplications() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_APPLICATIONS === 'true';
}

const DAY = 24 * 60 * 60 * 1000;

function daysAgo(days, hours = 0) {
  return new Date(Date.now() - days * DAY - hours * 60 * 60 * 1000).toISOString();
}

function sampleApplications() {
  const row = (id, jobTitle, company, status, days, emailSent) => ({
    id,
    jobTitle,
    company,
    status,
    appliedAt: status === 'draft' ? null : daysAgo(days),
    emailSent,
    emailSentAt: emailSent ? daysAgo(days, -1) : null,
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

export async function listApplications() {
  return isMockApplications() ? sampleApplications() : [];
}
