import { useId, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from './Alert';
import Button from './Button';
import MatchResult from './MatchResult';
import ResumeSuggestions from './ResumeSuggestions';
import TrackJobButton from './TrackJobButton';
import { getStoredMatch, matchJob } from '../services/jobService';
import { getSuggestions, listResumes, tailorResume } from '../services/resumeService';
import { findBaseResume } from '../utils/resumes';
import { formatLocations } from '../utils/jobFilters';
import { describeJobTime, safeHttpUrl, sourceLabel } from '../utils/relativeTime';

const REMOTE_LABELS = { remote: 'Remote', hybrid: 'Hybrid', onsite: 'On-site' };

// Stable color slot (0-5) per category so the same category always looks the same.
function chipSlot(category) {
  let hash = 0;
  for (const char of category) hash = (hash * 31 + char.charCodeAt(0)) % 6;
  return hash;
}

// job.match, when given, is a result already computed (see MatchResult); otherwise the user can run
// one with "Match with my resume", and a stored one is loaded the first time the details expand.
export default function JobDetails({ job, compact = false, onTracked }) {
  const descriptionId = useId();
  const suggestionsId = useId();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [match, setMatch] = useState(job.match ?? null);
  const [matchState, setMatchState] = useState('idle'); // idle | loading | no-resume | error
  const [storedChecked, setStoredChecked] = useState(false);
  const [tailorState, setTailorState] = useState('idle'); // idle | loading | no-resume | error
  const [suggestions, setSuggestions] = useState(null);
  const [suggestionsState, setSuggestionsState] = useState('idle'); // idle | loading | no-resume | error
  const [showSuggestions, setShowSuggestions] = useState(false);
  const details = [job.companyName, formatLocations(job), REMOTE_LABELS[job.remoteType]]
    .filter(Boolean)
    .join(' · ');
  const source = sourceLabel(job.source);
  const time = describeJobTime(job);
  const postingUrl = safeHttpUrl(job.sourceUrl);

  async function loadStored() {
    setStoredChecked(true);
    try {
      const stored = await getStoredMatch(job.id);
      if (stored) setMatch((current) => current ?? stored);
    } catch (error) {
      // A missing stored result is normal; status only, never log text.
      console.error('Loading stored match failed', { status: error?.status ?? 'unknown' });
    }
  }

  function toggle() {
    if (!open && !compact && job.id != null && !match && !storedChecked) loadStored();
    setOpen((value) => !value);
  }

  async function runMatch() {
    setMatchState('loading');
    try {
      setMatch(await matchJob(job.id));
      setMatchState('idle');
    } catch (error) {
      console.error('Matching job failed', { status: error?.status ?? 'unknown' });
      setMatchState(error?.status === 409 ? 'no-resume' : 'error');
    }
  }

  // Resolves to the base resume, or null when the user has no uploaded resume.
  async function findBase() {
    return findBaseResume(await listResumes());
  }

  function handleTailorError(error, setState) {
    console.error('Tailoring step failed', { status: error?.status ?? 'unknown' });
    setState(error?.status === 404 || error?.status === 409 ? 'no-resume' : 'error');
  }

  async function runTailor() {
    setTailorState('loading');
    try {
      const base = await findBase();
      if (!base) return setTailorState('no-resume');
      const copy = await tailorResume(base.id, job.id);
      setTailorState('idle');
      navigate(`/resume/${copy.id}`);
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      handleTailorError(error, setTailorState);
    }
  }

  async function loadSuggestions() {
    setSuggestionsState('loading');
    try {
      const base = await findBase();
      if (!base) return setSuggestionsState('no-resume');
      const result = await getSuggestions(base.id, job.id);
      setSuggestions(result?.suggestions ?? []);
      setSuggestionsState('idle');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      handleTailorError(error, setSuggestionsState);
    }
  }

  function toggleSuggestions() {
    const next = !showSuggestions;
    setShowSuggestions(next);
    if (next && suggestions === null && suggestionsState !== 'loading') loadSuggestions();
  }

  const noResumeNote = (
    <Alert variant="warning">
      Upload a resume first to tailor it. <Link to="/resume">Go to your resumes</Link>
    </Alert>
  );

  return (
    <div className={compact ? 'job-item job-item--compact' : 'job-item'}>
      <strong className="job-item__title">{job.title || 'Untitled job'}</strong>
      {job.category && (
        <span className={`job-chip job-chip--${chipSlot(job.category)}`}>{job.category}</span>
      )}
      {details && <div className="muted">{details}</div>}
      <div className="job-item__meta muted">
        {source && <span className="job-item__source">{source}</span>}
        {time && (
          <time
            className="job-item__time"
            dateTime={job.postedAt || job.createdAt}
            title={time.title}
          >
            {time.label}
          </time>
        )}
      </div>
      {postingUrl && (
        <a
          className="job-item__link"
          href={postingUrl}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`View posting for ${job.title || 'this job'} (opens in a new tab)`}
        >
          View posting
        </a>
      )}
      {!compact && job.id != null && (
        <div className="job-item__match">
          <Button
            variant="secondary"
            onClick={runMatch}
            loading={matchState === 'loading'}
            loadingText="Matching…"
          >
            {match ? 'Match again' : 'Match with my resume'}
          </Button>
          {matchState === 'no-resume' && (
            <Alert variant="warning">
              Upload a resume first to see your match. <Link to="/resume">Go to your resumes</Link>
            </Alert>
          )}
          {matchState === 'error' && (
            <Alert variant="error">We could not match this job. Please try again.</Alert>
          )}
          {match && <MatchResult match={match} />}
        </div>
      )}
      {!compact && job.id != null && (
        <div className="job-item__tailor">
          <Button
            variant="secondary"
            onClick={runTailor}
            loading={tailorState === 'loading'}
            loadingText="Tailoring…"
          >
            Tailor my resume for this job
          </Button>
          {tailorState === 'no-resume' && noResumeNote}
          {tailorState === 'error' && (
            <>
              <Alert variant="error">We could not tailor your resume. Please try again.</Alert>
              <Button variant="secondary" onClick={runTailor}>
                Try again
              </Button>
            </>
          )}
          <button
            type="button"
            className="job-item__toggle"
            aria-expanded={showSuggestions}
            aria-controls={suggestionsId}
            onClick={toggleSuggestions}
          >
            {showSuggestions ? 'Hide suggestions' : 'Get suggestions'}
          </button>
          <div id={suggestionsId} className="suggestions-region">
            {showSuggestions && suggestionsState === 'loading' && (
              <p role="status">Loading suggestions…</p>
            )}
            {showSuggestions && suggestionsState === 'no-resume' && noResumeNote}
            {showSuggestions && suggestionsState === 'error' && (
              <>
                <Alert variant="error">We could not load suggestions. Please try again.</Alert>
                <Button variant="secondary" onClick={loadSuggestions}>
                  Try again
                </Button>
              </>
            )}
            {showSuggestions && suggestions !== null && suggestionsState === 'idle' && (
              <ResumeSuggestions suggestions={suggestions} />
            )}
          </div>
        </div>
      )}
      {job.id != null && (
        <TrackJobButton jobId={job.id} jobTitle={job.title} onTracked={onTracked} />
      )}
      {job.description && (
        <>
          <button
            type="button"
            className="job-item__toggle"
            aria-expanded={open}
            aria-controls={descriptionId}
            onClick={toggle}
          >
            {open ? 'Hide description' : 'Show description'}
          </button>
          {open && (
            <div id={descriptionId} className="job-item__description">
              {job.description}
            </div>
          )}
        </>
      )}
    </div>
  );
}
