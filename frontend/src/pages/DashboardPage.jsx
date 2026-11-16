import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import Alert from '../components/Alert';
import Avatar from '../components/Avatar';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Icon from '../components/Icon';
import JobDetails from '../components/JobDetails';
import JobFilters from '../components/JobFilters';
import MailboxCard from '../components/MailboxCard';
import Select from '../components/Select';
import StatusBadge from '../components/StatusBadge';
import SubmitApplicationPanel from '../components/SubmitApplicationPanel';
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
import { isTailoredCopy } from '../utils/resumes';
import { clearToken } from '../utils/authToken';
import { formatRelativeTime } from '../utils/relativeTime';

const STATUS_OPTIONS = APPLICATION_STATUSES.map((value) => ({
  value,
  label: value.charAt(0).toUpperCase() + value.slice(1),
}));

const APPLICATION_COLUMNS = ['Job', 'Status', 'Applied', 'Email sent', 'When', 'Actions'];

const MAIL_NOTICES = {
  connected: { kind: 'success', text: 'Gmail is connected.' },
  error: { kind: 'error', text: 'We could not connect Gmail. Please try again.' },
};

// "Good morning, Jane": first name only, falling back to a plain welcome.
function greeting(fullName, now = new Date()) {
  const hour = now.getHours();
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const first = (fullName ?? '').trim().split(/\s+/)[0];
  return first ? `${part}, ${first}` : 'Welcome back';
}

