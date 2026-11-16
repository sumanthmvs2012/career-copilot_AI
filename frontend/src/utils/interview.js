export const CATEGORY_ORDER = ['technical', 'behavioral', 'role', 'company', 'resume_gap'];

export const CATEGORY_LABELS = {
  technical: 'Technical',
  behavioral: 'Behavioral',
  role: 'Role',
  company: 'Company',
  resume_gap: 'Gaps in your resume',
};

export function categoryLabel(category) {
  return CATEGORY_LABELS[category] ?? 'Other';
}

// Groups questions by category in a fixed order (unknown categories last as "Other"); each group
// keeps the server order. Returns [{category, label, questions}] without empty groups.
export function groupQuestions(questions) {
  const buckets = new Map(CATEGORY_ORDER.map((c) => [c, []]));
  for (const q of questions ?? []) {
    const key = buckets.has(q.category) ? q.category : 'other';
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key).push(q);
  }
  return [...buckets.entries()]
    .filter(([, list]) => list.length > 0)
    .map(([category, list]) => ({ category, label: categoryLabel(category), questions: list }));
}

// Plain numbered text, in the same order the groups are shown: "1. Question".
export function questionsToText(questions) {
  return groupQuestions(questions)
    .flatMap((g) => g.questions)
    .map((q, index) => `${index + 1}. ${q.question}`)
    .join('\n');
}
