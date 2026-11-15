import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Avatar from './Avatar';

describe('Avatar', () => {
  it('shows first and last initials in upper case', () => {
    render(<Avatar name="ada lovelace byron" />);
    expect(screen.getByText('AB')).toBeInTheDocument();
  });

  it('shows one initial for a single name', () => {
    render(<Avatar name="Ada" />);
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('falls back to a question mark when the name is missing', () => {
    render(<Avatar name="  " />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('is hidden from assistive technology', () => {
    render(<Avatar name="Ada Lovelace" />);
    expect(screen.getByText('AL')).toHaveAttribute('aria-hidden', 'true');
  });
});
