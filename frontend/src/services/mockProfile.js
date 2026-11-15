import { ApiError } from './apiClient';
import { getToken } from '../utils/authToken';

// Temporary stand-in until the Spring Boot /profile endpoints are wired up. State lives in memory
// only, so a page reload resets it; that is fine for a dev-only mock.
const MOCK_DELAY_MS = 400;
let storedProfile = null;

export function isMockProfileEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_PROFILE === 'true';
}

export function resetMockProfile() {
  storedProfile = null;
}

async function delayAndAuthorize() {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (!getToken()) throw new ApiError('Request failed with status 401', 401, null);
}

export async function mockGetProfile() {
  await delayAndAuthorize();
  if (!storedProfile) throw new ApiError('Request failed with status 404', 404, null);
  return { ...storedProfile };
}

export async function mockUpdateProfile(input) {
  await delayAndAuthorize();
  const fullName = (input.fullName ?? '').trim();
  if (!fullName || fullName.length > 200) {
    throw new ApiError('Request failed with status 400', 400, {
      status: 400,
      message: 'Validation failed',
      fieldErrors: { fullName: 'Full name must be between 1 and 200 characters.' },
    });
  }
  const now = new Date().toISOString();
  storedProfile = {
    id: storedProfile?.id ?? 1,
    fullName,
    headline: input.headline ?? '',
    location: input.location ?? '',
    remoteOk: Boolean(input.remoteOk),
    autoSubmitEnabled: Boolean(input.autoSubmitEnabled),
    createdAt: storedProfile?.createdAt ?? now,
    updatedAt: now,
  };
  return { ...storedProfile };
}

// Lets other dev mocks (jobs) mirror the backend rule "create your profile first".
export function hasMockProfile() {
  return storedProfile !== null;
}
