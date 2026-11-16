import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import StatusSelect from './StatusSelect';

const STATUSES = ['draft', 'applied', 'interviewing', 'offer', 'rejected', 'withdrawn'];

function Harness() {
  const [value, setValue] = useState('draft');
  return (
    <StatusSelect
      label="Change status for Engineer"
      statuses={STATUSES}
      value={value}
      onChange={(event) => setValue(event.target.value)}
    />
  );
}

describe('StatusSelect', () => {
  it('has an accessible name, visible status text and a colour that follows the choice', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const select = screen.getByLabelText('Change status for Engineer');
    expect(select.closest('.status-select')).toHaveClass('status-select--neutral');
    expect(screen.getByRole('option', { name: 'Draft' })).toHaveClass('status-option--neutral');
    expect(screen.getByRole('option', { name: 'Offer' })).toHaveClass('status-option--success');

    await user.selectOptions(select, 'rejected');
    expect(select.closest('.status-select')).toHaveClass('status-select--danger');
    await user.selectOptions(select, 'withdrawn');
    expect(select.closest('.status-select')).toHaveClass('status-select--warning');
    expect(select).toHaveDisplayValue('Withdrawn');
  });
});
