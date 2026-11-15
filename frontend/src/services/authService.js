import { post } from './apiClient';

// ASSUMPTION: ARCHITECTURE.md says POST /auth/login "returns a JWT token" but does not define
// the response shape. We assume `{ "token": "<jwt>" }`; change only this function if the
// backend contract differs.
function extractToken(data) {
  return data?.token;
}

export async function login(email, password) {
  const data = await post('/auth/login', { email, password });
  const token = extractToken(data);
  if (!token) {
    throw new Error('Login response did not include a token');
  }
  return token;
}
