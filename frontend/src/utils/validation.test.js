import { describe, expect, it } from 'vitest';
import { validateLogin } from './validation';

describe('validateLogin', () => {
  it('returns no errors for valid input', () => {
    expect(validateLogin({ email: 'jane@example.com', password: 'x' })).toEqual({});
  });

  it('requires email and password', () => {
    expect(validateLogin({ email: '', password: '' })).toEqual({
      email: 'Email is required.',
      password: 'Password is required.',
    });
  });

  it('treats a whitespace-only email as missing', () => {
    expect(validateLogin({ email: '   ', password: 'x' }).email).toBe('Email is required.');
  });

  it('handles undefined values', () => {
    expect(validateLogin({})).toEqual({
      email: 'Email is required.',
      password: 'Password is required.',
    });
  });

  it.each(['plain', 'no-at.com', 'a@b', 'a b@c.com', '@example.com'])(
    'rejects invalid email %s',
    (email) => {
      expect(validateLogin({ email, password: 'x' }).email).toBe('Enter a valid email address.');
    },
  );

  it('accepts an email with surrounding whitespace', () => {
    expect(validateLogin({ email: '  jane@example.com ', password: 'x' })).toEqual({});
  });
});
