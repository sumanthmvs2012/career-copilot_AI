const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    // status 0 means no response arrived (offline, DNS, CORS), so callers can tell it apart.
    this.status = status;
    this.data = data;
  }
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const options = {
    method,
    headers: { Accept: 'application/json', ...headers },
  };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, options);
  } catch {
    throw new ApiError('Network request failed', 0, null);
  }

  const data = await parseBody(response);
  if (!response.ok) {
    throw new ApiError(`Request failed with status ${response.status}`, response.status, data);
  }
  return data;
}

export function post(path, body, options = {}) {
  return request(path, { ...options, method: 'POST', body });
}
