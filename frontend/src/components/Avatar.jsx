// Circle with the user's initials. Decorative: the full name is always shown next to it.
function getInitials(name) {
  const parts = String(name ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
}

export default function Avatar({ name, className = '' }) {
  return (
    <span className={['avatar', className].filter(Boolean).join(' ')} aria-hidden="true">
      {getInitials(name)}
    </span>
  );
}
