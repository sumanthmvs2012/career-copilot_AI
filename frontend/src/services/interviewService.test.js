import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getInterviewQuestions,
  isMockInterview,
  regenerateInterviewQuestions,
} from './interviewService';
import { resetMockInterview } from './mockInterview';
import { get, post } from './apiClient';
import { getToken, setToken } from '../utils/authToken';

vi.mock('./apiClient', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  post: vi.fn(),
}));

describe('interviewService (real API)', () => {
  beforeEach(() => {
    vi.mocked(get).mockReset();
    vi.mocked(post).mockReset();
  });

  it('gets questions with GET /jobs/{id}/interview-questions', async () => {
    vi.mocked(get).mockResolvedValue({ jobId: 4, questions: [] });
    expect(isMockInterview()).toBe(false);
    expect(await getInterviewQuestions(4)).toEqual({ jobId: 4, questions: [] });
    expect(get).toHaveBeenCalledWith('/jobs/4/interview-questions');
  });

  it('regenerates with POST on the same path', async () => {
    vi.mocked(post).mockResolvedValue({ jobId: 4, questions: [] });
    await regenerateInterviewQuestions(4);
    expect(post).toHaveBeenCalledWith('/jobs/4/interview-questions');
  });

  it('passes 404 through and clears the token on 401', async () => {
    vi.mocked(get).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 404 }));
    await expect(getInterviewQuestions(4)).rejects.toMatchObject({ status: 404 });
    setToken('jwt');
    vi.mocked(get).mockRejectedValueOnce(Object.assign(new Error('x'), { status: 401 }));
    await expect(getInterviewQuestions(4)).rejects.toMatchObject({ status: 401 });
    expect(getToken()).toBeNull();
  });
});

describe('interviewService (mock)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK_INTERVIEW', 'true');
    resetMockInterview();
    vi.mocked(get).mockReset();
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('returns the contract shape and the same list on repeat calls', async () => {
    expect(isMockInterview()).toBe(true);
    const first = await getInterviewQuestions(5);
    expect(first).toMatchObject({ jobId: 5 });
    expect(first.questions[0]).toEqual({
      id: expect.any(Number),
      question: expect.any(String),
      category: expect.any(String),
    });
    expect(await getInterviewQuestions(5)).toEqual(first);
    expect(get).not.toHaveBeenCalled();
  });

  it('regenerates a different list', async () => {
    const first = await getInterviewQuestions(5);
    const next = await regenerateInterviewQuestions(5);
    expect(next.questions[0].id).not.toBe(first.questions[0].id);
    expect(await getInterviewQuestions(5)).toEqual(next);
  });

  it('rejects an invalid job id with 404', async () => {
    await expect(getInterviewQuestions('abc')).rejects.toMatchObject({ status: 404 });
    await expect(regenerateInterviewQuestions('abc')).rejects.toMatchObject({ status: 404 });
  });
});
