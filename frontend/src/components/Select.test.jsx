import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Select from './Select';

const OPTIONS = [
  { value: '', label: 'Any' },
  { value: 'remote', label: 'Remote' },
  { value: 'hybrid', label: 'Hybrid' },
];

describe('Select', () => {
  it('links the label to the select and renders every option', () => {
    render(<Select label="Remote type" options={OPTIONS} defaultValue="" />);
    const select = screen.getByLabelText('Remote type');
    expect(select.tagName).toBe('SELECT');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Any',
      'Remote',
      'Hybrid',
    ]);
  });

  it('calls onChange with the chosen value', async () => {
    const onChange = vi.fn((event) => event.target.value);
    render(<Select label="Remote type" options={OPTIONS} value="" onChange={onChange} />);
    await userEvent.setup().selectOptions(screen.getByLabelText('Remote type'), 'hybrid');
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveReturnedWith('hybrid');
  });

  it('shows an error and marks the select invalid', () => {
    render(<Select label="Source" options={OPTIONS} error="Pick one" defaultValue="" />);
    const select = screen.getByLabelText('Source');
    expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(select).toHaveAccessibleDescription('Pick one');
  });
});
