import { useId } from 'react';

// Status to colour family; matches StatusBadge so both read the same.
const VARIANTS = {
  draft: 'neutral',
  applied: 'info',
  interviewing: 'progress',
  offer: 'success',
  rejected: 'danger',
  withdrawn: 'warning',
};

const capitalize = (value) => value.charAt(0).toUpperCase() + value.slice(1);

// The select is the status display: coloured for the current status, with a leading dot, and the
// status word is always visible text. The label is read by screen readers only.
export default function StatusSelect({ label, statuses, value, disabled, onChange }) {
  const id = useId();
  const variant = VARIANTS[value] ?? 'neutral';
  return (
    <div className={`status-select status-select--${variant}`}>
      <label className="sr-only" htmlFor={id}>
        {label}
      </label>
      <span className="status-select__dot" aria-hidden="true" />
      <select
        id={id}
        className="status-select__input"
        value={value}
        disabled={disabled}
        onChange={onChange}
      >
        {statuses.map((status) => (
          <option
            key={status}
            value={status}
            className={`status-option status-option--${VARIANTS[status] ?? 'neutral'}`}
          >
            {capitalize(status)}
          </option>
        ))}
      </select>
    </div>
  );
}
