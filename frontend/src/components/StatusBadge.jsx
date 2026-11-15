// Colored pill for an application status. The text is always shown, so color is never the only cue.
const VARIANTS = {
  draft: 'neutral',
  saved: 'neutral',
  applied: 'info',
  submitted: 'info',
  interview: 'progress',
  interviewing: 'progress',
  screening: 'progress',
  offer: 'success',
  accepted: 'success',
  hired: 'success',
  pending: 'warning',
  withdrawn: 'warning',
  rejected: 'danger',
  declined: 'danger',
  failed: 'danger',
};

export default function StatusBadge({ status }) {
  const label = status ? String(status) : 'Unknown';
  const variant = VARIANTS[label.toLowerCase()] ?? 'neutral';
  return <span className={`status-badge status-badge--${variant}`}>{label}</span>;
}
