import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MatchJobPage from './MatchJobPage';
import { listJobs, matchJob } from '../services/jobService';

vi.mock('../services/jobService', () => ({ listJobs: vi.fn(), matchJob: vi.fn() }));

function renderPage() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/match']}>
      <Routes>
        <Route path="/match" element={<MatchJobPage />} />
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/profile" element={<p>Profile page</p>} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function apiError(status, data = null) {
  return Object.assign(new Error(`status ${status}`), { status, data });
}

const DESCRIPTION = 'Build and run backend services in Java for our platform.';
const SAVED = {
  job: {
    id: 5,
    title: 'Backend Engineer',
    companyName: 'Acme',
    location: 'Berlin',
    remoteType: 'hybrid',
    source: 'manual',
    sourceUrl: null,
    description: DESCRIPTION,
    postedAt: null,
    createdAt: '2026-11-12T10:00:00Z',
  },
  match: null,
  message: 'Matching is not available yet; your job is saved.',
};

async function fillDescription(user, text = DESCRIPTION) {
  await user.type(screen.getByLabelText('Job description'), text);
}

describe('MatchJobPage', () => {
  beforeEach(() => {
    vi.mocked(listJobs).mockReset().mockResolvedValue([]);
    vi.mocked(matchJob).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the form fields and a dashboard link', async () => {
    renderPage();
    for (const label of [
      'Job title (optional)',
      'Company (optional)',
      'Location (optional)',
      'Remote type',
      'Job URL (optional)',
      'Job description',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Remote type')).toHaveValue('');
    expect(screen.getByText('0 / 20000 characters (at least 20)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to dashboard' })).toHaveAttribute(
      'href',
      '/dashboard',
    );
    await screen.findByText('You have not pasted any jobs yet.');
  });

  it('updates the character count as the user types', async () => {
    const user = renderPage();
    await user.type(screen.getByLabelText('Job description'), 'hello');
    expect(screen.getByText('5 / 20000 characters (at least 20)')).toBeInTheDocument();
    await screen.findByText('You have not pasted any jobs yet.');
  });

  it('shows a loading state, then the empty state for the jobs list', async () => {
    renderPage();
    expect(screen.getByText('Loading your jobs…')).toBeInTheDocument();
    expect(await screen.findByText('You have not pasted any jobs yet.')).toBeInTheDocument();
  });

  it('lists the pasted jobs', async () => {
    vi.mocked(listJobs).mockResolvedValue([SAVED.job]);
    renderPage();
    const list = await screen.findByRole('list');
    expect(within(list).getByText('Backend Engineer')).toBeInTheDocument();
    expect(within(list).getByText('Acme · Berlin · Hybrid')).toBeInTheDocument();
    expect(within(list).getByText('Pasted manually')).toBeInTheDocument();
    expect(within(list).getByText(/^Saved .+ ago$/)).toBeInTheDocument();
    expect(
      within(list).getByText('Match with your base resume: coming soon'),
    ).toBeInTheDocument();
  });

  it('lists only pasted jobs, never scraped ones', async () => {
    renderPage();
    await screen.findByText('You have not pasted any jobs yet.');
    expect(listJobs).toHaveBeenCalledWith({ scope: 'pasted' });
  });

  it('toggles the full description of a listed job', async () => {
    vi.mocked(listJobs).mockResolvedValue([SAVED.job]);
    const user = renderPage();
    const list = await screen.findByRole('list');
    expect(within(list).queryByText(DESCRIPTION)).not.toBeInTheDocument();
    await user.click(within(list).getByRole('button', { name: 'Show description' }));
    expect(within(list).getByText(DESCRIPTION)).toBeInTheDocument();
    await user.click(within(list).getByRole('button', { name: 'Hide description' }));
    expect(within(list).queryByText(DESCRIPTION)).not.toBeInTheDocument();
  });

  it('shows an error for the jobs list and retries', async () => {
    vi.mocked(listJobs).mockRejectedValueOnce(apiError(500)).mockResolvedValue([SAVED.job]);
    const user = renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your jobs');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
  });

  it('redirects to /login when listing jobs returns 401', async () => {
    vi.mocked(listJobs).mockRejectedValue(apiError(401));
    renderPage();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('validates the description before calling the service', async () => {
    const user = renderPage();
    await user.click(screen.getByRole('button', { name: 'Match this job' }));
    expect(screen.getByText('Job description is required.')).toBeInTheDocument();
    expect(matchJob).not.toHaveBeenCalled();
    await screen.findByText('You have not pasted any jobs yet.');
  });

  it('shows the saving state, then the result card with the not-available note', async () => {
    let resolveMatch;
    vi.mocked(matchJob).mockReturnValue(new Promise((resolve) => (resolveMatch = resolve)));
    const user = renderPage();
    await user.type(screen.getByLabelText('Job title (optional)'), 'Backend Engineer');
    await user.selectOptions(screen.getByLabelText('Remote type'), 'hybrid');
    await fillDescription(user);
    await user.click(screen.getByRole('button', { name: 'Match this job' }));

    expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled();
    expect(matchJob).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Backend Engineer',
        remoteType: 'hybrid',
        description: DESCRIPTION,
      }),
    );

    resolveMatch(SAVED);
    const card = (await screen.findByRole('heading', { name: 'Job saved' })).closest('section');
    expect(within(card).getByText('Backend Engineer')).toBeInTheDocument();
    expect(within(card).getByText('Acme · Berlin · Hybrid')).toBeInTheDocument();
    expect(
      within(card).getByText('Matching is not available yet; your job is saved.'),
    ).toBeInTheDocument();
    expect(within(card).getByText('Match with your base resume: coming soon')).toBeInTheDocument();
    // The new job joins the list and the form is cleared for the next paste.
    expect(screen.getByRole('list')).toHaveTextContent('Backend Engineer');
    expect(screen.getByLabelText('Job description')).toHaveValue('');
  });

  it('shows field errors from a 400', async () => {
    vi.mocked(matchJob).mockRejectedValue(
      apiError(400, { status: 400, fieldErrors: { title: 'Title is too long.' } }),
    );
    const user = renderPage();
    await fillDescription(user);
    await user.click(screen.getByRole('button', { name: 'Match this job' }));

    expect(await screen.findByText('Title is too long.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Please fix the highlighted fields.');
    expect(screen.getByLabelText('Job description')).toHaveValue(DESCRIPTION);
  });

  it('points to the profile page on a 404', async () => {
    vi.mocked(matchJob).mockRejectedValue(apiError(404));
    const user = renderPage();
    await fillDescription(user);
    await user.click(screen.getByRole('button', { name: 'Match this job' }));

    expect(await screen.findByText(/create your profile first/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
      'href',
      '/profile',
    );
  });

  it('shows a generic error on a server failure and never logs job text', async () => {
    vi.mocked(matchJob).mockRejectedValue(apiError(500));
    const user = renderPage();
    await fillDescription(user);
    await user.click(screen.getByRole('button', { name: 'Match this job' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not save this job');
    expect(JSON.stringify(console.error.mock.calls)).not.toContain('Java');
  });

  it('redirects to /login on a 401 while submitting', async () => {
    vi.mocked(matchJob).mockRejectedValue(apiError(401));
    const user = renderPage();
    await fillDescription(user);
    await user.click(screen.getByRole('button', { name: 'Match this job' }));
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });
});
