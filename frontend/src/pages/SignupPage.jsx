import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import TextInput from '../components/TextInput';
import { signup } from '../services/authService';
import { setToken } from '../utils/authToken';
import { MIN_PASSWORD_LENGTH, validateSignup } from '../utils/validation';

const MESSAGES = {
  emailTaken: 'An account with this email already exists. Try logging in instead.',
  invalid: 'Please check your details and try again.',
  rateLimited: 'Too many attempts. Please try again shortly.',
  generic: 'Something went wrong. Please try again.',
};

function messageForError(error) {
  if (error?.status === 409) return MESSAGES.emailTaken;
  if (error?.status === 400) return MESSAGES.invalid;
  if (error?.status === 429) return MESSAGES.rateLimited;
  return MESSAGES.generic;
}

const EXPECTED_STATUSES = [400, 409, 429];

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  // Same double-submit guard as LoginPage: the ref updates before React re-renders.
  const submittingRef = useRef(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    setFormError('');
    const errors = validateSignup({ email, password, confirmPassword });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    submittingRef.current = true;
    setLoading(true);
    try {
      const token = await signup(email.trim(), password);
      setToken(token);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      // Status only: never log the email, password or token.
      if (!EXPECTED_STATUSES.includes(error?.status)) {
        console.error('Signup failed', { status: error?.status ?? 'unknown' });
      }
      setFormError(messageForError(error));
      setLoading(false);
      submittingRef.current = false;
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <h1>Create your account</h1>
        <p className="auth-card__subtitle">Upload your resume once, then match it to every job.</p>

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
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
            disabled={loading}
          />
          <TextInput
            label="Confirm password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            error={fieldErrors.confirmPassword}
            disabled={loading}
          />
          <Button
            type="submit"
            variant="primary"
            fullWidth
            loading={loading}
            loadingText="Creating account…"
          >
            Create account
          </Button>
        </form>

        <p className="auth-card__footer">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </div>
    </main>
  );
}
