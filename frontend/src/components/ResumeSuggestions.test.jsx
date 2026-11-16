import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ResumeSuggestions from './ResumeSuggestions';

const SUGGESTIONS = [
  { type: 'general', text: 'Keep it to one page.' },
  { type: 'add_skill', text: 'Add Kafka to your resume if you have used it.' },
  { type: 'near_match', text: 'Use PostgreSQL wording if true.' },
  { type: 'add_keyword', text: 'Mention microservices.' },
  { type: 'headline', text: 'Use Backend Engineer as headline.' },
  { type: 'location', text: 'The role is remote.' },
  { type: 'mystery', text: 'Something new.' },
];

describe('ResumeSuggestions', () => {
  it('shows a friendly empty state', () => {
    render(<ResumeSuggestions suggestions={[]} />);
    expect(screen.getByText('No suggestions for this job.')).toBeInTheDocument();
  });

  it('groups by type in a fixed order with friendly titles and priority words', () => {
    render(<ResumeSuggestions suggestions={SUGGESTIONS} />);
    const titles = screen
      .getAllByRole('heading', { level: 3 })
      .map((heading) => heading.firstElementChild.nextSibling.textContent);
    expect(titles).toEqual([
      'Add a skill',
      'Close match',
      'Keywords to include',
      'Headline',
      'Location',
      'Tips',
      'Other',
    ]);
    const skill = screen.getByRole('region', { name: /Add a skill/ });
    expect(within(skill).getByText('High priority')).toBeInTheDocument();
    expect(screen.getByText(/7 ideas to strengthen this application/)).toBeInTheDocument();
  });

  it('copies one suggestion and confirms with text', async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
    render(<ResumeSuggestions suggestions={[SUGGESTIONS[1]]} />);
    await user.click(screen.getByRole('button', { name: 'Copy suggestion: Add a skill' }));
    expect(write).toHaveBeenCalledWith(SUGGESTIONS[1].text);
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('reports a failed copy', async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'));
    render(<ResumeSuggestions suggestions={[SUGGESTIONS[1]]} />);
    await user.click(screen.getByRole('button', { name: /Copy suggestion/ }));
    expect(await screen.findByText('Copy failed')).toBeInTheDocument();
  });

  it('renders a checkbox per suggestion in checklist mode', () => {
    render(<ResumeSuggestions suggestions={SUGGESTIONS} checklist />);
    expect(screen.getAllByRole('checkbox')).toHaveLength(7);
  });
});
