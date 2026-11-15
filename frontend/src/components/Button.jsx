export default function Button({
  variant = 'primary',
  type = 'button',
  loading = false,
  loadingText = 'Loading…',
  fullWidth = false,
  disabled = false,
  children,
  className = '',
  ...rest
}) {
  const classes = ['btn', `btn--${variant}`, fullWidth ? 'btn--full' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading ? 'true' : undefined}
      {...rest}
    >
      {loading && <span className="btn__spinner" aria-hidden="true" />}
      {loading ? loadingText : children}
    </button>
  );
}
