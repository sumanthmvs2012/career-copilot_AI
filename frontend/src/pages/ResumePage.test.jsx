import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ResumePage from './ResumePage';
import { deleteResume, listResumes, uploadResume } from '../services/resumeService';

vi.mock('../services/resumeService', async () => {
  const actual = await vi.importActual('../services/resumeService');
  return { ...actual, listResumes: vi.fn(), uploadResume: vi.fn(), deleteResume: vi.fn() };
});

function renderResume() {
  const user = userEvent.setup({ applyAccept: false });
  render(
    <MemoryRouter initialEntries={['/resume']}>
      <Routes>
        <Route path="/resume" element={<ResumePage />} />
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/profile" element={<p>Profile page</p>} />
        <Route path="/dashboard" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function apiError(status, data = null) {
  return Object.assign(new Error(`status ${status}`), { status, data });
}

const RESUME = {
  id: 1,
  fileName: 'jane-cv.pdf',
  contentType: 'application/pdf',
  isBase: true,
  baseResumeId: null,
  textLength: 900,
  createdAt: '2026-11-10T10:00:00Z',
};

function pdf(name = 'new-cv.pdf') {
  return new File(['pdf'], name, { type: 'application/pdf' });
}

async function chooseAndSubmit(user, file = pdf()) {
  await user.upload(await screen.findByLabelText(/resume file/i), file);
  await user.click(screen.getByRole('button', { name: /upload resume/i }));
}

describe('ResumePage', () => {
  beforeEach(() => {
    vi.mocked(listResumes).mockReset().mockResolvedValue([]);
    vi.mocked(uploadResume).mockReset();
    vi.mocked(deleteResume).mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state', () => {
    vi.mocked(listResumes).mockReturnValue(new Promise(() => {}));
    renderResume();
    expect(screen.getByText('Loading your resume…')).toBeInTheDocument();
  });

  it('shows the empty state with an Upload button', async () => {
    renderResume();
    expect(await screen.findByText('You have not uploaded a resume yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload resume' })).toBeInTheDocument();
    expect(screen.getByLabelText(/resume file/i)).toHaveAttribute('accept', '.pdf,.docx');
    expect(screen.getByRole('link', { name: 'Back to dashboard' })).toBeInTheDocument();
  });

  it('shows the resume with a Base badge, the count and an enabled Upload button', async () => {
    vi.mocked(listResumes).mockResolvedValue([RESUME]);
    renderResume();
    expect(await screen.findByText('jane-cv.pdf')).toBeInTheDocument();
    expect(screen.getByText('Text extracted')).toBeInTheDocument();
    expect(screen.getByText('Base')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /1 of 2/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload resume' })).toBeEnabled();
  });

  describe('with two resumes', () => {
    const OLDER = { ...RESUME, id: 2, fileName: 'old-cv.pdf', isBase: false };

    it('disables upload and shows the limit hint', async () => {
      vi.mocked(listResumes).mockResolvedValue([RESUME, OLDER]);
      renderResume();
      expect(await screen.findByText('old-cv.pdf')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /2 of 2/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Upload resume' })).toBeDisabled();
      expect(screen.getByLabelText(/resume file/i)).toBeDisabled();
      expect(
        screen.getByText('You have 2 resumes. Delete one to upload another.'),
      ).toBeInTheDocument();
    });

    it('asks for confirmation, deletes, and shows the promoted base', async () => {
      vi.mocked(listResumes)
        .mockResolvedValueOnce([RESUME, OLDER])
        .mockResolvedValue([{ ...OLDER, isBase: true }]);
      vi.mocked(deleteResume).mockResolvedValue(null);
      const user = renderResume();
      await screen.findByText('old-cv.pdf');

      await user.click(screen.getByRole('button', { name: 'Delete jane-cv.pdf' }));
      expect(screen.getByText('Delete jane-cv.pdf?')).toBeInTheDocument();
      expect(deleteResume).not.toHaveBeenCalled();
      await user.click(screen.getByRole('button', { name: 'Delete' }));

      await waitFor(() => expect(screen.queryByText('jane-cv.pdf')).not.toBeInTheDocument());
      expect(deleteResume).toHaveBeenCalledWith(1);
      expect(screen.getByText('Base')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Upload resume' })).toBeEnabled();
    });

    it('cancels a delete without calling the API', async () => {
      vi.mocked(listResumes).mockResolvedValue([RESUME, OLDER]);
      const user = renderResume();
      await screen.findByText('old-cv.pdf');
      await user.click(screen.getByRole('button', { name: 'Delete old-cv.pdf' }));
      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(screen.queryByText('Delete old-cv.pdf?')).not.toBeInTheDocument();
      expect(deleteResume).not.toHaveBeenCalled();
    });

    it('shows an error when delete fails and keeps the resume', async () => {
      vi.mocked(listResumes).mockResolvedValue([RESUME, OLDER]);
      vi.mocked(deleteResume).mockRejectedValue(apiError(500));
      const user = renderResume();
      await screen.findByText('old-cv.pdf');
      await user.click(screen.getByRole('button', { name: 'Delete old-cv.pdf' }));
      await user.click(screen.getByRole('button', { name: 'Delete' }));
      expect(await screen.findByRole('alert')).toHaveTextContent('We could not delete old-cv.pdf');
      expect(screen.getByText('old-cv.pdf')).toBeInTheDocument();
    });

    it('redirects to /login when delete returns 401', async () => {
      vi.mocked(listResumes).mockResolvedValue([RESUME, OLDER]);
      vi.mocked(deleteResume).mockRejectedValue(apiError(401));
      const user = renderResume();
      await screen.findByText('old-cv.pdf');
      await user.click(screen.getByRole('button', { name: 'Delete old-cv.pdf' }));
      await user.click(screen.getByRole('button', { name: 'Delete' }));
      expect(await screen.findByText('Login page')).toBeInTheDocument();
    });
  });

  it('shows the API message on a 409 and refreshes the list', async () => {
    const OLDER = { ...RESUME, id: 2, fileName: 'old-cv.pdf', isBase: false };
    vi.mocked(listResumes).mockResolvedValueOnce([]).mockResolvedValue([RESUME, OLDER]);
    vi.mocked(uploadResume).mockRejectedValue(
      apiError(409, {
        status: 409,
        message: 'You already have 2 resumes. Delete one to upload another.',
      }),
    );
    const user = renderResume();
    await chooseAndSubmit(user);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You already have 2 resumes. Delete one to upload another.',
    );
    expect(await screen.findByText('old-cv.pdf')).toBeInTheDocument();
  });

  it('shows an error and retries', async () => {
    vi.mocked(listResumes).mockRejectedValueOnce(apiError(500)).mockResolvedValue([RESUME]);
    const user = renderResume();
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your resume');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('jane-cv.pdf')).toBeInTheDocument();
  });

  it('redirects to /login on 401', async () => {
    vi.mocked(listResumes).mockRejectedValue(apiError(401));
    renderResume();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('uploads a file, disables the button meanwhile, then shows success', async () => {
    let resolveUpload;
    vi.mocked(uploadResume).mockReturnValue(new Promise((r) => (resolveUpload = r)));
    const user = renderResume();
    await chooseAndSubmit(user);

    expect(screen.getByRole('button', { name: 'Uploading…' })).toBeDisabled();
    resolveUpload({ ...RESUME, fileName: 'new-cv.pdf' });

    expect(await screen.findByText('Resume uploaded.')).toBeInTheDocument();
    expect(screen.getByText('new-cv.pdf')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload resume' })).toBeEnabled();
    expect(uploadResume).toHaveBeenCalledTimes(1);
  });

  it('asks for a file when none is chosen', async () => {
    const user = renderResume();
    await user.click(await screen.findByRole('button', { name: 'Upload resume' }));
    expect(screen.getByText('Choose a PDF or DOCX file.')).toBeInTheDocument();
    expect(uploadResume).not.toHaveBeenCalled();
  });

  it('rejects a wrong file type on the client', async () => {
    const user = renderResume();
    await chooseAndSubmit(user, new File(['x'], 'notes.txt', { type: 'text/plain' }));
    expect(screen.getByText('Upload a PDF or DOCX file.')).toBeInTheDocument();
    expect(uploadResume).not.toHaveBeenCalled();
  });

  it('shows the API message on a 400', async () => {
    vi.mocked(uploadResume).mockRejectedValue(
      apiError(400, { status: 400, message: 'We could not read text from that file.' }),
    );
    const user = renderResume();
    await chooseAndSubmit(user);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not read text from that file.',
    );
  });

  it('links to the profile on a 404', async () => {
    vi.mocked(uploadResume).mockRejectedValue(apiError(404));
    const user = renderResume();
    await chooseAndSubmit(user);
    expect(await screen.findByRole('alert')).toHaveTextContent('Create your profile first.');
    expect(screen.getByRole('link', { name: 'Go to profile' })).toHaveAttribute('href', '/profile');
  });

  it('shows a generic message on a server error', async () => {
    vi.mocked(uploadResume).mockRejectedValue(apiError(500));
    const user = renderResume();
    await chooseAndSubmit(user);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not upload your resume. Please try again.',
    );
  });
});
