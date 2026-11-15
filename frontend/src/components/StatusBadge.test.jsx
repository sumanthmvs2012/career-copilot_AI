import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('shows the status text with a matching variant', () => {
    render(<StatusBadge status="Applied" />);
    expect(screen.getByText('Applied')).toHaveClass('status-badge--info');
  });

  it('maps rejected to the danger variant', () => {
    render(<StatusBadge status="rejected" />);
    expect(screen.getByText('rejected')).toHaveClass('status-badge--danger');
  });

  it('uses the neutral variant for unknown statuses', () => {
    render(<StatusBadge status="mystery" />);
    expect(screen.getByText('mystery')).toHaveClass('status-badge--neutral');
  });

  it('shows Unknown when the status is missing', () => {
    render(<StatusBadge />);
    expect(screen.getByText('Unknown')).toBeInTheDocument();
  });
});
