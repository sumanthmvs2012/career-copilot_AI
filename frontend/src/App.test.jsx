import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { clearToken, setToken } from './utils/authToken';

vi.mock('./services/jobService', () => ({
  listJobs: vi.fn(() => new Promise(() => {})),
  getJobFacets: vi.fn(() => new Promise(() => {})),
  matchPastedJob: vi.fn(),
}));
vi.mock('./services/profileService', () => ({
  getProfile: vi.fn(() => new Promise(() => {})),
  updateProfile: vi.fn(),
}));

function renderAt(path) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('route protection', () => {
  afterEach(() => clearToken());

  it.each(['/dashboard', '/profile', '/match', '/jobs'])(
    'redirects %s to /login without a token',
    (path) => {
      clearToken();
      renderAt(path);
      expect(screen.getByRole('heading', { name: /log in|welcome/i })).toBeInTheDocument();
    },
  );

  it('shows the dashboard when a token exists', () => {
    setToken('jwt-token');
    renderAt('/dashboard');
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('shows the profile page when a token exists', () => {
    setToken('jwt-token');
    renderAt('/profile');
    expect(screen.getByRole('heading', { name: 'Your profile' })).toBeInTheDocument();
  });

  it('shows the jobs page when a token exists', () => {
    setToken('jwt-token');
    renderAt('/jobs');
    expect(screen.getByRole('heading', { name: 'Jobs' })).toBeInTheDocument();
  });
});
