import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SignupPage from './SignupPage';
import { signup } from '../services/authService';
import { clearToken, getToken } from '../utils/authToken';

vi.mock('../services/authService', () => ({
  signup: vi.fn(),
}));

function renderSignup() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/signup']}>
      <Routes>
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

async function fillAndSubmit(
  user,
  { email = 'jane@example.com', password = 'secret-pass', confirm = password } = {},
) {
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  if (confirm) await user.type(screen.getByLabelText('Confirm password'), confirm);
  await user.click(screen.getByRole('button', { name: 'Create account' }));
}

function apiError(status) {
  return Object.assign(new Error(`status ${status}`), { status });
}

describe('SignupPage', () => {
  beforeEach(() => {
    vi.mocked(signup).mockReset();
    clearToken();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the sign-up form with a log-in link', () => {
    renderSignup();
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('Confirm password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
  });

  it('shows field errors on empty submit and does not call the service', async () => {
    const user = renderSignup();
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    expect(screen.getByText('Please confirm your password.')).toBeInTheDocument();
    expect(signup).not.toHaveBeenCalled();
  });

  it('rejects a short password', async () => {
    const user = renderSignup();
    await fillAndSubmit(user, { password: 'short' });

    expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument();
    expect(signup).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords', async () => {
    const user = renderSignup();
    await fillAndSubmit(user, { password: 'secret-pass', confirm: 'secret-pasz' });

    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
    expect(signup).not.toHaveBeenCalled();
  });

  it('shows a loading state and blocks double submit while pending', async () => {
    let resolveSignup;
    vi.mocked(signup).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSignup = resolve;
        }),
    );
    const user = renderSignup();
    await fillAndSubmit(user);

    const button = screen.getByRole('button', { name: /creating account/i });
    expect(button).toBeDisabled();
    await user.click(button);
    expect(signup).toHaveBeenCalledTimes(1);

    resolveSignup('jwt-token');
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
  });

  it('stores the token and navigates to /dashboard on success', async () => {
    vi.mocked(signup).mockResolvedValue('jwt-token');
    const user = renderSignup();
    await fillAndSubmit(user, { email: '  jane@example.com ' });

    expect(signup).toHaveBeenCalledWith('jane@example.com', 'secret-pass');
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
    expect(getToken()).toBe('jwt-token');
  });

  it.each([
    [409, 'An account with this email already exists. Try logging in instead.'],
    [400, 'Please check your details and try again.'],
    [429, 'Too many attempts. Please try again shortly.'],
    [500, 'Something went wrong. Please try again.'],
    [0, 'Something went wrong. Please try again.'],
  ])('shows the right message for status %i', async (status, message) => {
    vi.mocked(signup).mockRejectedValue(apiError(status));
    const user = renderSignup();
    await fillAndSubmit(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled();
    expect(getToken()).toBeNull();
  });

  it('never logs the password on an unexpected error', async () => {
    vi.mocked(signup).mockRejectedValue(apiError(500));
    const user = renderSignup();
    await fillAndSubmit(user);

    await screen.findByRole('alert');
    const logged = JSON.stringify(vi.mocked(console.error).mock.calls);
    expect(logged).not.toContain('secret-pass');
    expect(logged).not.toContain('jane@example.com');
  });
});
