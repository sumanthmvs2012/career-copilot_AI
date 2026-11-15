import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ProfilePage from './ProfilePage';
import { getProfile, updateProfile } from '../services/profileService';

vi.mock('../services/profileService', () => ({ getProfile: vi.fn(), updateProfile: vi.fn() }));

function renderProfile() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/profile']}>
      <Routes>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function apiError(status, data = null) {
  return Object.assign(new Error(`status ${status}`), { status, data });
}

const PROFILE = {
  id: 1,
  fullName: 'Jane Doe',
  headline: 'Engineer',
  location: 'Berlin',
  remoteOk: true,
  autoSubmitEnabled: false,
};

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(getProfile).mockReset();
    vi.mocked(updateProfile).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state, then the existing profile', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    renderProfile();

    expect(screen.getByText('Loading your profile…')).toBeInTheDocument();
    expect(await screen.findByLabelText('Full name')).toHaveValue('Jane Doe');
    expect(screen.getByLabelText('Headline')).toHaveValue('Engineer');
    expect(screen.getByLabelText('Location')).toHaveValue('Berlin');
    expect(screen.getByLabelText('Open to remote work')).toBeChecked();
    expect(screen.getByLabelText('Auto-submit applications')).not.toBeChecked();
    expect(screen.getByText(/off by default/i)).toBeInTheDocument();
  });

  it('treats 404 as an empty form', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(404));
    renderProfile();

    expect(await screen.findByLabelText('Full name')).toHaveValue('');
    expect(screen.getByLabelText('Open to remote work')).not.toBeChecked();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an error when loading fails', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(500));
    renderProfile();

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your profile');
    expect(screen.queryByLabelText('Full name')).not.toBeInTheDocument();
  });

  it('redirects to /login on 401 while loading', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(401));
    renderProfile();

    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('validates full name client-side and does not call the service', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(404));
    const user = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save profile' }));

    expect(screen.getByText('Full name is required.')).toBeInTheDocument();
    expect(updateProfile).not.toHaveBeenCalled();
  });

  it('saves, shows a success alert and links back to the dashboard', async () => {
    vi.mocked(getProfile).mockRejectedValue(apiError(404));
    vi.mocked(updateProfile).mockResolvedValue(PROFILE);
    const user = renderProfile();

    await user.type(await screen.findByLabelText('Full name'), 'Jane Doe');
    await user.click(screen.getByLabelText('Open to remote work'));
    await user.click(screen.getByLabelText('Auto-submit applications'));
    await user.click(screen.getByRole('button', { name: 'Save profile' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Profile saved.');
    expect(updateProfile).toHaveBeenCalledWith({
      fullName: 'Jane Doe',
      headline: '',
      location: '',
      remoteOk: true,
      autoSubmitEnabled: true,
    });
    const links = screen.getAllByRole('link', { name: /dashboard/i });
    expect(links.every((l) => l.getAttribute('href') === '/dashboard')).toBe(true);
  });

  it('shows a saving state and blocks double submit', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    let resolveSave;
    vi.mocked(updateProfile).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSave = resolve;
        }),
    );
    const user = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save profile' }));
    const button = screen.getByRole('button', { name: /saving/i });
    expect(button).toBeDisabled();
    await user.click(button);
    expect(updateProfile).toHaveBeenCalledTimes(1);

    resolveSave(PROFILE);
    expect(await screen.findByRole('status')).toHaveTextContent('Profile saved.');
  });

  it('shows field errors from a 400 response', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(updateProfile).mockRejectedValue(
      apiError(400, {
        status: 400,
        message: 'Validation failed',
        fieldErrors: { headline: 'Headline is too long.' },
      }),
    );
    const user = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save profile' }));

    expect(await screen.findByText('Headline is too long.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Please fix the highlighted fields.');
  });

  it('shows a generic message when saving fails', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(updateProfile).mockRejectedValue(apiError(500));
    const user = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save profile' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not save your profile');
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeEnabled();
  });

  it('redirects to /login on 401 while saving', async () => {
    vi.mocked(getProfile).mockResolvedValue(PROFILE);
    vi.mocked(updateProfile).mockRejectedValue(apiError(401));
    const user = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save profile' }));

    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });
});
