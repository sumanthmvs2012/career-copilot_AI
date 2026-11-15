import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardPage from './DashboardPage';
import {
  APPLICATION_STATUSES,
  createApplication,
  isMockApplications,
  listApplications,
  updateApplication,
} from '../services/applicationsService';
import { getJobFacets, listJobs } from '../services/jobService';
import { listResumes } from '../services/resumeService';
import { getProfile } from '../services/profileService';
import { clearToken, getToken, setToken } from '../utils/authToken';

vi.mock('../services/profileService', () => ({ getProfile: vi.fn() }));
vi.mock('../services/jobService', () => ({ listJobs: vi.fn(), getJobFacets: vi.fn() }));
vi.mock('../services/resumeService', () => ({ listResumes: vi.fn() }));
vi.mock('../services/applicationsService', () => ({
  APPLICATION_STATUSES: ['draft', 'applied', 'interviewing', 'offer', 'rejected', 'withdrawn'],
  listApplications: vi.fn(),
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  isMockApplications: vi.fn(),
}));

function renderDashboard() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/profile" element={<p>Profile page</p>} />
        <Route path="/match" element={<p>Match page</p>} />
        <Route path="/jobs" element={<p>Jobs page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function apiError(status) {
  return Object.assign(new Error(`status ${status}`), { status });
}

const FACETS = {
  categories: [
    { value: 'Software Engineering', count: 120 },
    { value: 'Design', count: 4 },
  ],
  countries: [{ value: 'United States', count: 90 }],
  states: [{ value: 'CA', count: 50 }],
  cities: [{ value: 'San Francisco', count: 30 }],
};

const PROFILE = {
  id: 1,
  fullName: 'Jane Doe',
  headline: 'Backend engineer',
  location: 'Berlin',
  remoteOk: true,
  autoSubmitEnabled: false,
};

describe('DashboardPage', () => {
  beforeEach(() => {
    setToken('jwt-token');
    vi.mocked(getProfile).mockReset();
    vi.mocked(listApplications).mockReset().mockResolvedValue([]);
    vi.mocked(isMockApplications).mockReset().mockReturnValue(false);
    vi.mocked(createApplication).mockReset();
    vi.mocked(updateApplication).mockReset();
    vi.mocked(listResumes).mockReset().mockResolvedValue([]);
    vi.mocked(listJobs).mockReset().mockResolvedValue([]);
    vi.mocked(getJobFacets).mockReset().mockResolvedValue(FACETS);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    clearToken();
  });

  it('shows loading states while requests are pending', () => {
    vi.mocked(getProfile).mockReturnValue(new Promise(() => {}));
    vi.mocked(listApplications).mockReturnValue(new Promise(() => {}));
    renderDashboard();

    expect(screen.getByText('Loading your profile…')).toBeInTheDocument();
    expect(screen.getByText('Loading applications…')).toBeInTheDocument();
  });

  it('shows the profile and the empty applications table on success', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Jane Doe' })).toBeInTheDocument();
    expect(screen.getByText('Backend engineer')).toBeInTheDocument();
    expect(screen.getByText('Berlin')).toBeInTheDocument();
    expect(screen.getByText('Open to remote work')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit profile' })).toHaveAttribute('href', '/profile');

    expect(await screen.findByText('No applications yet')).toBeInTheDocument();
    for (const name of ['Job', 'Status', 'Applied', 'Email sent', 'When']) {
      expect(screen.getByRole('columnheader', { name })).toBeInTheDocument();
    }
  });

  it('renders application rows when the service returns data', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(listApplications).mockResolvedValue([
      {
        id: 7,
        jobTitle: 'Engineer',
        company: 'Acme',
        status: 'applied',
        appliedAt: '2026-11-01T10:00:00Z',
        emailSent: true,
        emailSentAt: '2026-11-01T10:05:00Z',
      },
    ]);
    renderDashboard();

    expect(await screen.findByText('Engineer')).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('applied')).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
    expect(screen.queryByText('No applications yet')).not.toBeInTheDocument();
    expect(screen.queryByText('Demo data')).not.toBeInTheDocument();
  });

  describe('application status and tracking', () => {
    const APP = {
      id: 7,
      jobId: 1,
      jobTitle: 'Engineer',
      company: 'Acme',
      status: 'draft',
      appliedAt: null,
      emailSent: false,
      emailSentAt: null,
    };

    it('lists the six statuses and saves a change through updateApplication', async () => {
      vi.mocked(getProfile).mockResolvedValue(PROFILE);
      vi.mocked(listApplications).mockResolvedValue([APP]);
      vi.mocked(updateApplication).mockResolvedValue({ ...APP, status: 'interviewing' });
      const user = renderDashboard();

      const select = await screen.findByLabelText('Change status for Engineer');
      expect(within(select).getAllByRole('option')).toHaveLength(APPLICATION_STATUSES.length);
      await user.selectOptions(select, 'interviewing');

      expect(updateApplication).toHaveBeenCalledWith(7, { status: 'interviewing' });
      await waitFor(() => expect(select).toHaveValue('interviewing'));
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('shows a per-row error and keeps the old status when saving fails', async () => {
      vi.spyOn(console, 'error').mockImplementation(() => {});
      vi.mocked(getProfile).mockResolvedValue(PROFILE);
      vi.mocked(listApplications).mockResolvedValue([APP]);
      vi.mocked(updateApplication).mockRejectedValue(apiError(500));
      const user = renderDashboard();

      const select = await screen.findByLabelText('Change status for Engineer');
      await user.selectOptions(select, 'offer');

      expect(
        await screen.findByText('We could not save the status. Please try again.'),
      ).toBeInTheDocument();
      expect(select).toHaveValue('draft');
    });

    it('tracks a latest job and reloads the applications table', async () => {
      vi.mocked(getProfile).mockResolvedValue(PROFILE);
      vi.mocked(listJobs).mockResolvedValue([{ id: 55, title: 'Dev', source: 'lever' }]);
      vi.mocked(listApplications).mockResolvedValueOnce([]).mockResolvedValue([APP]);
      vi.mocked(createApplication).mockResolvedValue(APP);
      const user = renderDashboard();

      await user.click(await screen.findByRole('button', { name: /Track this job/ }));

      expect(createApplication).toHaveBeenCalledWith({ jobId: 55, status: 'draft' });
      expect(await screen.findByText('Tracked')).toBeInTheDocument();
      expect(await screen.findByText('Engineer')).toBeInTheDocument();
    });
  });

  it('shows the Demo data badge, statuses, email flags and relative times for mock data', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(isMockApplications).mockReturnValue(true);
    const ago = (days) => new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
    vi.mocked(listApplications).mockResolvedValue([
      {
        id: 1,
        jobTitle: 'A',
        company: 'X',
        status: 'offer',
        appliedAt: ago(5),
        emailSent: true,
        emailSentAt: ago(5),
      },
      {
        id: 2,
        jobTitle: 'B',
        company: 'Y',
        status: 'rejected',
        appliedAt: ago(9),
        emailSent: false,
        emailSentAt: null,
      },
    ]);
    renderDashboard();

    expect(await screen.findByText('Demo data')).toBeInTheDocument();
    expect(screen.getByText('offer')).toBeInTheDocument();
    expect(screen.getByText('rejected')).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
    expect(screen.getByText('No')).toBeInTheDocument();
    expect(screen.getByText('5 days ago')).toBeInTheDocument();
  });

  it('prompts to create a profile on 404', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(404));
    renderDashboard();

    expect(await screen.findByText('You have not set up your profile yet.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create your profile' })).toHaveAttribute(
      'href',
      '/profile',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an error and retries', async () => {
    vi.mocked(getProfile).mockRejectedValueOnce(apiError(500)).mockResolvedValue(PROFILE);
    const user = renderDashboard();

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your profile');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'Jane Doe' })).toBeInTheDocument();
  });

  it('shows an error when applications fail to load', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(listApplications).mockRejectedValue(apiError(500));
    renderDashboard();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not load your applications',
    );
  });

  it('redirects to /login and clears the token on 401', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(401));
    renderDashboard();

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(getToken()).toBeNull();
  });

  describe('resume card', () => {
    const RESUME = {
      id: 3,
      fileName: 'jane-cv.pdf',
      isBase: true,
      textLength: 900,
      createdAt: '2026-11-10T10:00:00Z',
    };

    beforeEach(() => {
      vi.mocked(getProfile).mockResolvedValue(PROFILE);
    });

    it('shows a loading state', () => {
      vi.mocked(listResumes).mockReturnValue(new Promise(() => {}));
      renderDashboard();
      expect(screen.getByText('Loading your resume…')).toBeInTheDocument();
    });

    it('prompts to upload when there is no resume', async () => {
      renderDashboard();
      expect(await screen.findByText('You have not uploaded a resume yet.')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Upload your resume' })).toHaveAttribute(
        'href',
        '/resume',
      );
    });

    it('shows the base resume, the count and a manage link', async () => {
      const older = { ...RESUME, id: 2, fileName: 'old-cv.pdf', isBase: false };
      vi.mocked(listResumes).mockResolvedValue([RESUME, older]);
      renderDashboard();
      expect(await screen.findByText('jane-cv.pdf')).toBeInTheDocument();
      expect(screen.queryByText('old-cv.pdf')).not.toBeInTheDocument();
      expect(screen.getByText('2 of 2 resumes')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Manage resumes' })).toHaveAttribute(
        'href',
        '/resume',
      );
    });

    it('shows 1 of 2 resumes for a single resume', async () => {
      vi.mocked(listResumes).mockResolvedValue([RESUME]);
      renderDashboard();
      expect(await screen.findByText('1 of 2 resumes')).toBeInTheDocument();
    });

    it('shows an error and retries', async () => {
      vi.mocked(listResumes).mockRejectedValueOnce(apiError(500)).mockResolvedValue([RESUME]);
      const user = renderDashboard();
      expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your resume');
      await user.click(screen.getByRole('button', { name: 'Try again' }));
      expect(await screen.findByText('jane-cv.pdf')).toBeInTheDocument();
    });

    it('redirects to /login on 401', async () => {
      vi.mocked(listResumes).mockRejectedValue(apiError(401));
      renderDashboard();
      expect(await screen.findByText('Login page')).toBeInTheDocument();
    });
  });

  it('has no pasted-jobs card', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    renderDashboard();
    await screen.findByText('No applications yet');
    expect(screen.queryByRole('heading', { name: 'Jobs' })).not.toBeInTheDocument();
    expect(listJobs).not.toHaveBeenCalledWith({ scope: 'pasted' });
  });

  describe('latest jobs card', () => {
    const SCRAPED = [
      {
        id: 91,
        title: 'Platform Engineer',
        companyName: 'Stripe',
        location: 'Dublin',
        remoteType: 'remote',
        source: 'greenhouse',
        sourceUrl: 'https://boards.greenhouse.io/stripe/jobs/91',
        description: 'Run the platform.',
        postedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        createdAt: new Date().toISOString(),
      },
    ];

    beforeEach(() => {
      vi.mocked(getProfile).mockResolvedValue(PROFILE);
    });

    it('asks for 10 scraped jobs for Latest jobs', async () => {
      renderDashboard();
      await screen.findByText('No applications yet');
      expect(listJobs).toHaveBeenCalledWith({ scope: 'scraped', limit: 10 });
    });

    it('links to the full jobs page from the Latest jobs card', async () => {
      const user = renderDashboard();
      await screen.findByText('No applications yet');
      await user.click(screen.getByRole('link', { name: 'Browse all jobs' }));
      expect(screen.getByText('Jobs page')).toBeInTheDocument();
    });

    it('links to /match from the Latest jobs card', async () => {
      const user = renderDashboard();
      await screen.findByText('No applications yet');
      expect(screen.getByRole('link', { name: 'Match a job' })).toHaveAttribute('href', '/match');
      await user.click(screen.getByRole('link', { name: 'Match a job' }));
      expect(screen.getByText('Match page')).toBeInTheDocument();
    });

    it('has the four compact filters and refetches only the jobs when one changes', async () => {
      vi.mocked(listJobs).mockResolvedValue(SCRAPED);
      const user = renderDashboard();
      await screen.findByText('Platform Engineer');
      expect(getJobFacets).toHaveBeenCalledWith({
        scope: 'scraped',
        category: '',
        country: '',
        state: '',
        city: '',
      });
      for (const label of ['Category', 'Country', 'State', 'City']) {
        expect(screen.getByLabelText(label)).toBeInTheDocument();
      }
      expect(screen.getByLabelText('State')).toBeDisabled();
      expect(screen.getAllByRole('option', { name: 'Select a country first' })).toHaveLength(2);

      await user.selectOptions(screen.getByLabelText('Country'), 'United States');
      await waitFor(() =>
        expect(listJobs).toHaveBeenLastCalledWith({
          scope: 'scraped',
          limit: 10,
          country: 'United States',
        }),
      );
      expect(screen.getByLabelText('State')).toBeEnabled();
      await user.selectOptions(screen.getByLabelText('Category'), 'Design');
      await waitFor(() =>
        expect(listJobs).toHaveBeenLastCalledWith({
          scope: 'scraped',
          limit: 10,
          category: 'Design',
          country: 'United States',
        }),
      );
      expect(getProfile).toHaveBeenCalledTimes(1);
    });

    it('narrows the dashboard facets when a category and a country are chosen', async () => {
      vi.mocked(listJobs).mockResolvedValue(SCRAPED);
      vi.mocked(getJobFacets).mockImplementation(async ({ category }) =>
        category === 'Design'
          ? {
              ...FACETS,
              countries: [{ value: 'Canada', count: 2 }],
              states: [{ value: 'ON', count: 2 }],
            }
          : FACETS,
      );
      const user = renderDashboard();
      await screen.findByRole('option', { name: 'United States (90)' });
      await user.selectOptions(screen.getByLabelText('Category'), 'Design');
      await waitFor(() =>
        expect(
          screen.queryByRole('option', { name: 'United States (90)' }),
        ).not.toBeInTheDocument(),
      );
      await user.selectOptions(screen.getByLabelText('Country'), 'Canada');
      await waitFor(() =>
        expect(getJobFacets).toHaveBeenLastCalledWith({
          scope: 'scraped',
          category: 'Design',
          country: 'Canada',
          state: '',
          city: '',
        }),
      );
      expect(await screen.findByRole('option', { name: 'ON (2)' })).toBeInTheDocument();
      expect(screen.queryByRole('option', { name: 'CA (50)' })).not.toBeInTheDocument();
    });

    it('shows the filtered empty state and Clear filters restores the list', async () => {
      vi.mocked(listJobs).mockImplementation(async ({ category }) => (category ? [] : SCRAPED));
      const user = renderDashboard();
      await screen.findByText('Platform Engineer');
      await user.selectOptions(screen.getByLabelText('Category'), 'Design');
      expect(await screen.findByText('No jobs match these filters.')).toBeInTheDocument();

      const buttons = screen.getAllByRole('button', { name: 'Clear filters' });
      await user.click(buttons[buttons.length - 1]);
      expect(await screen.findByText('Platform Engineer')).toBeInTheDocument();
      expect(screen.getByLabelText('Category')).toHaveValue('');
    });

    it('ignores a stale response after the filter changes', async () => {
      let resolveSlow;
      vi.mocked(listJobs)
        .mockReturnValueOnce(new Promise((resolve) => (resolveSlow = resolve)))
        .mockResolvedValue([{ ...SCRAPED[0], title: 'Fresh job' }]);
      const user = renderDashboard();
      await screen.findByRole('option', { name: 'Design (4)' });
      await user.selectOptions(screen.getByLabelText('Category'), 'Design');
      expect(await screen.findByText('Fresh job')).toBeInTheDocument();
      await act(async () => resolveSlow(SCRAPED));
      expect(screen.queryByText('Platform Engineer')).not.toBeInTheDocument();
      expect(screen.getByText('Fresh job')).toBeInTheDocument();
    });

    it('keeps the jobs list when facets fail', async () => {
      vi.mocked(getJobFacets).mockRejectedValue(apiError(500));
      vi.mocked(listJobs).mockResolvedValue(SCRAPED);
      renderDashboard();
      expect(await screen.findByText('Platform Engineer')).toBeInTheDocument();
      expect(await screen.findByText(/Filter options could not be loaded/)).toBeInTheDocument();
    });

    it('shows a loading state', () => {
      vi.mocked(listJobs).mockReturnValue(new Promise(() => {}));
      renderDashboard();
      expect(screen.getByText('Loading latest jobs…')).toBeInTheDocument();
    });

    it('shows the empty message', async () => {
      renderDashboard();
      expect(
        await screen.findByText(
          'No jobs collected yet. New postings appear here after the hourly scan.',
        ),
      ).toBeInTheDocument();
    });

    it('shows scraped jobs with source, posted time and a safe external link', async () => {
      vi.mocked(listJobs).mockImplementation(async ({ scope }) =>
        scope === 'scraped' ? SCRAPED : [],
      );
      renderDashboard();
      expect(await screen.findByText('Platform Engineer')).toBeInTheDocument();
      expect(screen.getByText('Greenhouse')).toBeInTheDocument();
      expect(screen.getByText('Posted 3 days ago')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Match with my resume' })).toBeInTheDocument();
      const link = screen.getByRole('link', { name: /View posting/ });
      expect(link).toHaveAttribute('href', SCRAPED[0].sourceUrl);
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    });

    it('shows an error and retries only that card', async () => {
      vi.mocked(listJobs).mockImplementation(async ({ scope }) => {
        if (scope === 'scraped') throw apiError(500);
        return [];
      });
      const user = renderDashboard();
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'We could not load the latest jobs',
      );

      vi.mocked(listJobs).mockImplementation(async ({ scope }) =>
        scope === 'scraped' ? SCRAPED : [],
      );
      await user.click(screen.getByRole('button', { name: 'Try again' }));
      expect(await screen.findByText('Platform Engineer')).toBeInTheDocument();
    });

    it('redirects to /login on 401', async () => {
      vi.mocked(listJobs).mockImplementation(async ({ scope }) => {
        if (scope === 'scraped') throw apiError(401);
        return [];
      });
      renderDashboard();
      expect(await screen.findByText('Login page')).toBeInTheDocument();
      expect(getToken()).toBeNull();
    });
  });

  it('logs out: clears the token and goes to /login', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    const user = renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(getToken()).toBeNull();
  });
});
