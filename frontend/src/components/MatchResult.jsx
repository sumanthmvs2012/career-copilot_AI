import { useId } from 'react';
import Alert from './Alert';
import StatusBadge from './StatusBadge';

const BANDS = [
  { min: 70, variant: 'success', label: 'Strong match' },
  { min: 40, variant: 'warning', label: 'Partial match' },
  { min: 0, variant: 'danger', label: 'Weak match' },
];

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
        <p className="match-result__number">
          <strong>{score}</strong>
          <span> out of 100</span>
        </p>
        <span className={`status-badge status-badge--${band.variant}`}>{band.label}</span>
        <div
          className="match-bar"
          role="meter"
          aria-label="Match score"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={score}
          aria-valuetext={`${score} out of 100, ${band.label}`}
        >
          <div
            className={`match-bar__fill match-bar__fill--${band.variant}`}
            style={{ width: `${score}%` }}
          />
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
