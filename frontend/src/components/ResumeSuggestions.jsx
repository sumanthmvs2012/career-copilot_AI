import { useEffect, useId, useRef, useState } from 'react';
import Icon from './Icon';
import { copyText } from '../utils/clipboard';

// Friendly title, icon, short hint and priority per suggestion type. Priority is always shown as
// words ("High", "Medium", "Low"), never by color alone.
const TYPES = {
  add_skill: {
    title: 'Add a skill',
    icon: 'plus',
    hint: 'The job asks for these and your resume does not show them.',
    priority: 'high',
  },
  near_match: {
    title: 'Close match',
    icon: 'swap',
    hint: 'You have something similar. Use the job’s wording if it is true for you.',
    priority: 'medium',
  },
  add_keyword: {
    title: 'Keywords to include',
    icon: 'tag',
    hint: 'Words the posting repeats that your resume leaves out.',
    priority: 'medium',
  },
  headline: {
    title: 'Headline',
    icon: 'headline',
    hint: 'A headline that mirrors the role.',
    priority: 'medium',
  },
  location: {
    title: 'Location',
    icon: 'pin',
    hint: 'How your location lines up with the job.',
    priority: 'low',
  },
  general: {
    title: 'Tips',
    icon: 'bulb',
    hint: 'Good habits for this application.',
    priority: 'low',
  },
};
const OTHER = { title: 'Other', icon: 'bulb', hint: '', priority: 'low' };
const TYPE_ORDER = Object.keys(TYPES);
const PRIORITY_LABELS = { high: 'High', medium: 'Medium', low: 'Low' };

function CopyButton({ text, label }) {
  const [state, setState] = useState('idle'); // idle | copied | failed
  const timer = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy() {
    setState((await copyText(text)) ? 'copied' : 'failed');
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setState('idle'), 2000);
  }

  return (
    <span className="suggestion__copy">
      <button type="button" className="suggestion__copy-btn" onClick={copy} aria-label={label}>
        <Icon name="copy" size={14} /> Copy
      </button>
      <span role="status" className="suggestion__copy-note">
        {state === 'copied' && 'Copied'}
        {state === 'failed' && 'Copy failed'}
      </span>
    </span>
  );
}

// Suggestions grouped by type as short cards. With checklist, each one gets a checkbox the user can
// tick off locally; ticking never edits the resume.
export default function ResumeSuggestions({ suggestions, checklist = false }) {
  const baseId = useId();
  if (!suggestions || suggestions.length === 0) {
    return (
      <div className="suggestions-empty">
        <Icon name="check" size={22} className="suggestions-empty__icon" />
        <div>
          <p className="suggestions-empty__title">No suggestions for this job.</p>
          <p className="muted">Your resume already lines up well with what the posting asks for.</p>
        </div>
      </div>
    );
  }
  const groups = new Map();
  for (const item of suggestions) {
    const key = TYPES[item.type] ? item.type : 'other';
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  const rank = (key) => (TYPE_ORDER.indexOf(key) === -1 ? 99 : TYPE_ORDER.indexOf(key));
  const keys = [...groups.keys()].sort((a, b) => rank(a) - rank(b));
  return (
    <div className="suggestions">
      <p className="suggestions__summary muted">
        {suggestions.length} {suggestions.length === 1 ? 'idea' : 'ideas'} to strengthen this
        application. Nothing is changed in your resume automatically.
      </p>
      {keys.map((key) => {
        const meta = TYPES[key] ?? OTHER;
        return (
          <section
            key={key}
            className={`suggestion-group suggestion-group--${meta.priority}`}
            aria-labelledby={`${baseId}-${key}`}
          >
            <h3 id={`${baseId}-${key}`} className="suggestions__heading">
              <span className="suggestions__icon">
                <Icon name={meta.icon} size={16} />
              </span>
              {meta.title}
              <span className={`priority-pill priority-pill--${meta.priority}`}>
                {PRIORITY_LABELS[meta.priority]} priority
              </span>
            </h3>
            {meta.hint && <p className="suggestions__hint muted">{meta.hint}</p>}
            <ul className="suggestions__list">
              {groups.get(key).map((item, index) => (
                <li key={`${key}-${index}`} className="suggestion">
                  {checklist ? (
                    <label className="suggestions__check">
                      <input type="checkbox" /> <span>{item.text}</span>
                    </label>
                  ) : (
                    <span className="suggestion__text">{item.text}</span>
                  )}
                  <CopyButton text={item.text} label={`Copy suggestion: ${meta.title}`} />
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
