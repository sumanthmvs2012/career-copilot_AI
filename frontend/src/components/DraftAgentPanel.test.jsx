import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { runAgent } from '../services/agentService';
import DraftAgentPanel from './DraftAgentPanel';

vi.mock('../services/agentService', () => ({ runAgent: vi.fn() }));

function apiError(status) {
  return Object.assign(new Error('x'), { status });
}

function setup() {
  const onDone = vi.fn();
  const onUnauthorized = vi.fn();
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <DraftAgentPanel onDone={onDone} onUnauthorized={onUnauthorized} />
    </MemoryRouter>,
  );
  return { user, onDone, onUnauthorized };
}

const run = (user) => user.click(screen.getByRole('button', { name: 'Find new matches' }));

describe('DraftAgentPanel', () => {
  beforeEach(() => {
    vi.mocked(runAgent).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('explains what it does', () => {
    setup();
    expect(screen.getByText(/It never submits anything\./)).toBeInTheDocument();
  });

  it('shows loading, then the counts, and refreshes the list', async () => {
    let resolve;
    vi.mocked(runAgent).mockReturnValue(new Promise((r) => (resolve = r)));
    const { user, onDone } = setup();
    await run(user);
    expect(screen.getByRole('button', { name: /Checking/ })).toBeDisabled();
    resolve({ considered: 7, draftsCreated: 2 });
    expect(await screen.findByText('Checked 7 new jobs, created 2 drafts')).toBeInTheDocument();
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('uses singular words for one', async () => {
    vi.mocked(runAgent).mockResolvedValue({ considered: 1, draftsCreated: 1 });
    const { user } = setup();
    await run(user);
    expect(await screen.findByText('Checked 1 new job, created 1 draft')).toBeInTheDocument();
  });

  it('409 asks for a resume with a link', async () => {
    vi.mocked(runAgent).mockRejectedValue(apiError(409));
    const { user, onDone } = setup();
    await run(user);
    expect(await screen.findByText(/Upload a resume first\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your resumes' })).toHaveAttribute(
      'href',
      '/resume',
    );
    expect(onDone).not.toHaveBeenCalled();
  });

  it('404 asks for a profile with a link', async () => {
    vi.mocked(runAgent).mockRejectedValue(apiError(404));
    const { user } = setup();
    await run(user);
    expect(await screen.findByText(/Create your profile first\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
      'href',
      '/profile',
    );
  });

  it('other errors show a generic message', async () => {
    vi.mocked(runAgent).mockRejectedValue(apiError(500));
    const { user } = setup();
    await run(user);
    expect(await screen.findByText(/could not check for new matches/i)).toBeInTheDocument();
  });

  it('401 hands over to the unauthorized handler', async () => {
    vi.mocked(runAgent).mockRejectedValue(apiError(401));
    const { user, onUnauthorized } = setup();
    await run(user);
    expect(onUnauthorized).toHaveBeenCalled();
  });
});
