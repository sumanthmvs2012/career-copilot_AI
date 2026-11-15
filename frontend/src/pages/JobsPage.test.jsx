import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import JobsPage from './JobsPage';
import { getJobFacets, listJobs } from '../services/jobService';
import { clearToken, getToken, setToken } from '../utils/authToken';

vi.mock('../services/jobService', () => ({ listJobs: vi.fn(), getJobFacets: vi.fn() }));

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="search">{location.search}</p>;
}

function renderJobs(path = '/jobs') {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={[path]}>
      <LocationProbe />
      <Routes>
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function makeJobs(count, start = 0) {
  return Array.from({ length: count }, (_, i) => ({
    id: start + i + 1,
    title: `Job ${start + i + 1}`,
    companyName: 'Acme',
    location: 'Berlin',
    remoteType: 'remote',
    source: 'greenhouse',
    sourceUrl: `https://example.com/${start + i + 1}`,
    description: 'Do things.',
    postedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  }));
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const FACETS = {
  categories: [
    { value: 'Software Engineering', count: 120 },
    { value: 'Design', count: 4 },
  ],
  countries: [
    { value: 'United States', count: 90 },
    { value: 'Canada', count: 10 },
  ],
  states: [{ value: 'CA', count: 50 }],
  cities: [{ value: 'San Francisco', count: 30 }],
};

const urlParams = () =>
  Object.fromEntries(new URLSearchParams(screen.getByTestId('search').textContent));

const lastCall =() => vi.mocked(listJobs).mock.calls.at(-1)[0];

describe('JobsPage', () => {
  beforeEach(() => {
    setToken('jwt-token');
    vi.mocked(listJobs).mockReset().mockResolvedValue(makeJobs(3));
    vi.mocked(getJobFacets).mockReset().mockResolvedValue(FACETS);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    clearToken();
  });

  it('shows a loading state while the request is pending', () => {
    vi.mocked(listJobs).mockReturnValue(new Promise(() => {}));
    renderJobs();
    expect(screen.getByRole('heading', { name: 'Jobs' })).toBeInTheDocument();
    expect(screen.getByText('Loading jobs…')).toBeInTheDocument();
  });

  it('lists jobs with the first page request and no Load more under 20 results', async () => {
    renderJobs();
    expect(await screen.findByText('Job 1')).toBeInTheDocument();
    expect(screen.getByText('Showing 3 jobs')).toBeInTheDocument();
    expect(listJobs).toHaveBeenCalledWith(
      expect.objectContaining({ limit: 20, offset: 0, scope: '', q: '' }),
    );
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
    expect(screen.getAllByText(/coming soon/)).toHaveLength(3);
  });

  it('has a labelled control for every filter and a back link', async () => {
    renderJobs();
    await screen.findByText('Job 1');
    for (const label of [
      'Search',
      'Location',
      'Source',
      'Remote type',
      'Posted within',
      'Scope',
      'Category',
      'Country',
      'State',
      'City',
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByRole('link', { name: 'Back to dashboard' })).toHaveAttribute(
      'href',
      '/dashboard',
    );
  });

  it('sends select filters immediately and keeps them in the URL', async () => {
    const user = renderJobs();
    await screen.findByText('Job 1');

    await user.selectOptions(screen.getByLabelText('Source'), 'lever');
    await user.selectOptions(screen.getByLabelText('Remote type'), 'hybrid');
    await user.selectOptions(screen.getByLabelText('Posted within'), '7');
    await user.selectOptions(screen.getByLabelText('Scope'), 'scraped');

    await waitFor(() =>
      expect(lastCall()).toMatchObject({
        source: 'lever',
        remoteType: 'hybrid',
        postedWithin: '7',
        scope: 'scraped',
        offset: 0,
      }),
    );
    const params = new URLSearchParams(screen.getByTestId('search').textContent);
    expect(Object.fromEntries(params)).toEqual({
      source: 'lever',
      remoteType: 'hybrid',
      postedWithin: '7',
      scope: 'scraped',
    });
  });

  it('reads filters from the URL on load', async () => {
    renderJobs(
      '/jobs?q=java&source=ashby&remoteType=remote&postedWithin=30&location=Dublin&scope=pasted',
    );
    await screen.findByText('Job 1');
    expect(listJobs).toHaveBeenCalledTimes(1);
    expect(lastCall()).toMatchObject({
      q: 'java',
      source: 'ashby',
      remoteType: 'remote',
      postedWithin: '30',
      location: 'Dublin',
      scope: 'pasted',
    });
    expect(screen.getByLabelText('Search')).toHaveValue('java');
    expect(screen.getByLabelText('Location')).toHaveValue('Dublin');
    expect(screen.getByLabelText('Source')).toHaveValue('ashby');
  });

  it('ignores unknown select values in the URL', async () => {
    renderJobs('/jobs?source=nope&postedWithin=999');
    await screen.findByText('Job 1');
    expect(lastCall()).toMatchObject({ source: '', postedWithin: '' });
  });

  it('debounces typing by 300ms into one request', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: false });
    renderJobs();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByText('Job 1')).toBeInTheDocument();
    vi.mocked(listJobs).mockClear();

    for (const text of ['r', 'ru', 'rus', 'rust']) {
      fireEvent.change(screen.getByLabelText('Search'), { target: { value: text } });
    }
    expect(listJobs).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(250);
    });
    expect(listJobs).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(listJobs).toHaveBeenCalledTimes(1);
    expect(lastCall()).toMatchObject({ q: 'rust', offset: 0 });
    expect(screen.getByTestId('search')).toHaveTextContent('?q=rust');
  });

  it('trims the location text before searching', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: false });
    renderJobs();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    fireEvent.change(screen.getByLabelText('Location'), { target: { value: '  Paris ' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(lastCall()).toMatchObject({ location: 'Paris' });
  });

  it('Clear filters resets inputs, URL and offset', async () => {
    const user = renderJobs('/jobs?q=java&source=lever&location=Dublin');
    await screen.findByText('Job 1');

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));

    await waitFor(() =>
      expect(lastCall()).toMatchObject({ q: '', source: '', location: '', offset: 0 }),
    );
    expect(screen.getByLabelText('Search')).toHaveValue('');
    expect(screen.getByLabelText('Location')).toHaveValue('');
    expect(screen.getByLabelText('Source')).toHaveValue('');
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('loads more with the next offset while a full page came back', async () => {
    vi.mocked(listJobs)
      .mockResolvedValueOnce(makeJobs(20))
      .mockResolvedValueOnce(makeJobs(20, 20))
      .mockResolvedValueOnce(makeJobs(5, 40));
    const user = renderJobs();
    expect(await screen.findByText('Showing 20 jobs')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Load more' }));
    expect(await screen.findByText('Showing 40 jobs')).toBeInTheDocument();
    expect(lastCall()).toMatchObject({ limit: 20, offset: 20 });
    expect(screen.getByText('Job 40')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Load more' }));
    expect(await screen.findByText('Showing 45 jobs')).toBeInTheDocument();
    expect(lastCall()).toMatchObject({ offset: 40 });
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
  });

  it('shows an error when loading more fails and keeps the list', async () => {
    vi.mocked(listJobs)
      .mockResolvedValueOnce(makeJobs(20))
      .mockRejectedValueOnce(Object.assign(new Error('x'), { status: 500 }));
    const user = renderJobs();
    await screen.findByText('Showing 20 jobs');
    await user.click(screen.getByRole('button', { name: 'Load more' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not load more');
    expect(screen.getByText('Job 20')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load more' })).toBeEnabled();
  });

  it('shows the empty state with a working Clear filters button', async () => {
    vi.mocked(listJobs).mockResolvedValueOnce([]).mockResolvedValue(makeJobs(2));
    const user = renderJobs('/jobs?source=lever');
    expect(await screen.findByText('No jobs match these filters.')).toBeInTheDocument();

    const buttons = screen.getAllByRole('button', { name: 'Clear filters' });
    await user.click(buttons[buttons.length - 1]);
    expect(await screen.findByText('Showing 2 jobs')).toBeInTheDocument();
    expect(lastCall()).toMatchObject({ source: '' });
  });

  it('shows an error with retry and logs only the status', async () => {
    vi.mocked(listJobs)
      .mockRejectedValueOnce(Object.assign(new Error('secret detail'), { status: 500 }))
      .mockResolvedValueOnce(makeJobs(1));
    const user = renderJobs();
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load jobs');
    expect(console.error).toHaveBeenCalledWith('Loading jobs failed', { status: 500 });

    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Job 1')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clears the token and redirects to /login on 401', async () => {
    vi.mocked(listJobs).mockRejectedValue(Object.assign(new Error('x'), { status: 401 }));
    renderJobs();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(getToken()).toBeNull();
  });

  it('ignores a stale response after the filters change', async () => {
    const slow = deferred();
    vi.mocked(listJobs)
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce([{ ...makeJobs(1)[0], title: 'Fresh result' }]);
    const user = renderJobs();

    await user.selectOptions(screen.getByLabelText('Source'), 'lever');
    expect(await screen.findByText('Fresh result')).toBeInTheDocument();

    await act(async () => {
      slow.resolve([{ ...makeJobs(1)[0], title: 'Stale result' }]);
    });
    expect(screen.queryByText('Stale result')).not.toBeInTheDocument();
    expect(screen.getByText('Fresh result')).toBeInTheDocument();
  });

  describe('category and place filters', () => {
    it('shows facet options with counts and disables state and city without a country', async () => {
      renderJobs();
      await screen.findByText('Job 1');
      expect(
        await screen.findByRole('option', { name: 'Software Engineering (120)' }),
      ).toBeInTheDocument();
      expect(screen.getByLabelText('State')).toBeDisabled();
      expect(screen.getByLabelText('City')).toBeDisabled();
      expect(getJobFacets).toHaveBeenCalledWith({ scope: 'scraped', country: '', state: '' });
    });

    it('syncs the filters with the URL and resets the cascade', async () => {
      const user = renderJobs();
      await screen.findByRole('option', { name: 'Design (4)' });

      await user.selectOptions(screen.getByLabelText('Category'), 'Design');
      await user.selectOptions(screen.getByLabelText('Country'), 'United States');
      await waitFor(() => expect(screen.getByLabelText('State')).toBeEnabled());
      await user.selectOptions(screen.getByLabelText('State'), 'CA');
      await user.selectOptions(screen.getByLabelText('City'), 'San Francisco');

      await waitFor(() =>
        expect(lastCall()).toMatchObject({
          category: 'Design',
          country: 'United States',
          state: 'CA',
          city: 'San Francisco',
          offset: 0,
        }),
      );
      expect(urlParams()).toEqual({
        category: 'Design',
        country: 'United States',
        state: 'CA',
        city: 'San Francisco',
      });

      await user.selectOptions(screen.getByLabelText('State'), '');
      expect(urlParams()).toEqual({
        category: 'Design',
        country: 'United States',
      });

      await user.selectOptions(screen.getByLabelText('State'), 'CA');
      await user.selectOptions(screen.getByLabelText('Country'), 'Canada');
      expect(urlParams()).toEqual({ category: 'Design', country: 'Canada' });
      expect(getJobFacets).toHaveBeenLastCalledWith({
        scope: 'scraped',
        country: 'Canada',
        state: '',
      });
    });

    it('reads the place filters from the URL', async () => {
      renderJobs('/jobs?category=Design&country=Canada&state=ON&city=Toronto&scope=pasted');
      await screen.findByText('Job 1');
      expect(lastCall()).toMatchObject({
        category: 'Design',
        country: 'Canada',
        state: 'ON',
        city: 'Toronto',
        scope: 'pasted',
      });
      expect(screen.getByLabelText('Country')).toHaveValue('Canada');
      expect(screen.getByLabelText('State')).toBeEnabled();
      expect(getJobFacets).toHaveBeenCalledWith({
        scope: 'pasted',
        country: 'Canada',
        state: 'ON',
      });
    });

    it('Clear filters removes the place filters from the URL', async () => {
      const user = renderJobs('/jobs?category=Design&country=Canada');
      await screen.findByText('Job 1');
      await user.click(screen.getByRole('button', { name: 'Clear filters' }));
      await waitFor(() => expect(lastCall()).toMatchObject({ category: '', country: '' }));
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    });

    it('keeps the list working when facets fail to load', async () => {
      vi.mocked(getJobFacets).mockRejectedValue(Object.assign(new Error('x'), { status: 500 }));
      renderJobs();
      expect(await screen.findByText('Job 1')).toBeInTheDocument();
      expect(await screen.findByText(/Filter options could not be loaded/)).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });

  it('ignores a stale error after the filters change', async () => {
    const slow = deferred();
    vi.mocked(listJobs).mockReturnValueOnce(slow.promise).mockResolvedValueOnce(makeJobs(1));
    const user = renderJobs();
    await user.selectOptions(screen.getByLabelText('Remote type'), 'remote');
    await screen.findByText('Job 1');

    await act(async () => {
      slow.reject(Object.assign(new Error('x'), { status: 401 }));
    });
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
    expect(getToken()).toBe('jwt-token');
  });
});
