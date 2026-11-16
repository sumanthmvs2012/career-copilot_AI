import { describe, expect, it } from 'vitest';
import { categoryLabel, groupQuestions, questionsToText } from './interview';

const QUESTIONS = [
  { id: 1, question: 'Tell me about a conflict.', category: 'behavioral' },
  { id: 2, question: 'Explain indexes.', category: 'technical' },
  { id: 3, question: 'Odd one.', category: 'mystery' },
  { id: 4, question: 'Why us?', category: 'company' },
];

describe('interview utils', () => {
  it('groups in a fixed order and puts unknown categories last', () => {
    const groups = groupQuestions(QUESTIONS);
    expect(groups.map((g) => g.label)).toEqual(['Technical', 'Behavioral', 'Company', 'Other']);
  });

  it('returns no groups for empty or missing input', () => {
    expect(groupQuestions([])).toEqual([]);
    expect(groupQuestions(undefined)).toEqual([]);
  });

  it('builds numbered plain text in display order', () => {
    expect(questionsToText(QUESTIONS)).toBe(
      '1. Explain indexes.\n2. Tell me about a conflict.\n3. Why us?\n4. Odd one.',
    );
  });

  it('labels categories', () => {
    expect(categoryLabel('resume_gap')).toBe('Gaps in your resume');
    expect(categoryLabel('x')).toBe('Other');
  });
});
