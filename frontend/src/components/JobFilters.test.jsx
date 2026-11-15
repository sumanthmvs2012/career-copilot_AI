import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import JobFilters from './JobFilters';
import { EMPTY_PLACE_FILTERS } from '../utils/jobFilters';
import { getJobFacets } from '../services/jobService';

vi.mock('../services/jobService', () => ({ getJobFacets: vi.fn() }));

const FACETS = {
  categories: [{ value: 'Software Engineering', count: 120 }],
  countries: [
    { value: 'United States', count: 90 },
    { value: 'Canada', count: 10 },
  ],
  states: [{ value: 'CA', count: 50 }],
  cities: [{ value: 'San Francisco', count: 30 }],
};

function Harness({ initial = EMPTY_PLACE_FILTERS, spy, ...props }) {
  const [value, setValue] = useState(initial);
  return (
    <JobFilters
      value={value}
      onChange={(next) => {
        spy?.(next);
        setValue(next);
      }}
      {...props}
    />
  );
}

describe('JobFilters', () => {
  beforeEach(() => {
    vi.mocked(getJobFacets).mockReset().mockResolvedValue(FACETS);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders four labelled selects with counts and disables State and City', async () => {
    render(<Harness />);
    expect(
      await screen.findByRole('option', { name: 'Software Engineering (120)' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'United States (90)' })).toBeInTheDocument();
    expect(screen.getByLabelText('Category')).toBeEnabled();
    expect(screen.getByLabelText('Country')).toBeEnabled();
    expect(screen.getByLabelText('State')).toBeDisabled();
    expect(screen.getByLabelText('City')).toBeDisabled();
  });

  it('enables State and City after choosing a country and refetches facets for it', async () => {
    const user = userEvent.setup();
    render(<Harness scope="pasted" />);
    await screen.findByRole('option', { name: 'Canada (10)' });
    await user.selectOptions(screen.getByLabelText('Country'), 'Canada');
    expect(screen.getByLabelText('State')).toBeEnabled();
    expect(screen.getByLabelText('City')).toBeEnabled();
    expect(getJobFacets).toHaveBeenLastCalledWith({
      scope: 'pasted',
      country: 'Canada',
      state: '',
    });
  });

  it('resets state and city when the country changes and city when the state changes', async () => {
    const user = userEvent.setup();
    const spy = vi.fn();
    render(
      <Harness
        spy={spy}
        initial={{
          category: 'Design',
          country: 'United States',
          state: 'CA',
          city: 'San Francisco',
        }}
      />,
    );
    await screen.findByRole('option', { name: 'Canada (10)' });

    await user.selectOptions(screen.getByLabelText('State'), '');
    expect(spy).toHaveBeenLastCalledWith({
      category: 'Design',
      country: 'United States',
      state: '',
      city: '',
    });

    await user.selectOptions(screen.getByLabelText('Country'), 'Canada');
    expect(spy).toHaveBeenLastCalledWith({
      category: 'Design',
      country: 'Canada',
      state: '',
      city: '',
    });
  });

  it('Clear filters resets everything and is disabled when nothing is set', async () => {
    const user = userEvent.setup();
    const spy = vi.fn();
    const { unmount } = render(<Harness spy={spy} />);
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeDisabled();
    unmount();

    render(<Harness spy={spy} initial={{ ...EMPTY_PLACE_FILTERS, category: 'Design' }} />);
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(spy).toHaveBeenCalledWith(EMPTY_PLACE_FILTERS);
  });

  it('can hide its own Clear filters button and supports the compact layout', () => {
    const { container } = render(<Harness showClear={false} compact />);
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
    expect(container.firstChild).toHaveClass('job-filters--compact');
  });

  it('keeps a selected value visible before facets load', () => {
    vi.mocked(getJobFacets).mockReturnValue(new Promise(() => {}));
    render(<Harness initial={{ ...EMPTY_PLACE_FILTERS, country: 'Canada' }} />);
    expect(screen.getByLabelText('Country')).toHaveValue('Canada');
  });

  it('shows a short note, logs only the status and retries when facets fail', async () => {
    const user = userEvent.setup();
    vi.mocked(getJobFacets)
      .mockRejectedValueOnce(Object.assign(new Error('secret'), { status: 500 }))
      .mockResolvedValue(FACETS);
    render(<Harness />);
    expect(await screen.findByText(/Filter options could not be loaded/)).toBeInTheDocument();
    expect(console.error).toHaveBeenCalledWith('Loading job filters failed', { status: 500 });
    expect(screen.getByLabelText('Category')).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('option', { name: 'Canada (10)' })).toBeInTheDocument();
    expect(screen.queryByText(/could not be loaded/)).not.toBeInTheDocument();
  });
});
