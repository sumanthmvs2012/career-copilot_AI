import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import EmptyState from './EmptyState';

describe('EmptyState', () => {
  it('shows the message and an optional action, with a decorative icon', () => {
    const { container } = render(
      <EmptyState action={<a href="/x">Do it</a>}>Nothing yet.</EmptyState>,
    );
    expect(screen.getByText('Nothing yet.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Do it' })).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });
});
