import { describe, expect, it } from 'vitest';
import { validateJobMatch, validateLogin, validateProfile, validateSignup } from './validation';

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

describe('validateSignup', () => {
  const valid = {
    email: 'jane@example.com',
    password: 'secret-pass',
    confirmPassword: 'secret-pass',
  };

  it('returns no errors for valid input', () => {
    expect(validateSignup(valid)).toEqual({});
  });

  it('requires a password of at least 8 characters', () => {
    expect(validateSignup({ ...valid, password: 'short', confirmPassword: 'short' })).toEqual({
      password: 'Password must be at least 8 characters.',
    });
  });

  it('requires the confirmation to match', () => {
    expect(validateSignup({ ...valid, confirmPassword: 'other-pass' })).toEqual({
      confirmPassword: 'Passwords do not match.',
    });
  });

  it('requires a confirmation', () => {
    expect(validateSignup({ ...valid, confirmPassword: '' })).toEqual({
      confirmPassword: 'Please confirm your password.',
    });
  });
});

describe('validateProfile', () => {
  it('requires a full name', () => {
    expect(validateProfile({ fullName: '  ' }).fullName).toBe('Full name is required.');
  });

  it('accepts a valid profile with optional fields empty', () => {
    expect(validateProfile({ fullName: 'Jane', headline: '', location: '' })).toEqual({});
  });

  it('rejects fields over 200 characters', () => {
    const long = 'a'.repeat(201);
    const errors = validateProfile({ fullName: long, headline: long, location: long });
    expect(Object.keys(errors)).toEqual(['fullName', 'headline', 'location']);
  });
});

describe('validateJobMatch', () => {
  const valid = { description: 'a'.repeat(20) };

  it('accepts a description with optional fields empty', () => {
    expect(validateJobMatch({ ...valid, title: '', companyName: '', remoteType: '' })).toEqual({});
  });

  it('requires a description', () => {
    expect(validateJobMatch({ description: '   ' }).description).toBe(
      'Job description is required.',
    );
  });

  it('enforces the 20 to 20000 character range on the trimmed description', () => {
    expect(validateJobMatch({ description: ` ${'a'.repeat(19)} ` }).description).toMatch(
      /at least 20/,
    );
    expect(validateJobMatch({ description: 'a'.repeat(20000) })).toEqual({});
    expect(validateJobMatch({ description: 'a'.repeat(20001) }).description).toMatch(
      /at most 20000/,
    );
  });

  it('rejects over-long optional fields and a bad remote type', () => {
    const long = 'a'.repeat(201);
    const errors = validateJobMatch({
      ...valid,
      title: long,
      companyName: long,
      location: long,
      sourceUrl: 'h'.repeat(1001),
      remoteType: 'moon',
    });
    expect(Object.keys(errors)).toEqual([
      'title',
      'companyName',
      'location',
      'sourceUrl',
      'remoteType',
    ]);
  });
});
