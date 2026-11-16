import { useId, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from './Alert';
import Button from './Button';
import Icon from './Icon';
import MatchResult from './MatchResult';
import ResumeSuggestions from './ResumeSuggestions';
import TrackJobButton from './TrackJobButton';
import { getStoredMatch, matchJob } from '../services/jobService';
import { getSuggestions, listResumes, tailorResume } from '../services/resumeService';
import { findBaseResume } from '../utils/resumes';
import { formatLocations, summarizeLocations } from '../utils/jobFilters';
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
// variant="row" runs the same logic as one list row: title, category pill and details on the left,
// compact actions on the right, and every result in a panel directly under the row.
export default function JobDetails({ job, compact = false, variant = 'card', onTracked }) {
  const isRow = variant === 'row';
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
  // The server's own words for a 404/409 (e.g. "Job not found."); empty means use the default text.
  const [tailorMessage, setTailorMessage] = useState('');
  const [suggestionsMessage, setSuggestionsMessage] = useState('');
  const details = [job.companyName, formatLocations(job), REMOTE_LABELS[job.remoteType]]
    .filter(Boolean)
    .join(' · ');
  const rowDetails = [job.companyName, summarizeLocations(job), REMOTE_LABELS[job.remoteType]]
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

  function handleTailorError(error, setState, setMessage) {
    console.error('Tailoring step failed', { status: error?.status ?? 'unknown' });
    const missing = error?.status === 404 || error?.status === 409;
    const text = error?.data?.message;
    setMessage(missing && typeof text === 'string' ? text : '');
    setState(missing ? 'no-resume' : 'error');
  }

  async function runTailor() {
    setTailorState('loading');
    try {
      const base = await findBase();
      if (!base) {
        setTailorMessage('');
        return setTailorState('no-resume');
      }
      const copy = await tailorResume(base.id, job.id);
      setTailorState('idle');
      navigate(`/resume/${copy.id}`);
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      handleTailorError(error, setTailorState, setTailorMessage);
    }
  }

  async function loadSuggestions() {
    setSuggestionsState('loading');
    try {
      const base = await findBase();
      if (!base) {
        setSuggestionsMessage('');
        return setSuggestionsState('no-resume');
      }
      const result = await getSuggestions(base.id, job.id);
      setSuggestions(result?.suggestions ?? []);
      setSuggestionsState('idle');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      handleTailorError(error, setSuggestionsState, setSuggestionsMessage);
    }
  }

  function toggleSuggestions() {
    const next = !showSuggestions;
    setShowSuggestions(next);
    if (next && suggestions === null && suggestionsState !== 'loading') loadSuggestions();
  }

  const noResumeNote = (message) => (
    <Alert variant="warning">
      {message || 'Upload a resume first to tailor it.'} <Link to="/resume">Go to your resumes</Link>
    </Alert>
  );

  if (isRow) {
    const hasId = job.id != null && !compact;
    const title = job.title || 'Untitled job';
    const showPanel =
      Boolean(match) ||
      matchState === 'no-resume' ||
      matchState === 'error' ||
      tailorState === 'no-resume' ||
      tailorState === 'error' ||
      showSuggestions ||
      open;
    const panelId = `${descriptionId}-panel`;
    return (
      <article className="job-row">
        <div className="job-row__top">
          <div className="job-row__main">
            <div className="job-row__heading">
              <h3 className="job-row__title">{title}</h3>
              {job.category && (
                <span className={`job-chip job-chip--${chipSlot(job.category)}`}>
                  {job.category}
                </span>
              )}
            </div>
            {rowDetails && (
              <p className="job-row__details" title={formatLocations(job) || undefined}>
                {rowDetails}
              </p>
            )}
            <p className="job-row__meta muted">
              {source && <span className="job-item__source">{source}</span>}
              {time && (
                <time dateTime={job.postedAt || job.createdAt} title={time.title}>
                  {time.label}
                </time>
              )}
              {postingUrl && (
                <a
                  className="job-item__link"
                  href={postingUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={`View posting for ${title} (opens in a new tab)`}
                >
                  View posting <Icon name="external" size={12} />
                </a>
              )}
            </p>
          </div>
          <div className="job-row__actions" role="group" aria-label={`Actions for ${title}`}>
            {hasId && (
              <Button
                variant="secondary"
                className="btn--sm"
                onClick={runMatch}
                loading={matchState === 'loading'}
                loadingText="Matching…"
              >
                <Icon name="target" size={14} /> {match ? 'Match again' : 'Match with my resume'}
              </Button>
            )}
            {hasId && (
              <Button
                variant="secondary"
                className="btn--sm"
                onClick={runTailor}
                loading={tailorState === 'loading'}
                loadingText="Tailoring…"
              >
                <Icon name="wand" size={14} /> Tailor my resume
              </Button>
            )}
            {hasId && (
              <Button
                variant="secondary"
                className="btn--sm"
                aria-expanded={showSuggestions}
                aria-controls={showSuggestions ? suggestionsId : undefined}
                onClick={toggleSuggestions}
              >
                <Icon name="bulb" size={14} /> Suggestions
              </Button>
            )}
            {hasId && (
              <Button
                variant="secondary"
                className="btn--sm"
                onClick={() => navigate(`/jobs/${encodeURIComponent(job.id)}/interview`)}
                aria-label={`Interview questions for ${title}`}
              >
                <Icon name="chat" size={14} /> Interview
              </Button>
            )}
            {job.id != null && (
              <TrackJobButton
                jobId={job.id}
                jobTitle={job.title}
                onTracked={onTracked}
                label={
                  <>
                    <Icon name="bookmark" size={14} /> Track
                  </>
                }
                small
              />
            )}
            {job.description && (
              <Button
                variant="secondary"
                className="btn--sm"
                aria-expanded={open}
                aria-controls={open ? panelId : undefined}
                onClick={toggle}
              >
                <Icon name="chevron" size={14} className="chevron" />{' '}
                {open ? 'Hide details' : 'View details'}
              </Button>
            )}
          </div>
        </div>
        {showPanel && (
          <div className="job-row__panel" id={panelId}>
            {matchState === 'no-resume' && (
              <Alert variant="warning">
                Upload a resume first to see your match.{' '}
                <Link to="/resume">Go to your resumes</Link>
              </Alert>
            )}
            {matchState === 'error' && (
              <Alert variant="error">We could not match this job. Please try again.</Alert>
            )}
            {match && <MatchResult match={match} />}
            {tailorState === 'no-resume' && noResumeNote(tailorMessage)}
            {tailorState === 'error' && (
              <>
                <Alert variant="error">We could not tailor your resume. Please try again.</Alert>
                <Button variant="secondary" onClick={runTailor}>
                  Try again
                </Button>
              </>
            )}
            {showSuggestions && (
              <div id={suggestionsId} className="suggestions-region">
                {suggestionsState === 'loading' && <p role="status">Loading suggestions…</p>}
                {suggestionsState === 'no-resume' && noResumeNote(suggestionsMessage)}
                {suggestionsState === 'error' && (
                  <>
                    <Alert variant="error">We could not load suggestions. Please try again.</Alert>
                    <Button variant="secondary" onClick={loadSuggestions}>
                      Try again
                    </Button>
                  </>
                )}
                {suggestions !== null && suggestionsState === 'idle' && (
                  <ResumeSuggestions suggestions={suggestions} />
                )}
              </div>
            )}
            {open && job.description && (
              <div className="job-item__description">{job.description}</div>
            )}
          </div>
        )}
      </article>
    );
  }

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
          {tailorState === 'no-resume' && noResumeNote(tailorMessage)}
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
            {showSuggestions && suggestionsState === 'no-resume' && noResumeNote(suggestionsMessage)}
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
      {!compact && job.id != null && (
        <div className="job-item__interview">
          <Button
            variant="secondary"
            onClick={() => navigate(`/jobs/${encodeURIComponent(job.id)}/interview`)}
            aria-label={`Interview questions for ${job.title || 'this job'}`}
          >
            Interview questions
          </Button>
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
