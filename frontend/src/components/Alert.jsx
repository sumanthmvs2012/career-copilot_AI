export default function Alert({ variant = 'error', children }) {
  // Errors interrupt screen readers (role="alert"); success and warning are announced politely.
  const role = variant === 'error' ? 'alert' : 'status';

  return (
    <div className={`alert alert--${variant}`} role={role}>
      {children}
    </div>
  );
}
