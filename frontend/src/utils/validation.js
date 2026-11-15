// Intentionally loose: the backend is the authority on valid emails; this only catches typos.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateLogin({ email, password }) {
  const errors = {};
  const trimmedEmail = (email ?? '').trim();

  if (!trimmedEmail) {
    errors.email = 'Email is required.';
  } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!password) {
    errors.password = 'Password is required.';
  }

  return errors;
}

// ASSUMPTION: SECURITY.md sets no password policy yet; 8 characters is a common minimum.
// Keep this in sync with the backend once it enforces its own rule.
export const MIN_PASSWORD_LENGTH = 8;

export function validateSignup({ email, password, confirmPassword }) {
  const errors = validateLogin({ email, password });

  if (password && password.length < MIN_PASSWORD_LENGTH) {
    errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
  }

  if (!confirmPassword) {
    errors.confirmPassword = 'Please confirm your password.';
  } else if (password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match.';
  }

  return errors;
}

export const MAX_PROFILE_FIELD_LENGTH = 200;

export function validateProfile({ fullName, headline, location }) {
  const errors = {};
  const name = (fullName ?? '').trim();

  if (!name) {
    errors.fullName = 'Full name is required.';
  } else if (name.length > MAX_PROFILE_FIELD_LENGTH) {
    errors.fullName = `Full name must be at most ${MAX_PROFILE_FIELD_LENGTH} characters.`;
  }
  if ((headline ?? '').trim().length > MAX_PROFILE_FIELD_LENGTH) {
    errors.headline = `Headline must be at most ${MAX_PROFILE_FIELD_LENGTH} characters.`;
  }
  if ((location ?? '').trim().length > MAX_PROFILE_FIELD_LENGTH) {
    errors.location = `Location must be at most ${MAX_PROFILE_FIELD_LENGTH} characters.`;
  }
  return errors;
}

export const MIN_JOB_DESCRIPTION_LENGTH = 20;
export const MAX_JOB_DESCRIPTION_LENGTH = 20000;
export const MAX_JOB_FIELD_LENGTH = 200;
export const MAX_JOB_URL_LENGTH = 1000;
export const REMOTE_TYPES = ['remote', 'hybrid', 'onsite'];

export function validateJobMatch({
  title,
  companyName,
  location,
  remoteType,
  sourceUrl,
  description,
}) {
  const errors = {};
  const text = (description ?? '').trim();

  if (!text) {
    errors.description = 'Job description is required.';
  } else if (text.length < MIN_JOB_DESCRIPTION_LENGTH) {
    errors.description = `Job description must be at least ${MIN_JOB_DESCRIPTION_LENGTH} characters.`;
  } else if (text.length > MAX_JOB_DESCRIPTION_LENGTH) {
    errors.description = `Job description must be at most ${MAX_JOB_DESCRIPTION_LENGTH} characters.`;
  }

  const shortFields = [
    ['title', title, 'Job title'],
    ['companyName', companyName, 'Company'],
    ['location', location, 'Location'],
  ];
  for (const [key, value, label] of shortFields) {
    if ((value ?? '').trim().length > MAX_JOB_FIELD_LENGTH) {
      errors[key] = `${label} must be at most ${MAX_JOB_FIELD_LENGTH} characters.`;
    }
  }
  if ((sourceUrl ?? '').trim().length > MAX_JOB_URL_LENGTH) {
    errors.sourceUrl = `Job URL must be at most ${MAX_JOB_URL_LENGTH} characters.`;
  }
  if (remoteType && !REMOTE_TYPES.includes(remoteType)) {
    errors.remoteType = 'Choose a valid remote type.';
  }
  return errors;
}
