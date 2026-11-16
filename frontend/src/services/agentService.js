import { post } from './apiClient';
import { clearToken } from '../utils/authToken';
import { mockRunAgent } from './mockAgent';

// Real path (see ARCHITECTURE.md "Draft agent"): POST /agent/run, no body.
// Resolves {considered, draftsCreated}. Rejects with an ApiError: 404 means no profile yet,
// 409 means no base resume yet. The agent only writes drafts; it never submits anything.
export function isMockAgent() {
  return import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_AGENT === 'true';
}

export async function runAgent() {
  try {
    return await (isMockAgent() ? mockRunAgent() : post('/agent/run'));
  } catch (error) {
    // A 401 means the token is missing or expired, so drop it; pages then redirect to /login.
    if (error?.status === 401) clearToken();
    throw error;
  }
}
