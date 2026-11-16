import { describe, expect, it } from 'vitest';
import { formatLocations, summarizeLocations } from './jobFilters';

const place = (city, state, country) => ({ city, state, country });

describe('summarizeLocations', () => {
  it('shows the first two places without the country, then +N more', () => {
    const job = {
      locations: [
        place('San Francisco', 'CA', 'United States'),
        place('Seattle', 'WA', 'United States'),
        place('Chicago', 'IL', 'United States'),
        place('Chicago', 'IL', 'United States'),
        place('London', null, 'United Kingdom'),
      ],
    };
    expect(summarizeLocations(job)).toBe('San Francisco, CA · Seattle, WA · +2 more');
    expect(formatLocations(job)).toContain('United States');
  });

  it('uses the country when a row has no city or state, and the raw text without rows', () => {
    expect(summarizeLocations({ locations: [place(null, null, 'Canada')] })).toBe('Canada');
    expect(summarizeLocations({ locations: [], location: 'Remote' })).toBe('Remote');
  });
});
