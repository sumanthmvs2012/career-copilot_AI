import { describe, expect, it } from 'vitest';
import { describeJobTime, formatRelativeTime } from './relativeTime';

const NOW = new Date('2026-11-15T12:00:00Z');
const ago = (ms) => new Date(NOW.getTime() - ms).toISOString();
const MIN = 60 * 1000;
const DAY = 24 * 60 * MIN;

describe('formatRelativeTime', () => {
  it('formats units with singular and plural', () => {
    expect(formatRelativeTime(ago(10 * 1000), NOW)).toBe('just now');
    expect(formatRelativeTime(ago(MIN), NOW)).toBe('1 minute ago');
    expect(formatRelativeTime(ago(5 * MIN), NOW)).toBe('5 minutes ago');
    expect(formatRelativeTime(ago(3 * 60 * MIN), NOW)).toBe('3 hours ago');
    expect(formatRelativeTime(ago(3 * DAY), NOW)).toBe('3 days ago');
    expect(formatRelativeTime(ago(65 * DAY), NOW)).toBe('2 months ago');
    expect(formatRelativeTime(ago(800 * DAY), NOW)).toBe('2 years ago');
  });

  it('treats future times as just now and bad input as empty', () => {
    expect(formatRelativeTime(ago(-5 * MIN), NOW)).toBe('just now');
    expect(formatRelativeTime(null, NOW)).toBe('');
    expect(formatRelativeTime('nope', NOW)).toBe('');
  });
});

describe('describeJobTime', () => {
  it('prefers postedAt', () => {
    const t = describeJobTime({ postedAt: ago(3 * DAY), createdAt: ago(MIN) }, NOW);
    expect(t.label).toBe('Posted 3 days ago');
    expect(t.title).toBeTruthy();
  });

  it('falls back to createdAt as saved time', () => {
    expect(describeJobTime({ postedAt: null, createdAt: ago(5 * MIN) }, NOW).label).toBe(
      'Saved 5 minutes ago',
    );
  });

  it('returns null with no usable date', () => {
    expect(describeJobTime({}, NOW)).toBeNull();
  });
});
