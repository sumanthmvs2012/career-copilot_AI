import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getStoredMatch, matchJob } from '../services/jobService';
import { getSuggestions, listResumes, tailorResume } from '../services/resumeService';
import JobDetails from './JobDetails';

vi.mock('../services/jobService', () => ({ matchJob: vi.fn(), getStoredMatch: vi.fn() }));
vi.mock('../services/resumeService', () => ({
  listResumes: vi.fn(),
  tailorResume: vi.fn(),
  getSuggestions: vi.fn(),
}));

const MATCH = {
  jobId: 1,
  resumeId: 1,
  score: 82,
  matchedSkills: ['Java'],
  missingSkills: ['Kafka'],
  nearMatchedSkills: [],
  missingKeywords: [],
  suggestedHeadline: 'Backend Engineer',
  locationFit: { status: 'match', note: 'Remote role.' },
};

const apiError = (status) => Object.assign(new Error(`status ${status}`), { status });

const JOB = {
  id: 1,
  title: 'Backend Engineer',
  companyName: 'Acme',
  location: 'Berlin',
  remoteType: 'remote',
  source: 'greenhouse',
  category: 'Software Engineering',
  sourceUrl: 'https://x.test/1',
  description: 'Line one\nLine two',
  postedAt: null,
  createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
};
const BASE = { id: 3, fileName: 'cv.pdf', isBase: true, baseResumeId: null };

function renderRow(job = JOB) {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/jobs']}>
      <Routes>
        <Route path="/jobs" element={<JobDetails job={job} variant="row" />} />
        <Route path="/resume/:id" element={<p>Tailored page</p>} />
        <Route path="/jobs/:id/interview" element={<p>Interview page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

describe('JobDetails row variant', () => {
  beforeEach(() => {
    vi.mocked(matchJob).mockReset();
    vi.mocked(getStoredMatch).mockReset().mockResolvedValue(null);
    vi.mocked(listResumes).mockReset().mockResolvedValue([BASE]);
    vi.mocked(tailorResume).mockReset();
    vi.mocked(getSuggestions).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows the title as a heading with the category pill, details and an actions group', () => {
    renderRow();
    expect(screen.getByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument();
    expect(screen.getByText('Software Engineering')).toHaveClass('job-chip');
    expect(screen.getByText('Acme · Berlin · Remote')).toBeInTheDocument();
    expect(screen.getByText('Greenhouse')).toBeInTheDocument();
    expect(screen.getByText('Saved 5 minutes ago')).toBeInTheDocument();
    const group = screen.getByRole('group', { name: 'Actions for Backend Engineer' });
    for (const name of [
      'Match with my resume',
      'Tailor my resume',
      'Suggestions',
      /Interview questions/,
      /Track this job/,
      'View details',
    ]) {
      expect(within(group).getByRole('button', { name })).toBeInTheDocument();
    }
    expect(document.querySelector('.job-row__panel')).toBeNull();
  });

  it('shows the match result in a panel under the row', async () => {
    vi.mocked(matchJob).mockResolvedValue(MATCH);
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(await screen.findByText('Strong match')).toBeInTheDocument();
    expect(document.querySelector('.job-row__panel')).toContainElement(
      screen.getByText('Strong match'),
    );
    expect(screen.getByRole('button', { name: 'Match again' })).toBeInTheDocument();
  });

  it('shows the no-resume and error notes in the panel', async () => {
    vi.mocked(matchJob).mockRejectedValueOnce(apiError(409)).mockRejectedValueOnce(apiError(500));
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(await screen.findByText(/Upload a resume first to see your match/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(await screen.findByText(/could not match this job/)).toBeInTheDocument();
  });

  it('tailors the base resume and opens the copy', async () => {
    vi.mocked(tailorResume).mockResolvedValue({ id: 11 });
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'Tailor my resume' }));
    expect(await screen.findByText('Tailored page')).toBeInTheDocument();
    expect(tailorResume).toHaveBeenCalledWith(3, 1);
  });

  it('shows the tailor error in the panel with a retry', async () => {
    vi.mocked(tailorResume).mockRejectedValue(apiError(500));
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'Tailor my resume' }));
    expect(await screen.findByText(/could not tailor your resume/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('toggles suggestions in the panel', async () => {
    vi.mocked(getSuggestions).mockResolvedValue({
      suggestions: [{ type: 'add_skill', text: 'Add Kafka.' }],
    });
    const user = renderRow();
    const toggle = screen.getByRole('button', { name: 'Suggestions' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);
    expect(await screen.findByText('Add Kafka.')).toBeInTheDocument();
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(getSuggestions).toHaveBeenCalledWith(3, 1);
    await user.click(toggle);
    expect(screen.queryByText('Add Kafka.')).not.toBeInTheDocument();
  });

  it('shows a no-resume note for suggestions', async () => {
    vi.mocked(listResumes).mockResolvedValue([]);
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'Suggestions' }));
    expect(await screen.findByText(/upload a resume first to tailor it/i)).toBeInTheDocument();
  });

  it('expands and collapses the details and loads a stored match once', async () => {
    vi.mocked(getStoredMatch).mockResolvedValue(MATCH);
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: 'View details' }));
    expect(screen.getByText(/Line one/).textContent).toBe('Line one\nLine two');
    expect(await screen.findByText('out of 100')).toBeInTheDocument();
    expect(getStoredMatch).toHaveBeenCalledWith(1);
    await user.click(screen.getByRole('button', { name: 'Hide details' }));
    expect(screen.queryByText(/Line one/)).not.toBeInTheDocument();
  });

  it('opens interview questions', async () => {
    const user = renderRow();
    await user.click(screen.getByRole('button', { name: /Interview questions/ }));
    expect(screen.getByText('Interview page')).toBeInTheDocument();
  });

  it('omits id-based actions without an id, and the details button without a description', () => {
    renderRow({ ...JOB, id: undefined, description: undefined });
    expect(screen.queryByRole('button', { name: 'Match with my resume' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'View details' })).not.toBeInTheDocument();
  });
});
