import { useId, useState } from 'react';
import { formatLocations } from '../utils/jobFilters';
import { describeJobTime, safeHttpUrl, sourceLabel } from '../utils/relativeTime';

const REMOTE_LABELS = { remote: 'Remote', hybrid: 'Hybrid', onsite: 'On-site' };

// Stable color slot (0-5) per category so the same category always looks the same.
function chipSlot(category) {
  let hash = 0;
  for (const char of category) hash = (hash * 31 + char.charCodeAt(0)) % 6;
  return hash;
}

// match is null until the AI module ships (Phase 5); then it carries {score, ...}.
export default function JobDetails({ job, compact = false }) {
  const descriptionId = useId();
  const [open, setOpen] = useState(false);
  const details = [job.companyName, formatLocations(job), REMOTE_LABELS[job.remoteType]]
    .filter(Boolean)
    .join(' · ');
  const source = sourceLabel(job.source);
  const time = describeJobTime(job);
  const postingUrl = safeHttpUrl(job.sourceUrl);

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
          <time className="job-item__time" dateTime={job.postedAt || job.createdAt} title={time.title}>
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
      {!compact &&
        (job.match ? (
          <p className="job-item__match">Match score: {job.match.score}</p>
        ) : (
          <p className="job-item__match muted">Match with your base resume: coming soon</p>
        ))}
      {job.description && (
        <>
          <button
            type="button"
            className="job-item__toggle"
            aria-expanded={open}
            aria-controls={descriptionId}
            onClick={() => setOpen((value) => !value)}
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
