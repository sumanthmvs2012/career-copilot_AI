import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getStoredMatch, matchJob } from '../services/jobService';
import { sourceLabel } from '../utils/relativeTime';
import JobDetails from './JobDetails';

vi.mock('../services/jobService', () => ({ matchJob: vi.fn(), getStoredMatch: vi.fn() }));

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
  createdAt: '2026-11-15T10:00:00Z',
};

function apiError(status, data = null) {
  return Object.assign(new Error(`status ${status}`), { status, data });
}

const JOB = {
  id: 1,
  title: 'Backend Engineer',
  companyName: 'Acme',
  location: 'Berlin',
  remoteType: 'remote',
  source: 'manual',
  description: 'Line one\nLine two',
  postedAt: null,
  createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
};

function renderJob(ui) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe('JobDetails', () => {
  beforeEach(() => {
    vi.mocked(matchJob).mockReset();
    vi.mocked(getStoredMatch).mockReset().mockResolvedValue(null);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows the summary, source label, saved time and the match button', () => {
    renderJob(<JobDetails job={JOB} />);
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Acme · Berlin · Remote')).toBeInTheDocument();
    expect(screen.getByText('Pasted manually')).toBeInTheDocument();
    const time = screen.getByText('Saved 5 minutes ago');
    expect(time).toHaveAttribute('title', new Date(JOB.createdAt).toLocaleString());
    expect(screen.getByRole('button', { name: 'Match with my resume' })).toBeInTheDocument();
  });

  it('shows posted time and the source name for scraped jobs', () => {
    const postedAt = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString();
    renderJob(<JobDetails job={{ ...JOB, source: 'remoteok', postedAt }} />);
    expect(screen.getByText('remoteok')).toBeInTheDocument();
    expect(screen.getByText('Posted 3 days ago')).toBeInTheDocument();
  });

  it('shows the category chip and structured locations joined by a dot', () => {
    render(
      <JobDetails
        job={{
          ...JOB,
          category: 'Software Engineering',
          locations: [
            { city: 'San Francisco', state: 'CA', country: 'United States' },
            { city: null, state: null, country: 'Canada' },
          ],
        }}
      />,
    );
    expect(screen.getByText('Software Engineering')).toHaveClass('job-chip');
    expect(
      screen.getByText('Acme · San Francisco, CA, United States · Canada · Remote'),
    ).toBeInTheDocument();
  });

  it('falls back to the raw location without structured locations or a category', () => {
    const { container } = renderJob(<JobDetails job={{ ...JOB, locations: [], category: null }} />);
    expect(screen.getByText('Acme · Berlin · Remote')).toBeInTheDocument();
    expect(container.querySelector('.job-chip')).toBeNull();
  });

  it('toggles the description and keeps line breaks', async () => {
    const user = userEvent.setup();
    renderJob(<JobDetails job={JOB} />);
    const toggle = screen.getByRole('button', { name: 'Show description' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);
    const body = screen.getByText(/Line one/);
    expect(body.textContent).toBe('Line one\nLine two');
    expect(body).toHaveClass('job-item__description');
    expect(screen.getByRole('button', { name: 'Hide description' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    await user.click(screen.getByRole('button', { name: 'Hide description' }));
    expect(screen.queryByText(/Line one/)).not.toBeInTheDocument();
  });

  it('shows a result passed in with the job', () => {
    renderJob(<JobDetails job={{ ...JOB, match: MATCH }} />);
    expect(screen.getByText('out of 100')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Match again' })).toBeInTheDocument();
  });

  it('runs a match and shows the result inline', async () => {
    const user = userEvent.setup();
    let resolve;
    vi.mocked(matchJob).mockReturnValue(new Promise((r) => (resolve = r)));
    renderJob(<JobDetails job={JOB} />);
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(screen.getByRole('button', { name: 'Matching…' })).toBeDisabled();
    expect(matchJob).toHaveBeenCalledWith(1);
    resolve(MATCH);
    expect(await screen.findByText('Strong match')).toBeInTheDocument();
    expect(screen.getByText('Kafka')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Track this job/ })).toBeInTheDocument();
  });

  it('shows the 409 message with a link to /resume', async () => {
    const user = userEvent.setup();
    vi.mocked(matchJob).mockRejectedValue(
      apiError(409, { message: 'Upload a resume first to see your match.' }),
    );
    renderJob(<JobDetails job={JOB} />);
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(
      await screen.findByText(/Upload a resume first to see your match\./),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your resumes' })).toHaveAttribute(
      'href',
      '/resume',
    );
  });

  it('shows a short error for other failures and allows a retry', async () => {
    const user = userEvent.setup();
    vi.mocked(matchJob).mockRejectedValueOnce(apiError(500)).mockResolvedValueOnce(MATCH);
    renderJob(<JobDetails job={JOB} />);
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(
      await screen.findByText('We could not match this job. Please try again.'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Match with my resume' }));
    expect(await screen.findByText('Strong match')).toBeInTheDocument();
  });

  it('loads a stored result when the description is expanded', async () => {
    const user = userEvent.setup();
    vi.mocked(getStoredMatch).mockResolvedValue(MATCH);
    renderJob(<JobDetails job={JOB} />);
    expect(screen.queryByText('out of 100')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Show description' }));
    expect(getStoredMatch).toHaveBeenCalledWith(1);
    expect(await screen.findByText('out of 100')).toBeInTheDocument();
  });

  it('shows nothing extra when no stored result exists (404 resolves to null)', async () => {
    const user = userEvent.setup();
    renderJob(<JobDetails job={JOB} />);
    await user.click(screen.getByRole('button', { name: 'Show description' }));
    expect(getStoredMatch).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('out of 100')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Match with my resume' })).toBeInTheDocument();
  });

  it('ignores a failed stored-result lookup', async () => {
    const user = userEvent.setup();
    vi.mocked(getStoredMatch).mockRejectedValue(apiError(500));
    renderJob(<JobDetails job={JOB} />);
    await user.click(screen.getByRole('button', { name: 'Show description' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('is compact on request, with no match line, and omits the toggle without a description', () => {
    renderJob(<JobDetails job={{ ...JOB, description: undefined }} compact />);
    expect(screen.queryByRole('button', { name: 'Match with my resume' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Show description/ })).not.toBeInTheDocument();
  });

  it('offers a Track this job button for jobs with an id', () => {
    renderJob(<JobDetails job={JOB} />);
    expect(screen.getByRole('button', { name: /Track this job/ })).toBeInTheDocument();
  });

  it('links to the posting in a new tab for http and https URLs', () => {
    const sourceUrl = 'https://jobs.lever.co/acme/1';
    renderJob(<JobDetails job={{ ...JOB, source: 'lever', sourceUrl }} />);
    const link = screen.getByRole('link', { name: /View posting/ });
    expect(link).toHaveAttribute('href', sourceUrl);
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByText('Lever')).toBeInTheDocument();
  });

  it.each(['javascript:alert(1)', 'data:text/html,hi', 'ftp://example.com/x', 'not a url', ''])(
    'refuses the link for %j',
    (sourceUrl) => {
      renderJob(<JobDetails job={{ ...JOB, sourceUrl }} />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    },
  );

  it('labels sources', () => {
    expect(sourceLabel('greenhouse')).toBe('Greenhouse');
    expect(sourceLabel('lever')).toBe('Lever');
    expect(sourceLabel('manual')).toBe('Pasted manually');
    expect(sourceLabel('adzuna')).toBe('adzuna');
    expect(sourceLabel(null)).toBe('');
  });
});
