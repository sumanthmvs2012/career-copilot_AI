import { get, put } from './apiClient';
import { clearToken } from '../utils/authToken';
import { isMockProfileEnabled, mockGetProfile, mockUpdateProfile } from './mockProfile';

// A 401 means the token is missing or expired, so drop it here; pages then redirect to /login.
async function withAuthHandling(call) {
  try {
    return await call();
  } catch (error) {
    if (error?.status === 401) clearToken();
    throw error;
  }
}

// Rejects with an ApiError; status 404 means the user has no profile yet.
export function getProfile() {
  return withAuthHandling(() => (isMockProfileEnabled() ? mockGetProfile() : get('/profile')));
}

export function updateProfile({ fullName, headline, location, remoteOk, autoSubmitEnabled }) {
  const body = {
    fullName: fullName.trim(),
    headline: headline.trim(),
    location: location.trim(),
    remoteOk: Boolean(remoteOk),
    autoSubmitEnabled: Boolean(autoSubmitEnabled),
  };
  return withAuthHandling(() =>
    isMockProfileEnabled() ? mockUpdateProfile(body) : put('/profile', body),
  );
}
