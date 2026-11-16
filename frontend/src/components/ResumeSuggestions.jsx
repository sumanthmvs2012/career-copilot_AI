import { useId } from 'react';

const TYPE_LABELS = {
  add_skill: 'Skills to add',
  near_match: 'Near matches',
  add_keyword: 'Keywords to add',
  headline: 'Headline',
  location: 'Location',
  general: 'General',
};
const TYPE_ORDER = Object.keys(TYPE_LABELS);

function labelFor(type) {
  return TYPE_LABELS[type] ?? 'Other';
}

// Plain-text suggestions grouped by type. With checklist, each one gets a checkbox the user can tick
// off locally; ticking never edits the resume.
export default function ResumeSuggestions({ suggestions, checklist = false }) {
  const baseId = useId();
  if (!suggestions || suggestions.length === 0) {
    return <p className="muted">No suggestions for this job.</p>;
  }
  const groups = new Map();
  for (const item of suggestions) {
    const key = TYPE_LABELS[item.type] ? item.type : 'other';
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  const keys = [...groups.keys()].sort(
    (a, b) =>
      (TYPE_ORDER.indexOf(a) === -1 ? 99 : TYPE_ORDER.indexOf(a)) -
      (TYPE_ORDER.indexOf(b) === -1 ? 99 : TYPE_ORDER.indexOf(b)),
  );
  return (
    <div className="suggestions">
      {keys.map((key) => (
        <section key={key} aria-labelledby={`${baseId}-${key}`}>
          <h3 id={`${baseId}-${key}`} className="suggestions__heading">
            {labelFor(key)}
          </h3>
          <ul className="suggestions__list">
            {groups.get(key).map((item, index) => (
              <li key={`${key}-${index}`}>
                {checklist ? (
                  <label className="suggestions__check">
                    <input type="checkbox" /> <span>{item.text}</span>
                  </label>
                ) : (
                  item.text
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
