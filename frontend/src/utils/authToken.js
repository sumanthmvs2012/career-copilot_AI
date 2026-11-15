// The token lives in memory for fast access and is mirrored to sessionStorage so a page refresh
// keeps the user signed in for this tab only. Never log the token.
export const TOKEN_STORAGE_KEY = 'authToken';

let memoryToken = null;

function readStorage() {
  try {
    return window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    // Storage can be unavailable (privacy mode, blocked site data); memory still works.
    return null;
  }
}

function writeStorage(value) {
  try {
    if (value === null) {
      window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    } else {
      window.sessionStorage.setItem(TOKEN_STORAGE_KEY, value);
    }
  } catch {
    // Same reason as readStorage: fall back to memory only.
  }
}

export function getToken() {
  if (memoryToken) return memoryToken;
  const stored = readStorage();
  if (stored) memoryToken = stored;
  return memoryToken;
}

export function setToken(token) {
  memoryToken = token;
  writeStorage(token);
}

export function clearToken() {
  memoryToken = null;
  writeStorage(null);
}
