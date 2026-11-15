import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createApplication,
  isMockApplications,
  listApplications,
  resetMockApplications,
  updateApplication,
} from './applicationsService';
import { get, post, put } from './apiClient';

vi.mock('./apiClient', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

describe('applicationsService (real API)', () => {
  beforeEach(() => {
    vi.mocked(get).mockReset();
    vi.mocked(post).mockReset();
    vi.mocked(put).mockReset();
  });

  it('lists with GET /applications', async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1 }]);
    expect(isMockApplications()).toBe(false);
    expect(await listApplications()).toEqual([{ id: 1 }]);
    expect(get).toHaveBeenCalledWith('/applications');
  });

  it('creates with POST /applications and defaults to draft', async () => {
    vi.mocked(post).mockResolvedValue({ id: 5 });
    await createApplication({ jobId: 7 });
    expect(post).toHaveBeenCalledWith('/applications', {
      jobId: 7,
      resumeId: undefined,
      status: 'draft',
      coverLetter: undefined,
    });
  });

  it('updates with PUT /applications/{id} and only sends given fields', async () => {
    vi.mocked(put).mockResolvedValue({ id: 3, status: 'offer' });
    await updateApplication(3, { status: 'offer' });
    expect(put).toHaveBeenCalledWith('/applications/3', { status: 'offer' });
  });

  it('passes errors through', async () => {
    vi.mocked(post).mockRejectedValue(Object.assign(new Error('x'), { status: 409 }));
    await expect(createApplication({ jobId: 1 })).rejects.toMatchObject({ status: 409 });
  });
});

describe('applicationsService (mock)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK_APPLICATIONS', 'true');
    resetMockApplications();
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('returns a varied sample list with the full row shape', async () => {
    expect(isMockApplications()).toBe(true);
    const rows = await listApplications();
    expect(rows.length).toBeGreaterThanOrEqual(8);
    const statuses = new Set(rows.map((r) => r.status));
    for (const s of ['draft', 'applied', 'interviewing', 'offer', 'rejected', 'withdrawn']) {
      expect(statuses.has(s)).toBe(true);
    }
    for (const r of rows) {
      expect(Object.keys(r).sort()).toEqual(
        [
          'appliedAt',
          'company',
          'coverLetter',
          'createdAt',
          'emailSent',
          'emailSentAt',
          'id',
          'jobId',
          'jobTitle',
          'resumeId',
          'status',
          'submittedBy',
          'updatedAt',
        ].sort(),
      );
    }
  });

  it('creates a draft in memory and rejects a duplicate job with 409', async () => {
    const before = (await listApplications()).length;
    const created = await createApplication({ jobId: 42 });
    expect(created.status).toBe('draft');
    expect((await listApplications()).length).toBe(before + 1);
    await expect(createApplication({ jobId: 42 })).rejects.toMatchObject({
      status: 409,
      data: { message: 'You already track this job.' },
    });
  });

  it('updates the status and rejects bad input', async () => {
    const [first] = await listApplications();
    const updated = await updateApplication(first.id, { status: 'applied' });
    expect(updated.status).toBe('applied');
    expect(updated.appliedAt).toBeTruthy();
    await expect(updateApplication(first.id, { status: 'bogus' })).rejects.toMatchObject({
      status: 400,
    });
    await expect(updateApplication(first.id, {})).rejects.toMatchObject({ status: 400 });
    await expect(updateApplication(99999, { status: 'offer' })).rejects.toMatchObject({
      status: 404,
    });
  });
});
