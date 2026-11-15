import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import TextInput from '../components/TextInput';
import { getProfile, updateProfile } from '../services/profileService';
import { validateProfile } from '../utils/validation';

const EMPTY_FORM = {
  fullName: '',
  headline: '',
  location: '',
  remoteOk: false,
  autoSubmitEnabled: false,
};

export default function ProfilePage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [loadState, setLoadState] = useState('loading');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const profile = await getProfile();
        if (cancelled) return;
        setForm({
          fullName: profile.fullName ?? '',
          headline: profile.headline ?? '',
          location: profile.location ?? '',
          remoteOk: Boolean(profile.remoteOk),
          autoSubmitEnabled: Boolean(profile.autoSubmitEnabled),
        });
        setLoadState('ready');
      } catch (error) {
        if (cancelled) return;
        if (error?.status === 401) return navigate('/login', { replace: true });
        // No profile yet is normal for a new user: show an empty form.
        if (error?.status === 404) return setLoadState('ready');
        console.error('Loading profile failed', { status: error?.status ?? 'unknown' });
        setLoadState('error');
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  function setField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
    setSaved(false);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (savingRef.current) return;

    setFormError('');
    setSaved(false);
    const errors = validateProfile(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    savingRef.current = true;
    setSaving(true);
    try {
      await updateProfile(form);
      setSaved(true);
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      if (error?.status === 400 && error.data?.fieldErrors) {
        setFieldErrors(error.data.fieldErrors);
        setFormError('Please fix the highlighted fields.');
      } else {
        console.error('Saving profile failed', { status: error?.status ?? 'unknown' });
        setFormError('We could not save your profile. Please try again.');
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <main className="page page--narrow">
      <header className="page__header">
        <h1>Your profile</h1>
        <Link to="/dashboard">Back to dashboard</Link>
      </header>

      <section className="card">
        {loadState === 'loading' && <p role="status">Loading your profile…</p>}
        {loadState === 'error' && (
          <>
            <Alert variant="error">We could not load your profile. Please try again.</Alert>
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Reload
            </Button>
          </>
        )}
        {loadState === 'ready' && (
          <form onSubmit={handleSubmit} noValidate>
            {saved && (
              <Alert variant="success">
                Profile saved. <Link to="/dashboard">Go to dashboard</Link>
              </Alert>
            )}
            {formError && <Alert variant="error">{formError}</Alert>}

            <TextInput
              label="Full name"
              name="fullName"
              autoComplete="name"
              value={form.fullName}
              onChange={(e) => setField('fullName', e.target.value)}
              error={fieldErrors.fullName}
              disabled={saving}
            />
            <TextInput
              label="Headline"
              name="headline"
              value={form.headline}
              onChange={(e) => setField('headline', e.target.value)}
              error={fieldErrors.headline}
              disabled={saving}
            />
            <TextInput
              label="Location"
              name="location"
              autoComplete="address-level2"
              value={form.location}
              onChange={(e) => setField('location', e.target.value)}
              error={fieldErrors.location}
              disabled={saving}
            />

            <div className="check">
              <input
                id="remoteOk"
                type="checkbox"
                checked={form.remoteOk}
                onChange={(e) => setField('remoteOk', e.target.checked)}
                disabled={saving}
              />
              <label htmlFor="remoteOk">Open to remote work</label>
            </div>
            <div className="check">
              <input
                id="autoSubmitEnabled"
                type="checkbox"
                aria-describedby="autoSubmitNote"
                checked={form.autoSubmitEnabled}
                onChange={(e) => setField('autoSubmitEnabled', e.target.checked)}
                disabled={saving}
              />
              <label htmlFor="autoSubmitEnabled">Auto-submit applications</label>
            </div>
            <p id="autoSubmitNote" className="muted">
              Off by default; only applies where a job board allows API applications.
            </p>

            <Button type="submit" loading={saving} loadingText="Saving…">
              Save profile
            </Button>
          </form>
        )}
      </section>
    </main>
  );
}
