import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SubmitApplicationPanel, { COVER_LETTER_MAX } from './SubmitApplicationPanel';
import { submitApplication, updateApplication } from '../services/applicationsService';

vi.mock('../services/applicationsService', () => ({
  submitApplication: vi.fn(),
  updateApplication: vi.fn(),
}));

const APP = { id: 7, jobTitle: 'Engineer', company: 'Acme', status: 'draft', coverLetter: 'Hi' };
const apiError = (status) => Object.assign(new Error(`status ${status}`), { status });

function setup(props = {}) {
  const handlers = { onClose: vi.fn(), onUpdated: vi.fn(), onUnauthorized: vi.fn() };
  const user = userEvent.setup();
  render(<SubmitApplicationPanel application={APP} {...handlers} {...props} />);
  return { user, ...handlers };
}

describe('SubmitApplicationPanel', () => {
  beforeEach(() => {
    vi.mocked(submitApplication).mockReset();
    vi.mocked(updateApplication).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows the existing cover letter, a counter and a 10000 character limit', () => {
    setup();
    const box = screen.getByLabelText('Cover letter');
    expect(box).toHaveValue('Hi');
    expect(box).toHaveAttribute('maxlength', String(COVER_LETTER_MAX));
    expect(screen.getByText('2 of 10000 characters')).toBeInTheDocument();
  });

  it('updates the counter as the user types', async () => {
    const { user } = setup();
    await user.type(screen.getByLabelText('Cover letter'), ' there');
    expect(screen.getByText('8 of 10000 characters')).toBeInTheDocument();
  });

  it('saves with updateApplication and announces success', async () => {
    vi.mocked(updateApplication).mockResolvedValue({ ...APP, coverLetter: 'Hi!' });
    const { user, onUpdated } = setup();
    await user.type(screen.getByLabelText('Cover letter'), '!');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(updateApplication).toHaveBeenCalledWith(7, { coverLetter: 'Hi!' });
    expect(await screen.findByText('Cover letter saved.')).toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalledWith({ ...APP, coverLetter: 'Hi!' });
  });

  it('asks for confirmation before submitting and can cancel', async () => {
    const { user } = setup();
    await user.click(screen.getByRole('button', { name: 'Submit application' }));
    expect(
      screen.getByText(/marks the application as applied in the app\. It does not send it/),
    ).toBeInTheDocument();
    expect(submitApplication).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('button', { name: 'Submit application' })).toBeInTheDocument();
  });

  it('submits the cover letter after confirming and closes', async () => {
    const done = { ...APP, status: 'applied' };
    vi.mocked(submitApplication).mockResolvedValue(done);
    const { user, onUpdated, onClose } = setup();
    await user.click(screen.getByRole('button', { name: 'Submit application' }));
    await user.click(screen.getByRole('button', { name: 'Yes, mark as applied' }));

    expect(submitApplication).toHaveBeenCalledWith(7, 'Hi');
    await vi.waitFor(() => expect(onClose).toHaveBeenCalledWith({ submitted: true }));
    expect(onUpdated).toHaveBeenCalledWith(done);
  });

  it('shows the 409 message', async () => {
    vi.mocked(submitApplication).mockRejectedValue(apiError(409));
    const { user } = setup();
    await user.click(screen.getByRole('button', { name: 'Submit application' }));
    await user.click(screen.getByRole('button', { name: 'Yes, mark as applied' }));

    expect(await screen.findByText('Only a draft can be submitted.')).toBeInTheDocument();
    expect(console.error).toHaveBeenCalledWith('Application update failed', { status: 409 });
  });

  it('shows a generic error for other failures and keeps the text', async () => {
    vi.mocked(updateApplication).mockRejectedValue(apiError(500));
    const { user } = setup();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/could not save the cover letter/)).toBeInTheDocument();
    expect(screen.getByLabelText('Cover letter')).toHaveValue('Hi');
  });

  it('hands a 401 to onUnauthorized', async () => {
    vi.mocked(updateApplication).mockRejectedValue(apiError(401));
    const { user, onUnauthorized } = setup();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(onUnauthorized).toHaveBeenCalled();
  });

  it('closes without submitting', async () => {
    const { user, onClose } = setup();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledWith({ submitted: false });
  });
});
