import { post } from './apiClient';
import { isMockAuthEnabled, mockLogin, mockSignup } from './mockAuth';

// ASSUMPTION: ARCHITECTURE.md says POST /auth/login "returns a JWT token" but does not define
// the response shape. We assume `{ "token": "<jwt>" }` for both login and signup (TESTING.md says
// signup also gives the user a token); change only this function if the backend contract differs.
function extractToken(data) {
  return data?.token;
}

export async function login(email, password) {
  const data = isMockAuthEnabled()
    ? await mockLogin(email, password)
    : await post('/auth/login', { email, password });
  const token = extractToken(data);
  if (!token) {
    throw new Error('Login response did not include a token');
  }
  return token;
}

export async function signup(email, password) {
  const data = isMockAuthEnabled()
    ? await mockSignup(email, password)
    : await post('/auth/signup', { email, password });
  const token = extractToken(data);
  if (!token) {
    throw new Error('Signup response did not include a token');
  }
  return token;
}
