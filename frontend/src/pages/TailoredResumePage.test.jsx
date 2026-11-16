import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TailoredResumePage from './TailoredResumePage';
import { deleteResume, getResume, getSuggestions } from '../services/resumeService';

vi.mock('../services/resumeService', () => ({
  getResume: vi.fn(),
  deleteResume: vi.fn(),
  getSuggestions: vi.fn(),
}));

function apiError(status) {
  return Object.assign(new Error(`status ${status}`), { status });
}

const COPY = {
  id: 5,
  fileName: 'Tailored - Backend Engineer - Acme.txt',
  contentType: 'text/plain',
  isBase: false,
  baseResumeId: 1,
  jobId: 2,
  jobTitle: 'Backend Engineer',
  company: 'Acme',
  parsedText: 'Backend Engineer\nRelevant skills: Java\n\nOriginal text',
  suggestions: [
    { type: 'add_skill', text: 'Add Kafka to your resume if you have used it.' },
    { type: 'add_skill', text: 'Add Docker to your resume if you have used it.' },
    { type: 'headline', text: 'Use Backend Engineer as your headline.' },
  ],
  createdAt: '2026-11-15T10:00:00Z',
};

function renderPage() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/resume/5']}>
      <Routes>
        <Route path="/resume/:id" element={<TailoredResumePage />} />
        <Route path="/resume" element={<p>Resume list page</p>} />
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

describe('TailoredResumePage', () => {
  beforeEach(() => {
    vi.mocked(getResume).mockReset().mockResolvedValue(COPY);
    vi.mocked(deleteResume).mockReset().mockResolvedValue(null);
    vi.mocked(getSuggestions).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the loading state then the copy', async () => {
    renderPage();
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    expect(await screen.findByRole('heading', { name: COPY.fileName })).toBeInTheDocument();
    expect(screen.getByText('Tailored for Backend Engineer at Acme')).toBeInTheDocument();
    expect(screen.getByLabelText('Tailored resume text')).toHaveTextContent('Original text');
    expect(getResume).toHaveBeenCalledWith('5');
    expect(screen.getByText('Skills to add')).toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
    expect(screen.getByRole('link', { name: 'Back to your resumes' })).toHaveAttribute(
      'href',
      '/resume',
    );
  });

  it('copies the text and confirms', async () => {
    const user = renderPage();
    const writeText = vi.fn().mockResolvedValue();
    await screen.findByRole('heading', { name: COPY.fileName });
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await user.click(screen.getByRole('button', { name: 'Copy text' }));
    expect(writeText).toHaveBeenCalledWith(COPY.parsedText);
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('shows a fallback message when copying fails', async () => {
    const user = renderPage();
    const writeText = vi.fn().mockRejectedValue(new Error('denied'));
    await screen.findByRole('heading', { name: COPY.fileName });
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await user.click(screen.getByRole('button', { name: 'Copy text' }));
    expect(await screen.findByText(/could not copy automatically/i)).toBeInTheDocument();
    expect(screen.queryByText('Copied')).not.toBeInTheDocument();
  });

  it('downloads a .txt file named from fileName', async () => {
    const user = renderPage();
    await screen.findByRole('heading', { name: COPY.fileName });
    const createObjectURL = vi.fn().mockReturnValue('blob:x');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    let downloadName = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function click() {
      downloadName = this.download;
    });
    await user.click(screen.getByRole('button', { name: 'Download .txt' }));
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(createObjectURL.mock.calls[0][0]).toBeInstanceOf(Blob);
    expect(downloadName).toBe(COPY.fileName);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x');
  });

  it('deletes after a two-step confirm and returns to /resume', async () => {
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: `Delete ${COPY.fileName}` }));
    expect(deleteResume).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText('Resume list page')).toBeInTheDocument();
    expect(deleteResume).toHaveBeenCalledWith(5);
  });

  it('cancelling the confirm keeps the copy', async () => {
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: `Delete ${COPY.fileName}` }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('button', { name: `Delete ${COPY.fileName}` })).toBeInTheDocument();
    expect(deleteResume).not.toHaveBeenCalled();
  });

  it('shows an error when delete fails', async () => {
    vi.mocked(deleteResume).mockRejectedValue(apiError(500));
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: `Delete ${COPY.fileName}` }));
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText(/could not delete this copy/i)).toBeInTheDocument();
  });

  it('shows not-found on 404', async () => {
    vi.mocked(getResume).mockRejectedValue(apiError(404));
    renderPage();
    expect(await screen.findByText(/could not find that resume/i)).toBeInTheDocument();
  });

  it('shows an error with retry on other failures', async () => {
    vi.mocked(getResume).mockRejectedValueOnce(apiError(500)).mockResolvedValueOnce(COPY);
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: 'Try again' }));
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: COPY.fileName })).toBeInTheDocument(),
    );
  });

  it('goes to login on 401', async () => {
    vi.mocked(getResume).mockRejectedValue(apiError(401));
    renderPage();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('fetches suggestions when the loaded copy has none, like the real API', async () => {
    const { suggestions, ...withoutSuggestions } = COPY;
    vi.mocked(getResume).mockResolvedValue(withoutSuggestions);
    vi.mocked(getSuggestions).mockResolvedValue({ suggestions });
    renderPage();
    expect(await screen.findByText(/add kafka to your resume/i)).toBeInTheDocument();
    expect(getSuggestions).toHaveBeenCalledWith(5, 2);
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
  });

  it('does not refetch when the response includes suggestions', async () => {
    renderPage();
    await screen.findByText(/add kafka to your resume/i);
    expect(getSuggestions).not.toHaveBeenCalled();
  });

  it('shows an error with retry when suggestions fail to load', async () => {
    const { suggestions, ...withoutSuggestions } = COPY;
    vi.mocked(getResume).mockResolvedValue(withoutSuggestions);
    vi.mocked(getSuggestions)
      .mockRejectedValueOnce(apiError(500))
      .mockResolvedValueOnce({ suggestions });
    const user = renderPage();
    expect(await screen.findByText(/could not load suggestions/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(/add kafka to your resume/i)).toBeInTheDocument();
  });

  it('shows a single Delete control until the confirm opens', async () => {
    renderPage();
    await screen.findByRole('heading', { name: COPY.fileName });
    expect(screen.getAllByText('Delete')).toHaveLength(1);
  });
});
