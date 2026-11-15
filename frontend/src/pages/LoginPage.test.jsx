import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';
import { login } from '../services/authService';
import { clearToken, getToken } from '../utils/authToken';

vi.mock('../services/authService', () => ({
  login: vi.fn(),
}));

function renderLogin() {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

async function fillAndSubmit(user, email = 'jane@example.com', password = 'secret-pass') {
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

function apiError(status) {
  return Object.assign(new Error(`status ${status}`), { status });
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(login).mockReset();
    clearToken();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the login form with a sign-up link', () => {
    renderLogin();
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled();
    expect(screen.getByRole('link', { name: 'Sign up' })).toHaveAttribute('href', '/signup');
  });

  it('shows field errors on empty submit and does not call the service', async () => {
    const user = renderLogin();
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    const emailInput = screen.getByLabelText('Email');
    expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    expect(emailInput).toHaveAccessibleDescription('Email is required.');
    expect(login).not.toHaveBeenCalled();
  });

  it('shows an error for an invalid email format', async () => {
    const user = renderLogin();
    await fillAndSubmit(user, 'not-an-email', 'secret-pass');

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('calls authService.login with the entered values', async () => {
    vi.mocked(login).mockResolvedValue('jwt-token');
    const user = renderLogin();
    await fillAndSubmit(user, 'jane@example.com', 'secret-pass');

    expect(login).toHaveBeenCalledTimes(1);
    expect(login).toHaveBeenCalledWith('jane@example.com', 'secret-pass');
  });

  it('shows a loading state and blocks double submit while pending', async () => {
    let resolveLogin;
    vi.mocked(login).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve;
        }),
    );
    const user = renderLogin();
    await fillAndSubmit(user);

    const button = screen.getByRole('button', { name: /logging in/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await user.click(button);
    expect(login).toHaveBeenCalledTimes(1);

    resolveLogin('jwt-token');
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
  });

  it('stores the token and navigates to /dashboard on success', async () => {
    vi.mocked(login).mockResolvedValue('jwt-token');
    const user = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
    expect(getToken()).toBe('jwt-token');
    expect(window.sessionStorage.getItem('authToken')).toBe('jwt-token');
  });

  it('shows a generic message on 401 without saying which field was wrong', async () => {
    vi.mocked(login).mockRejectedValue(apiError(401));
    const user = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled();
    expect(getToken()).toBeNull();
  });

  it('shows a rate-limit message on 429', async () => {
    vi.mocked(login).mockRejectedValue(apiError(429));
    const user = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please try again shortly.',
    );
  });

  it('shows a generic message on a network error and never logs the password', async () => {
    vi.mocked(login).mockRejectedValue(apiError(0));
    const user = renderLogin();
    await fillAndSubmit(user, 'jane@example.com', 'secret-pass');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong. Please try again.',
    );
    const logged = JSON.stringify(vi.mocked(console.error).mock.calls);
    expect(logged).not.toContain('secret-pass');
  });

  it('shows a generic message on a server error', async () => {
    vi.mocked(login).mockRejectedValue(apiError(500));
    const user = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong. Please try again.',
    );
  });
});
