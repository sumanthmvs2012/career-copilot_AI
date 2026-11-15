import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TrackJobButton from './TrackJobButton';
import { createApplication } from '../services/applicationsService';

vi.mock('../services/applicationsService', () => ({ createApplication: vi.fn() }));

const apiError = (status, message) =>
  Object.assign(new Error(`status ${status}`), { status, data: { status, message } });

describe('TrackJobButton', () => {
  beforeEach(() => {
    vi.mocked(createApplication).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('creates a draft, shows Tracked and calls onTracked', async () => {
    vi.mocked(createApplication).mockResolvedValue({ id: 1 });
    const onTracked = vi.fn();
    const user = userEvent.setup();
    render(<TrackJobButton jobId={9} jobTitle="Dev" onTracked={onTracked} />);

    await user.click(screen.getByRole('button', { name: /Track this job/ }));

    expect(createApplication).toHaveBeenCalledWith({ jobId: 9, status: 'draft' });
    expect(await screen.findByText('Tracked')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(onTracked).toHaveBeenCalledTimes(1);
  });

  it('shows the 409 message when the job is already tracked', async () => {
    vi.mocked(createApplication).mockRejectedValue(apiError(409, 'You already track this job.'));
    const user = userEvent.setup();
    render(<TrackJobButton jobId={9} />);

    await user.click(screen.getByRole('button', { name: /Track this job/ }));

    expect(await screen.findByText('You already track this job.')).toBeInTheDocument();
  });

  it('shows a short error and allows a retry on other failures', async () => {
    vi.mocked(createApplication).mockRejectedValueOnce(apiError(500, 'boom'));
    vi.mocked(createApplication).mockResolvedValueOnce({ id: 1 });
    const user = userEvent.setup();
    render(<TrackJobButton jobId={9} />);

    await user.click(screen.getByRole('button', { name: /Track this job/ }));
    expect(
      await screen.findByText('We could not track this job. Please try again.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Track this job/ }));
    expect(await screen.findByText('Tracked')).toBeInTheDocument();
  });
});
