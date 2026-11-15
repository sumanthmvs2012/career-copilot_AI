import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import Button from '../components/Button';
import JobDetails from '../components/JobDetails';
import JobFilters from '../components/JobFilters';
import Select from '../components/Select';
import StatusBadge from '../components/StatusBadge';
import {
  APPLICATION_STATUSES,
  isMockApplications,
  listApplications,
  updateApplication,
} from '../services/applicationsService';
import { listJobs } from '../services/jobService';
import { listResumes } from '../services/resumeService';
import { getProfile } from '../services/profileService';
import { EMPTY_PLACE_FILTERS } from '../utils/jobFilters';
import { clearToken } from '../utils/authToken';
import { formatRelativeTime } from '../utils/relativeTime';

const STATUS_OPTIONS = APPLICATION_STATUSES.map((value) => ({
  value,
  label: value.charAt(0).toUpperCase() + value.slice(1),
}));

const APPLICATION_COLUMNS = ['Job', 'Status', 'Applied', 'Email sent', 'When'];

function ProfileCard({ state, profile, onRetry }) {
  if (state === 'loading') {
    return <p role="status">Loading your profile…</p>;
  }
  if (state === 'error') {
    return (
      <>
        <Alert variant="error">We could not load your profile. Please try again.</Alert>
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      </>
    );
  }
  if (state === 'empty') {
    return (
      <>
        <p>You have not set up your profile yet.</p>
        <Link to="/profile">Create your profile</Link>
      </>
    );
  }
  return (
    <>
      <Avatar name={profile.fullName} />
      <h3>{profile.fullName}</h3>
      <dl className="detail-list">
        <dt>Headline</dt>
        <dd>{profile.headline || 'Not set'}</dd>
        <dt>Location</dt>
        <dd>{profile.location || 'Not set'}</dd>
        <dt>Remote</dt>
        <dd>{profile.remoteOk ? 'Open to remote work' : 'Not specified'}</dd>
      </dl>
      <Link to="/profile">Edit profile</Link>
    </>
  );
}

function ResumeCard({ state, resume, count, onRetry }) {
  if (state === 'loading') {
    return <p role="status">Loading your resume…</p>;
  }
  if (state === 'error') {
    return (
      <>
        <Alert variant="error">We could not load your resume. Please try again.</Alert>
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      </>
    );
  }
  if (state === 'empty') {
    return (
      <>
        <p>You have not uploaded a resume yet.</p>
        <Link to="/resume">Upload your resume</Link>
      </>
    );
  }
  return (
    <>
      <dl className="detail-list">
        <dt>File</dt>
        <dd>{resume.fileName}</dd>
        <dt>Uploaded</dt>
        <dd>{new Date(resume.createdAt).toLocaleDateString()}</dd>
        <dt>Resumes</dt>
        <dd>{count} of 2 resumes</dd>
      </dl>
      <Link to="/resume">Manage resumes</Link>
    </>
  );
}

function LatestJobsSection({ state, jobs, filters, onFiltersChange, onRetry, onTracked }) {
  const filtered = Object.values(filters).some(Boolean);
  return (
    <section className="card" aria-labelledby="latest-jobs-heading">
      <h2 id="latest-jobs-heading">Latest jobs</h2>
      <JobFilters scope="scraped" compact value={filters} onChange={onFiltersChange} />
      {state === 'loading' && <p role="status">Loading latest jobs…</p>}
      {state === 'error' && (
        <>
          <Alert variant="error">We could not load the latest jobs. Please try again.</Alert>
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        </>
      )}
      {state === 'empty' && filtered && (
        <>
          <p>No jobs match these filters.</p>
          <Button variant="secondary" onClick={() => onFiltersChange({ ...EMPTY_PLACE_FILTERS })}>
            Clear filters
          </Button>
        </>
      )}
      {state === 'empty' && !filtered && (
        <p>No jobs collected yet. New postings appear here after the hourly scan.</p>
      )}
      {state === 'success' && (
        <ul className="job-list job-list--latest">
          {jobs.map((job) => (
            <li key={job.id}>
              <JobDetails job={job} onTracked={onTracked} />
            </li>
          ))}
        </ul>
      )}
      <div className="card__links">
        <Link to="/jobs">Browse all jobs</Link>
        <Link to="/match">Match a job</Link>
      </div>
    </section>
  );
}

