import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import JobDetails from '../components/JobDetails';
import TextArea from '../components/TextArea';
import TextInput from '../components/TextInput';
import { listJobs, matchJob } from '../services/jobService';
import { MAX_JOB_DESCRIPTION_LENGTH, validateJobMatch } from '../utils/validation';

const EMPTY_FORM = {
  title: '',
  companyName: '',
  location: '',
  remoteType: '',
  sourceUrl: '',
  description: '',
};

// match is null today; when the AI module ships it becomes
// {score, missingSkills, nearMatchedSkills, missingKeywords, suggestedHeadline, locationFit}
// and is rendered here in place of the "not available" note.
function ResultCard({ result }) {
  const { job, match, message } = result;
  return (
    <section className="card" aria-labelledby="result-heading">
      <h2 id="result-heading">Job saved</h2>
      <JobDetails job={{ ...job, match }} />
      {!match && (
        <Alert variant="warning">
          {message || 'Matching is not available yet; your job is saved.'}
        </Alert>
      )}
    </section>
  );
}

function PastedJobs({ state, jobs, onRetry }) {
  return (
    <section className="card" aria-labelledby="jobs-heading">
      <h2 id="jobs-heading">Your pasted jobs</h2>
      {state === 'loading' && <p role="status">Loading your jobs…</p>}
      {state === 'error' && (
        <>
          <Alert variant="error">We could not load your jobs. Please try again.</Alert>
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        </>
      )}
      {state === 'empty' && <p className="muted">You have not pasted any jobs yet.</p>}
      {state === 'success' && (
        <ul className="job-list">
          {jobs.map((job) => (
            <li key={job.id}>
              <JobDetails job={job} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function MatchJobPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [noProfile, setNoProfile] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [listState, setListState] = useState('loading');
  const [jobs, setJobs] = useState([]);

  const loadJobs = useCallback(async () => {
    setListState('loading');
    try {
      const rows = await listJobs({ scope: 'pasted' });
      setJobs(rows);
      setListState(rows.length === 0 ? 'empty' : 'success');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      // Status only: never log job text.
      console.error('Loading jobs failed', { status: error?.status ?? 'unknown' });
      setListState('error');
    }
  }, [navigate]);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  function setField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (savingRef.current) return;

    setFormError('');
    setNoProfile(false);
    setResult(null);
    const errors = validateJobMatch(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    savingRef.current = true;
    setSaving(true);
    try {
      const response = await matchJob(form);
      setResult(response);
      setForm(EMPTY_FORM);
      setJobs((current) => [response.job, ...current]);
      setListState('success');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      if (error?.status === 404) {
        setNoProfile(true);
      } else if (error?.status === 400 && error.data?.fieldErrors) {
        setFieldErrors(error.data.fieldErrors);
        setFormError('Please fix the highlighted fields.');
      } else {
        console.error('Matching job failed', { status: error?.status ?? 'unknown' });
        setFormError('We could not save this job. Please try again.');
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <main className="page page--narrow">
      <header className="page__header">
        <h1>Match a job</h1>
        <Link to="/dashboard">Back to dashboard</Link>
      </header>

      <section className="card">
        <form onSubmit={handleSubmit} noValidate>
          {noProfile && (
            <Alert variant="error">
              Create your profile first. <Link to="/profile">Go to your profile</Link>
            </Alert>
          )}
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextInput
            label="Job title (optional)"
            name="title"
            value={form.title}
            onChange={(e) => setField('title', e.target.value)}
            error={fieldErrors.title}
            disabled={saving}
          />
          <TextInput
            label="Company (optional)"
            name="companyName"
            value={form.companyName}
            onChange={(e) => setField('companyName', e.target.value)}
            error={fieldErrors.companyName}
            disabled={saving}
          />
          <TextInput
            label="Location (optional)"
            name="location"
            value={form.location}
            onChange={(e) => setField('location', e.target.value)}
            error={fieldErrors.location}
            disabled={saving}
          />
          <div className="field">
            <label className="field__label" htmlFor="remoteType">
              Remote type
            </label>
            <select
              id="remoteType"
              name="remoteType"
              className="field__input"
              value={form.remoteType}
              onChange={(e) => setField('remoteType', e.target.value)}
              aria-invalid={fieldErrors.remoteType ? 'true' : 'false'}
              disabled={saving}
            >
              <option value="">Not specified</option>
              <option value="remote">Remote</option>
              <option value="hybrid">Hybrid</option>
              <option value="onsite">On-site</option>
            </select>
            {fieldErrors.remoteType && <p className="field__error">{fieldErrors.remoteType}</p>}
          </div>
          <TextInput
            label="Job URL (optional)"
            name="sourceUrl"
            type="url"
            value={form.sourceUrl}
            onChange={(e) => setField('sourceUrl', e.target.value)}
            error={fieldErrors.sourceUrl}
            disabled={saving}
          />
          <TextArea
            label="Job description"
            name="description"
            rows={10}
            value={form.description}
            onChange={(e) => setField('description', e.target.value)}
            error={fieldErrors.description}
            hint={`${form.description.length} / ${MAX_JOB_DESCRIPTION_LENGTH} characters (at least 20)`}
            disabled={saving}
          />

          <Button type="submit" loading={saving} loadingText="Saving…">
            Match this job
          </Button>
        </form>
      </section>

      {result && <ResultCard result={result} />}

      <PastedJobs state={listState} jobs={jobs} onRetry={loadJobs} />
    </main>
  );
}
