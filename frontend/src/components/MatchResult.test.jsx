import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MatchResult from './MatchResult';

const MATCH = {
  jobId: 1,
  resumeId: 2,
  score: 72,
  matchedSkills: ['Java', 'SQL'],
  missingSkills: ['Kafka', 'React'],
  nearMatchedSkills: [{ jobSkill: 'React', resumeSkill: 'React Native' }],
  missingKeywords: ['microservices'],
  suggestedHeadline: 'Senior Backend Engineer',
  locationFit: { status: 'partial', note: 'Same country, different city.' },
  createdAt: '2026-11-15T10:00:00Z',
};

function section(name) {
  return screen.getByRole('region', { name });
}

describe('MatchResult', () => {
  it('shows the score as text, a labelled meter and a band label', () => {
    render(<MatchResult match={MATCH} />);
    expect(screen.getByText('72')).toBeInTheDocument();
    expect(screen.getByText('out of 100')).toBeInTheDocument();
    expect(screen.getByText('Strong match')).toBeInTheDocument();
    const meter = screen.getByRole('meter', { name: 'Match score' });
    expect(meter).toHaveAttribute('aria-valuenow', '72');
  });

  it.each([
    [70, 'Strong match'],
    [69, 'Partial match'],
    [40, 'Partial match'],
    [39, 'Weak match'],
    [0, 'Weak match'],
  ])('labels a score of %i as %s', (score, label) => {
    render(<MatchResult match={{ ...MATCH, score }} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('clamps an out-of-range score', () => {
    render(<MatchResult match={{ ...MATCH, score: 150 }} />);
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '100');
  });

  it('lists every section', () => {
    render(<MatchResult match={MATCH} />);
    expect(within(section('Matched skills')).getByText('Java')).toBeInTheDocument();
    expect(within(section('Missing skills')).getByText('Kafka')).toBeInTheDocument();
    expect(within(section('Near matches')).getByText('React vs React Native')).toBeInTheDocument();
    expect(within(section('Missing keywords')).getByText('microservices')).toBeInTheDocument();
    expect(
      within(section('Suggested headline')).getByText('Senior Backend Engineer'),
    ).toBeInTheDocument();
    const location = section('Location fit');
    expect(within(location).getByText('partial')).toHaveClass('status-badge--warning');
    expect(within(location).getByText(/Same country, different city\./)).toBeInTheDocument();
  });

  it('shows friendly empty notes for empty or missing lists', () => {
    render(
      <MatchResult
        match={{
          jobId: 1,
          score: 0,
          matchedSkills: [],
          locationFit: null,
          suggestedHeadline: null,
        }}
      />,
    );
    expect(within(section('Matched skills')).getByText(/None of the job/)).toBeInTheDocument();
    expect(within(section('Missing skills')).getByText(/every skill/)).toBeInTheDocument();
    expect(within(section('Near matches')).getByText('No near matches.')).toBeInTheDocument();
    expect(
      within(section('Suggested headline')).getByText('No headline suggestion.'),
    ).toBeInTheDocument();
    expect(
      within(section('Location fit')).getByText('Location fit is unknown.'),
    ).toBeInTheDocument();
  });

  it('shows loading, error and empty states', () => {
    const { rerender } = render(<MatchResult loading />);
    expect(screen.getByRole('status')).toHaveTextContent('Checking your match');
    rerender(<MatchResult error="We could not match this job." />);
    expect(screen.getByRole('alert')).toHaveTextContent('We could not match this job.');
    rerender(<MatchResult match={null} />);
    expect(screen.getByText('No match yet.')).toBeInTheDocument();
  });

  it('draws a circular gauge that fills in proportion to the score', () => {
    const { container } = render(<MatchResult match={{ ...MATCH, score: 25 }} />);
    const fill = container.querySelector('.match-gauge__fill');
    const circumference = 2 * Math.PI * 34;
    expect(Number(fill.getAttribute('stroke-dashoffset'))).toBeCloseTo(circumference * 0.75, 3);
    expect(container.querySelector('.match-gauge svg')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('meter')).toHaveAttribute(
      'aria-valuetext',
      '25 out of 100, Weak match',
    );
  });

  it('shows the optional note in an info alert, and nothing when absent', () => {
    const note = 'No known skills were found in this job description, so the score cannot be calculated.';
    const { rerender } = render(<MatchResult match={{ ...MATCH, note }} />);
    expect(screen.getByText(note)).toBeInTheDocument();
    expect(screen.getByText(note)).toHaveClass('alert--info');
    rerender(<MatchResult match={MATCH} />);
    expect(screen.queryByText(note)).not.toBeInTheDocument();
  });
});