function ApplicationRow({ app, onUnauthorized }) {
  const [row, setRow] = useState(app);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Keep the row in step when the list is reloaded.
  useEffect(() => {
    setRow(app);
  }, [app]);

  async function changeStatus(event) {
    const status = event.target.value;
    setSaving(true);
    setError('');
    try {
      setRow(await updateApplication(app.id, { status }));
    } catch (err) {
      if (err?.status === 401) return onUnauthorized();
      // Status only: never log tokens or text.
      console.error('Updating application failed', { status: err?.status ?? 'unknown' });
      setError('We could not save the status. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <tr>
      <td>
        <span className="app-job__title">{row.jobTitle}</span>
        {row.company && <span className="app-job__company">{row.company}</span>}
      </td>
      <td>
        <StatusBadge status={row.status} />
        <Select
          label={`Change status for ${row.jobTitle}`}
          options={STATUS_OPTIONS}
          value={row.status}
          disabled={saving}
          onChange={changeStatus}
        />
        {saving && <span role="status" className="muted">Saving�</span>}
        {error && <Alert variant="error">{error}</Alert>}
      </td>
      <td>{row.appliedAt ? new Date(row.appliedAt).toLocaleDateString() : ''}</td>
      <td>
        <span className={`email-flag ${row.emailSent ? 'email-flag--yes' : ''}`}>
          {row.emailSent ? 'Yes' : 'No'}
        </span>
      </td>
      <td title={row.emailSentAt ? new Date(row.emailSentAt).toLocaleString() : undefined}>
        {formatRelativeTime(row.emailSentAt)}
      </td>
    </tr>
  );
}

function ApplicationsSection({ state, applications, onRetry, onUnauthorized }) {
  return (
    <section className="card" aria-labelledby="applications-heading">
      <div className="card__head">
        <h2 id="applications-heading">Applications</h2>
        {isMockApplications() && (
          <span className="demo-badge" title="Sample data for previewing the layout">
            Demo data
          </span>
        )}
      </div>
      {state === 'loading' && <p role="status">Loading applications…</p>}
      {state === 'error' && (
        <>
          <Alert variant="error">We could not load your applications. Please try again.</Alert>
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        </>
      )}
      {(state === 'empty' || state === 'success') && (
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                {APPLICATION_COLUMNS.map((name) => (
                  <th key={name} scope="col">
                    {name}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {applications.map((app) => (
                <ApplicationRow key={app.id} app={app} onUnauthorized={onUnauthorized} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {state === 'empty' && <p className="muted">No applications yet</p>}
    </section>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const [profileState, setProfileState] = useState('loading');
  const [profile, setProfile] = useState(null);
  const [resumeState, setResumeState] = useState('loading');
  const [resume, setResume] = useState(null);
  const [resumeCount, setResumeCount] = useState(0);
  const [latestState, setLatestState] = useState('loading');
  const [latestJobs, setLatestJobs] = useState([]);
  const [latestFilters, setLatestFilters] = useState(EMPTY_PLACE_FILTERS);
  // Only the newest Latest jobs request may update the card.
  const latestRequestId = useRef(0);
  const [appsState, setAppsState] = useState('loading');
  const [applications, setApplications] = useState([]);

  const goToLogin = useCallback(() => {
    clearToken();
    navigate('/login', { replace: true });
  }, [navigate]);

  const loadProfile = useCallback(async () => {
    setProfileState('loading');
    try {
      setProfile(await getProfile());
      setProfileState('success');
    } catch (error) {
      if (error?.status === 401) return goToLogin();
      if (error?.status === 404) return setProfileState('empty');
      // Status only: never log the profile or token.
      console.error('Loading profile failed', { status: error?.status ?? 'unknown' });
      setProfileState('error');
    }
  }, [goToLogin]);

  const loadResume = useCallback(async () => {
    setResumeState('loading');
    try {
      const rows = await listResumes();
      const base = rows.find((r) => r.isBase) ?? rows[0] ?? null;
      setResume(base);
      setResumeCount(rows.length);
      setResumeState(base ? 'success' : 'empty');
    } catch (error) {
      if (error?.status === 401) return goToLogin();
      console.error('Loading resume failed', { status: error?.status ?? 'unknown' });
      setResumeState('error');
    }
  }, [goToLogin]);

  const loadApplications = useCallback(async () => {
    setAppsState('loading');
    try {
      const rows = await listApplications();
      setApplications(rows);
      setAppsState(rows.length === 0 ? 'empty' : 'success');
    } catch (error) {
      if (error?.status === 401) return goToLogin();
      console.error('Loading applications failed', { status: error?.status ?? 'unknown' });
      setAppsState('error');
    }
  }, [goToLogin]);

  const loadLatestJobs = useCallback(async () => {
    const id = ++latestRequestId.current;
    setLatestState('loading');
    const active = Object.fromEntries(Object.entries(latestFilters).filter(([, v]) => v));
    try {
      const rows = await listJobs({ scope: 'scraped', limit: 10, ...active });
      if (id !== latestRequestId.current) return;
      setLatestJobs(rows);
      setLatestState(rows.length === 0 ? 'empty' : 'success');
    } catch (error) {
      if (id !== latestRequestId.current) return;
      if (error?.status === 401) return goToLogin();
      console.error('Loading latest jobs failed', { status: error?.status ?? 'unknown' });
      setLatestState('error');
    }
  }, [goToLogin, latestFilters]);

  // Latest jobs reload on every filter change without refetching the other cards.
  useEffect(() => {
    loadLatestJobs();
  }, [loadLatestJobs]);

  useEffect(() => {
    loadProfile();
    loadResume();
    loadApplications();
  }, [loadProfile, loadResume, loadApplications]);

  return (
    <main className="page">
      <header className="page__header">
        <h1>Dashboard</h1>
        <Button variant="secondary" onClick={goToLogin}>
          Log out
        </Button>
      </header>

      <div className="page__grid">
        <section className="card" aria-labelledby="profile-heading">
          <h2 id="profile-heading">Your profile</h2>
          <ProfileCard state={profileState} profile={profile} onRetry={loadProfile} />
        </section>
        <section className="card" aria-labelledby="resume-heading">
          <h2 id="resume-heading">Your resume</h2>
          <ResumeCard
            state={resumeState}
            resume={resume}
            count={resumeCount}
            onRetry={loadResume}
          />
        </section>
        <LatestJobsSection
          state={latestState}
          jobs={latestJobs}
          filters={latestFilters}
          onFiltersChange={setLatestFilters}
          onRetry={loadLatestJobs}
          onTracked={loadApplications}
        />
        <ApplicationsSection
          state={appsState}
          applications={applications}
          onRetry={loadApplications}
          onUnauthorized={goToLogin}
        />
      </div>
    </main>
  );
}
