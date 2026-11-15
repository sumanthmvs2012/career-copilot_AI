import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import TextInput from '../components/TextInput';
import { login } from '../services/authService';
import { setToken } from '../utils/authToken';
import { validateLogin } from '../utils/validation';

const MESSAGES = {
  // Generic on purpose: never reveal whether the email or the password was wrong.
  invalidCredentials: 'Email or password is incorrect.',
  rateLimited: 'Too many attempts. Please try again shortly.',
  generic: 'Something went wrong. Please try again.',
};

function messageForError(error) {
  if (error?.status === 401) return MESSAGES.invalidCredentials;
  if (error?.status === 429) return MESSAGES.rateLimited;
  return MESSAGES.generic;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  // A ref guards against a second submit landing before React re-renders the disabled button.
  const submittingRef = useRef(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    setFormError('');
    const errors = validateLogin({ email, password });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    submittingRef.current = true;
    setLoading(true);
    try {
      const token = await login(email.trim(), password);
      setToken(token);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      // Status only: the error never carries the password or token, and neither is logged.
      if (error?.status !== 401 && error?.status !== 429) {
        console.error('Login failed', { status: error?.status ?? 'unknown' });
      }
      setFormError(messageForError(error));
      setLoading(false);
      submittingRef.current = false;
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <h1>Log in</h1>
        <p className="auth-card__subtitle">Welcome back to Career Copilot.</p>

        {formError && <Alert variant="error">{formError}</Alert>}

        <form onSubmit={handleSubmit} noValidate>
          <TextInput
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            disabled={loading}
          />
          <TextInput
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            disabled={loading}
          />
          <Button
            type="submit"
            variant="primary"
            fullWidth
            loading={loading}
            loadingText="Logging in…"
          >
            Log in
          </Button>
        </form>

        <p className="auth-card__footer">
          Don&apos;t have an account? <Link to="/signup">Sign up</Link>
        </p>
      </div>
    </main>
  );
}
