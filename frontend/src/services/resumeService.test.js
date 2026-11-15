import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, upload } from './apiClient';
import { mockDeleteResume, mockListResumes, mockUploadResume, resetMockResume } from './mockResume';
import { deleteResume, listResumes, uploadResume, validateResumeFile } from './resumeService';
import { clearToken, getToken, setToken } from '../utils/authToken';

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const RESUME = { id: 1, fileName: 'cv.pdf', isBase: true, textLength: 10 };

function makeFile(name, size = 100) {
  const file = new File(['x'], name, { type: 'application/pdf' });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('resumeService', () => {
  let fetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    setToken('jwt-token');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearToken();
  });

  it('GETs /resumes with the bearer token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, [RESUME]));
    await expect(listResumes()).resolves.toEqual([RESUME]);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/resumes$/);
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('POSTs multipart form data without a Content-Type header', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, RESUME));
    await expect(uploadResume(makeFile('CV.PDF'))).resolves.toEqual(RESUME);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/resumes$/);
    expect(options.method).toBe('POST');
    expect(options.body).toBeInstanceOf(FormData);
    expect(options.body.get('file')).toBeInstanceOf(File);
    expect(options.headers['Content-Type']).toBeUndefined();
    expect(options.headers.Accept).toBe('application/json');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('accepts .docx and rejects other types and oversize files before any request', async () => {
    expect(validateResumeFile(makeFile('a.docx'))).toBe('');
    expect(validateResumeFile(makeFile('a.txt'))).toBe('Upload a PDF or DOCX file.');
    expect(validateResumeFile(makeFile('a.pdf', 5 * 1024 * 1024))).toBe('');
    expect(validateResumeFile(makeFile('a.pdf', 5 * 1024 * 1024 + 1))).toBe(
      'The file must be 5 MB or smaller.',
    );

    const error = await uploadResume(makeFile('a.txt')).catch((e) => e);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Upload a PDF or DOCX file.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('keeps status and message from a 404 or 400', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(404, { status: 404, message: 'Create your profile first.' }),
    );
    const notFound = await uploadResume(makeFile('a.pdf')).catch((e) => e);
    expect(notFound).toBeInstanceOf(ApiError);
    expect(notFound.status).toBe(404);

    fetchMock.mockResolvedValue(jsonResponse(400, { status: 400, message: 'Unreadable' }));
    const bad = await uploadResume(makeFile('a.pdf')).catch((e) => e);
    expect(bad.status).toBe(400);
    expect(bad.data.message).toBe('Unreadable');
  });

  it('DELETEs /resumes/{id} and resolves null on 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(deleteResume(5)).resolves.toBeNull();
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/resumes\/5$/);
    expect(options.method).toBe('DELETE');
    expect(options.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('keeps the 409 message from a third upload', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        status: 409,
        message: 'You already have 2 resumes. Delete one to upload another.',
      }),
    );
    const error = await uploadResume(makeFile('a.pdf')).catch((e) => e);
    expect(error.status).toBe(409);
    expect(error.data.message).toMatch(/Delete one/);
  });

  it('clears the token on a 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'expired' }));
    const error = await listResumes().catch((e) => e);
    expect(error.status).toBe(401);
    expect(getToken()).toBeNull();
  });

  it('upload helper omits Authorization without a token', async () => {
    clearToken();
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await upload('/x', new FormData());
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
});

describe('mockResume', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetMockResume();
    setToken('mock-token');
  });

  afterEach(() => {
    vi.useRealTimers();
    clearToken();
  });

  async function settle(promise) {
    const result = promise.catch((e) => e);
    await vi.runAllTimersAsync();
    return result;
  }

  it('lists nothing, then keeps up to 2 resumes with the newest as base', async () => {
    expect(await settle(mockListResumes())).toEqual([]);
    const first = await settle(mockUploadResume(makeFile('a.pdf')));
    const second = await settle(mockUploadResume(makeFile('b.pdf')));
    expect(second.id).not.toBe(first.id);
    const rows = await settle(mockListResumes());
    expect(rows.map((r) => r.fileName)).toEqual(['b.pdf', 'a.pdf']);
    expect(rows.map((r) => r.isBase)).toEqual([true, false]);
  });

  it('returns 409 with the limit message on a third upload', async () => {
    await settle(mockUploadResume(makeFile('a.pdf')));
    await settle(mockUploadResume(makeFile('b.pdf')));
    const error = await settle(mockUploadResume(makeFile('c.pdf')));
    expect(error.status).toBe(409);
    expect(error.data.message).toBe('You already have 2 resumes. Delete one to upload another.');
    expect(await settle(mockListResumes())).toHaveLength(2);
  });

  it('deletes the base and promotes the newest remaining, then allows another upload', async () => {
    const a = await settle(mockUploadResume(makeFile('a.pdf')));
    const b = await settle(mockUploadResume(makeFile('b.pdf')));
    expect(await settle(mockDeleteResume(b.id))).toBeNull();
    const rows = await settle(mockListResumes());
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ id: a.id, isBase: true });
    const c = await settle(mockUploadResume(makeFile('c.pdf')));
    expect(c.isBase).toBe(true);
  });

  it('returns 404 when deleting an unknown resume', async () => {
    expect((await settle(mockDeleteResume(99))).status).toBe(404);
  });

  it('returns 401 without a token', async () => {
    clearToken();
    expect((await settle(mockListResumes())).status).toBe(401);
  });
});
