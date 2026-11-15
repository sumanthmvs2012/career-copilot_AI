import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { sourceLabel } from '../utils/relativeTime';
import JobDetails from './JobDetails';

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

describe('JobDetails', () => {
  it('shows the summary, source label, saved time and the coming-soon match line', () => {
    render(<JobDetails job={JOB} />);
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Acme · Berlin · Remote')).toBeInTheDocument();
    expect(screen.getByText('Pasted manually')).toBeInTheDocument();
    const time = screen.getByText('Saved 5 minutes ago');
    expect(time).toHaveAttribute('title', new Date(JOB.createdAt).toLocaleString());
    expect(screen.getByText('Match with your base resume: coming soon')).toBeInTheDocument();
  });

  it('shows posted time and the source name for scraped jobs', () => {
    const postedAt = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString();
    render(<JobDetails job={{ ...JOB, source: 'remoteok', postedAt }} />);
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
    const { container } = render(<JobDetails job={{ ...JOB, locations: [], category: null }} />);
    expect(screen.getByText('Acme · Berlin · Remote')).toBeInTheDocument();
    expect(container.querySelector('.job-chip')).toBeNull();
  });

  it('toggles the description and keeps line breaks', async () => {
    const user = userEvent.setup();
    render(<JobDetails job={JOB} />);
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

  it('keeps the existing branch when a match exists', () => {
    render(<JobDetails job={{ ...JOB, match: { score: 82 } }} />);
    expect(screen.getByText('Match score: 82')).toBeInTheDocument();
    expect(screen.queryByText(/coming soon/)).not.toBeInTheDocument();
  });

  it('is compact on request, with no match line, and omits the toggle without a description', () => {
    render(<JobDetails job={{ ...JOB, description: undefined }} compact />);
    expect(screen.queryByText(/coming soon/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Show description/ })).not.toBeInTheDocument();
  });

  it('offers a Track this job button for jobs with an id', () => {
    render(<JobDetails job={JOB} />);
    expect(screen.getByRole('button', { name: /Track this job/ })).toBeInTheDocument();
  });

  it('links to the posting in a new tab for http and https URLs', () => {
    const sourceUrl = 'https://jobs.lever.co/acme/1';
    render(<JobDetails job={{ ...JOB, source: 'lever', sourceUrl }} />);
    const link = screen.getByRole('link', { name: /View posting/ });
    expect(link).toHaveAttribute('href', sourceUrl);
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByText('Lever')).toBeInTheDocument();
  });

  it.each(['javascript:alert(1)', 'data:text/html,hi', 'ftp://example.com/x', 'not a url', ''])(
    'refuses the link for %j',
    (sourceUrl) => {
      render(<JobDetails job={{ ...JOB, sourceUrl }} />);
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
