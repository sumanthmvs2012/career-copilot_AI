import { ApiError } from './apiClient';

// Temporary stand-in until the Spring Boot /auth/login endpoint exists. These are throwaway
// demo values, not real credentials, and the mock is never used outside the dev server.
export const DEMO_EMAIL = 'demo@example.com';
export const DEMO_PASSWORD = 'password123';
const MOCK_DELAY_MS = 500;

export function isMockAuthEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_AUTH === 'true';
}

export async function mockLogin(email, password) {
  // The delay keeps the loading state visible, as it would be with a real request.
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  if (email.toLowerCase() !== DEMO_EMAIL || password !== DEMO_PASSWORD) {
    throw new ApiError('Request failed with status 401', 401, null);
  }
  return { token: `mock-token-${Date.now()}` };
}

export async function mockSignup(email) {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
  // The demo account already "exists", so it exercises the duplicate-email path.
  if (email.toLowerCase() === DEMO_EMAIL) {
    throw new ApiError('Request failed with status 409', 409, null);
  }
  return { token: `mock-token-${Date.now()}` };
}
