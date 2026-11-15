import { afterEach, describe, expect, it, vi } from 'vitest';
import { isMockApplications, listApplications } from './applicationsService';

describe('applicationsService', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('returns [] when the mock flag is off', async () => {
    expect(isMockApplications()).toBe(false);
    expect(await listApplications()).toEqual([]);
  });

  it('returns a varied sample list when the mock flag is on in dev', async () => {
    vi.stubEnv('VITE_USE_MOCK_APPLICATIONS', 'true');
    expect(isMockApplications()).toBe(true);
    const rows = await listApplications();
    expect(rows.length).toBeGreaterThanOrEqual(8);
    expect(rows.length).toBeLessThanOrEqual(10);
    const statuses = new Set(rows.map((r) => r.status));
    for (const s of ['draft', 'applied', 'interviewing', 'offer', 'rejected', 'withdrawn']) {
      expect(statuses.has(s)).toBe(true);
    }
    expect(rows.some((r) => r.emailSent)).toBe(true);
    expect(rows.some((r) => !r.emailSent)).toBe(true);
    for (const r of rows) {
      expect(Object.keys(r).sort()).toEqual(
        ['appliedAt', 'company', 'emailSent', 'emailSentAt', 'id', 'jobTitle', 'status'].sort(),
      );
    }
  });
});