// A number tile in the hero; value null means still loading or unavailable, shown as a dash.
function StatTile({ icon, label, value }) {
  return (
    <li className="stat-tile">
      <span className="stat-tile__icon">
        <Icon name={icon} size={18} />
      </span>
      <span className="stat-tile__value">{value ?? '–'}</span>
      <span className="stat-tile__label">{label}</span>
    </li>
  );
}

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
      <EmptyState icon="user" action={<Link to="/profile">Create your profile</Link>}>
        You have not set up your profile yet.
      </EmptyState>
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
      <EmptyState icon="file" action={<Link to="/resume">Upload your resume</Link>}>
        You have not uploaded a resume yet.
      </EmptyState>
    );
  }
  return (
    <>
      <dl className="detail-list">
        <dt>File</dt>
        <dd className="resume-file" title={resume.fileName}>
          {resume.fileName}
        </dd>
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
      <h2 id="latest-jobs-heading">
        <Icon name="briefcase" size={20} /> Latest jobs
      </h2>
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
          <EmptyState
            icon="briefcase"
            action={
              <Button
                variant="secondary"
                onClick={() => onFiltersChange({ ...EMPTY_PLACE_FILTERS })}
              >
                Clear filters
              </Button>
            }
          >
            No jobs match these filters.
          </EmptyState>
        </>
      )}
      {state === 'empty' && !filtered && (
        <EmptyState icon="briefcase">
          No jobs collected yet. New postings appear here after the hourly scan.
        </EmptyState>
      )}
      {state === 'success' && (
        <ul className="job-rows">
          {jobs.map((job) => (
            <li key={job.id}>
              <JobDetails job={job} variant="row" onTracked={onTracked} />
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
  const [editing, setEditing] = useState(false);
  const [submittedNote, setSubmittedNote] = useState(false);

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
    <>
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
          {saving && (
            <span role="status" className="muted">
              Saving…
            </span>
          )}
          {error && <Alert variant="error">{error}</Alert>}
        </td>
        <td>{row.appliedAt ? new Date(row.appliedAt).toLocaleDateString() : ''}</td>
        <td>
          <span className={`email-flag ${row.emailSent ? 'email-flag--yes' : ''}`}>
            {row.emailSent ? 'Yes' : 'No'}
          </span>
          {row.emailSent && row.emailClassification && (
            <StatusBadge status={row.emailClassification} />
          )}
        </td>
        <td title={row.emailSentAt ? new Date(row.emailSentAt).toLocaleString() : undefined}>
          {formatRelativeTime(row.emailSentAt)}
        </td>
        <td>
          {row.status === 'draft' && !editing && (
            <Button
              variant="secondary"
              onClick={() => {
                setSubmittedNote(false);
                setEditing(true);
              }}
              aria-label={`Edit and submit ${row.jobTitle}`}
            >
              Edit &amp; submit
            </Button>
          )}
          {submittedNote && (
            <span role="status" className="muted">
              Marked as applied.
            </span>
          )}
        </td>
      </tr>
      {editing && row.status === 'draft' && (
        <tr>
          <td colSpan={APPLICATION_COLUMNS.length}>
            <SubmitApplicationPanel
              application={row}
              onUpdated={setRow}
              onUnauthorized={onUnauthorized}
              onClose={({ submitted }) => {
                setEditing(false);
                setSubmittedNote(submitted);
              }}
            />
          </td>
        </tr>
      )}
    </>
  );
}

function ApplicationsSection({ state, applications, onRetry, onUnauthorized }) {
  return (
    <section className="card" aria-labelledby="applications-heading">
      <div className="card__head">
        <h2 id="applications-heading">
          <Icon name="bookmark" size={20} /> Applications
        </h2>
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
      {state === 'empty' && (
        <EmptyState icon="bookmark">
          <span className="muted">No applications yet</span>
        </EmptyState>
      )}
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [mailNotice, setMailNotice] = useState(
    () => MAIL_NOTICES[searchParams.get('mail')] ?? null,
  );

  // Read ?mail=connected|error once, then remove it so a reload does not repeat the message.
  useEffect(() => {
    if (!searchParams.has('mail')) return;
    setMailNotice(MAIL_NOTICES[searchParams.get('mail')] ?? null);
    const next = new URLSearchParams(searchParams);
    next.delete('mail');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

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
      // Tailored copies are not uploads: they are neither shown here nor counted.
      const uploads = rows.filter((r) => !isTailoredCopy(r));
      const base = uploads.find((r) => r.isBase) ?? uploads[0] ?? null;
      setResume(base);
      setResumeCount(uploads.length);
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
      <header className="hero">
        <div className="hero__top">
          <div>
            <h1 className="hero__eyebrow">Dashboard</h1>
            <p className="hero__greeting">{greeting(profile?.fullName)}</p>
            <p className="hero__sub">Here is where your job search stands today.</p>
          </div>
          <Button variant="secondary" className="hero__logout" onClick={goToLogin}>
            Log out
          </Button>
        </div>
        <ul className="hero__stats" aria-label="Quick stats">
          <StatTile
            icon="bookmark"
            label="Applications"
            value={appsState === 'loading' || appsState === 'error' ? null : applications.length}
          />
          <StatTile
            icon="mail"
            label="Replies"
            value={
              appsState === 'loading' || appsState === 'error'
                ? null
                : applications.filter((app) => app.emailSent).length
            }
          />
          <StatTile
            icon="briefcase"
            label="Latest jobs"
            value={latestState === 'success' ? latestJobs.length : null}
          />
          <StatTile
            icon="file"
            label="Resumes"
            value={resumeState === 'loading' || resumeState === 'error' ? null : resumeCount}
          />
        </ul>
      </header>

      <div className="page__grid">
        <section className="card" aria-labelledby="profile-heading">
          <h2 id="profile-heading">Your profile</h2>
          <ProfileCard state={profileState} profile={profile} onRetry={loadProfile} />
        </section>
        <section className="card" aria-labelledby="resume-heading">
          <h2 id="resume-heading">
            <Icon name="file" size={20} /> Your resume
          </h2>
          <ResumeCard
            state={resumeState}
            resume={resume}
            count={resumeCount}
            onRetry={loadResume}
          />
        </section>
        <MailboxCard notice={mailNotice} onUnauthorized={goToLogin} onSynced={loadApplications} />
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
