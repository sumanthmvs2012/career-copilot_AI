import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InterviewQuestionsPage from './InterviewQuestionsPage';
import { getInterviewQuestions, regenerateInterviewQuestions } from '../services/interviewService';
import { listResumes } from '../services/resumeService';

vi.mock('../services/interviewService', () => ({
  getInterviewQuestions: vi.fn(),
  regenerateInterviewQuestions: vi.fn(),
}));
vi.mock('../services/resumeService', () => ({ listResumes: vi.fn() }));

const DATA = {
  jobId: 4,
  jobTitle: 'Backend Engineer',
  company: 'Acme',
  questions: [
    { id: 1, question: 'Tell me about a conflict.', category: 'behavioral' },
    { id: 2, question: 'Explain indexes.', category: 'technical' },
    { id: 3, question: 'Why Acme?', category: 'company' },
  ],
};

const apiError = (status) => Object.assign(new Error(`status ${status}`), { status });

function renderPage() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/jobs/4/interview']}>
      <Routes>
        <Route path="/jobs/:id/interview" element={<InterviewQuestionsPage />} />
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function stubClipboard(writeText) {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
}

describe('InterviewQuestionsPage', () => {
  beforeEach(() => {
    vi.mocked(getInterviewQuestions).mockReset().mockResolvedValue(DATA);
    vi.mocked(regenerateInterviewQuestions).mockReset();
    vi.mocked(listResumes)
      .mockReset()
      .mockResolvedValue([{ id: 1, isBase: true }]);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows a loading state first', () => {
    vi.mocked(getInterviewQuestions).mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText('Loading interview questions…')).toBeInTheDocument();
  });

  it('groups questions by category and shows no resume hint when a resume exists', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Technical' })).toBeInTheDocument();
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(headings).toEqual(['Technical', 'Behavioral', 'Company']);
    expect(screen.getByText('Backend Engineer at Acme')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(screen.queryByText(/Upload a resume/)).not.toBeInTheDocument();
    expect(getInterviewQuestions).toHaveBeenCalledWith('4');
  });

  it('shows the upload hint but still lists questions when there is no resume', async () => {
    vi.mocked(listResumes).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/Upload a resume for questions/)).toBeInTheDocument();
    expect(screen.getByText('Explain indexes.')).toBeInTheDocument();
  });

  it('still lists questions when the resume check fails', async () => {
    vi.mocked(listResumes).mockRejectedValue(apiError(500));
    renderPage();
    expect(await screen.findByText('Explain indexes.')).toBeInTheDocument();
  });

  it('copies all questions as numbered plain text in one click', async () => {
    const writeText = vi.fn().mockResolvedValue();
    const user = renderPage();
    stubClipboard(writeText);
    await user.click(await screen.findByRole('button', { name: 'Copy all' }));
    expect(writeText).toHaveBeenCalledWith(
      '1. Explain indexes.\n2. Tell me about a conflict.\n3. Why Acme?',
    );
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('warns when copying is not possible', async () => {
    const user = renderPage();
    stubClipboard(vi.fn().mockRejectedValue(new Error('no')));
    await user.click(await screen.findByRole('button', { name: 'Copy all' }));
    expect(await screen.findByText(/could not copy automatically/)).toBeInTheDocument();
  });

  it('regenerates and replaces the list', async () => {
    vi.mocked(regenerateInterviewQuestions).mockResolvedValue({
      ...DATA,
      questions: [{ id: 9, question: 'A brand new question.', category: 'role' }],
    });
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: 'Regenerate' }));
    expect(await screen.findByText('A brand new question.')).toBeInTheDocument();
    expect(regenerateInterviewQuestions).toHaveBeenCalledWith('4');
    expect(screen.queryByText('Explain indexes.')).not.toBeInTheDocument();
  });

  it('keeps the old list and shows an error when regenerating fails', async () => {
    vi.mocked(regenerateInterviewQuestions).mockRejectedValue(apiError(500));
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: 'Regenerate' }));
    expect(await screen.findByText(/could not regenerate/)).toBeInTheDocument();
    expect(screen.getByText('Explain indexes.')).toBeInTheDocument();
  });

  it('shows the empty state', async () => {
    vi.mocked(getInterviewQuestions).mockResolvedValue({ ...DATA, questions: [] });
    renderPage();
    expect(await screen.findByText(/No questions yet/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy all' })).toBeDisabled();
  });

  it('shows not-found on 404', async () => {
    vi.mocked(getInterviewQuestions).mockRejectedValue(apiError(404));
    renderPage();
    expect(await screen.findByText(/could not find that job/)).toBeInTheDocument();
  });

  it('shows an error with retry, logging only the status', async () => {
    vi.mocked(getInterviewQuestions).mockRejectedValueOnce(apiError(500));
    const user = renderPage();
    expect(await screen.findByText(/could not load the questions/)).toBeInTheDocument();
    expect(console.error).toHaveBeenCalledWith('Loading interview questions failed', {
      status: 500,
    });
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Explain indexes.')).toBeInTheDocument();
  });

  it('redirects to login on 401', async () => {
    vi.mocked(getInterviewQuestions).mockRejectedValue(apiError(401));
    renderPage();
    await waitFor(() => expect(screen.getByText('Login page')).toBeInTheDocument());
  });
});
