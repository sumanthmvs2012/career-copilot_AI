import Icon from './Icon';

// Friendly "nothing here yet" block: decorative icon, a short message and an optional action.
export default function EmptyState({ icon = 'inbox', children, action = null }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon">
        <Icon name={icon} size={22} />
      </span>
      <div className="empty-state__body">
        <p className="empty-state__text">{children}</p>
        {action}
      </div>
    </div>
  );
}
