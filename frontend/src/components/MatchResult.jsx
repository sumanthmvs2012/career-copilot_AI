import { useId } from 'react';
import Alert from './Alert';
import StatusBadge from './StatusBadge';

const BANDS = [
  { min: 70, variant: 'success', label: 'Strong match' },
  { min: 40, variant: 'warning', label: 'Partial match' },
  { min: 0, variant: 'danger', label: 'Weak match' },
];

const RADIUS = 34;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function clampScore(score) {
  const value = Number(score);
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, Math.round(value)));
}

function Section({ title, empty, children }) {
  const id = useId();
  return (
    <section className="match-result__section" aria-labelledby={id}>
      <h3 id={id} className="match-result__heading">
        {title}
      </h3>
      {children ?? <p className="muted">{empty}</p>}
    </section>
  );
}

function SkillChips({ items, tone }) {
  if (!items || items.length === 0) return null;
  return (
    <ul className="chip-list">
      {items.map((item) => (
        <li key={item} className={`skill-chip skill-chip--${tone}`}>
          {item}
        </li>
      ))}
    </ul>
  );
}

// Shows a stored or fresh match result {score, matchedSkills, ...}. Pass `loading` or `error`
// while the result is being fetched; with no match and neither, an empty note is shown.
export default function MatchResult({ match, loading = false, error = '' }) {
  if (loading) return <p role="status">Checking your match…</p>;
  if (error) return <Alert variant="error">{error}</Alert>;
  if (!match) return <p className="muted">No match yet.</p>;

  const score = clampScore(match.score);
  const band = BANDS.find((b) => score >= b.min);
  const matched = match.matchedSkills ?? [];
  const missing = match.missingSkills ?? [];
  const near = match.nearMatchedSkills ?? [];
  const keywords = match.missingKeywords ?? [];
  const location = match.locationFit;

  return (
    <div className="match-result">
      <div className="match-result__score">
        <div
          className={`match-gauge match-gauge--${band.variant}`}
          role="meter"
          aria-label="Match score"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={score}
          aria-valuetext={`${score} out of 100, ${band.label}`}
        >
          <svg viewBox="0 0 80 80" width="104" height="104" aria-hidden="true" focusable="false">
            <circle className="match-gauge__track" cx="40" cy="40" r={RADIUS} />
            <circle
              className="match-gauge__fill"
              cx="40"
              cy="40"
              r={RADIUS}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={CIRCUMFERENCE * (1 - score / 100)}
              transform="rotate(-90 40 40)"
            />
          </svg>
          <p className="match-result__number match-gauge__value">
            <strong>{score}</strong>
            <span> out of 100</span>
          </p>
        </div>
        <div className="match-result__summary">
          <span className={`status-badge status-badge--${band.variant}`}>{band.label}</span>
          <p className="muted">
            {score >= 70
              ? 'Your resume covers most of what this job asks for.'
              : score >= 40
                ? 'You match part of the job. See what is missing below.'
                : 'Several key skills are missing. The tips below can help.'}
          </p>
        </div>
      </div>

      <Section title="Matched skills" empty="None of the job's skills were found in your resume.">
        {matched.length > 0 ? <SkillChips items={matched} tone="good" /> : undefined}
      </Section>
      <Section title="Missing skills" empty="You have every skill the job lists.">
        {missing.length > 0 ? <SkillChips items={missing} tone="bad" /> : undefined}
      </Section>
      <Section title="Near matches" empty="No near matches.">
        {near.length > 0 ? (
          <ul className="match-result__list">
            {near.map((pair) => (
              <li key={`${pair.jobSkill}-${pair.resumeSkill}`}>
                {pair.jobSkill} vs {pair.resumeSkill}
              </li>
            ))}
          </ul>
        ) : undefined}
      </Section>
      <Section title="Missing keywords" empty="No missing keywords.">
        {keywords.length > 0 ? <SkillChips items={keywords} tone="neutral" /> : undefined}
      </Section>
      <Section title="Suggested headline" empty="No headline suggestion.">
        {match.suggestedHeadline ? <p>{match.suggestedHeadline}</p> : undefined}
      </Section>
      <Section title="Location fit" empty="Location fit is unknown.">
        {location ? (
          <p>
            <StatusBadge status={location.status} /> {location.note}
          </p>
        ) : undefined}
      </Section>
    </div>
  );
}
