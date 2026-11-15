import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import {
  MAX_RESUMES,
  deleteResume,
  listResumes,
  uploadResume,
  validateResumeFile,
} from '../services/resumeService';

const LIMIT_NOTE = 'You have 2 resumes. Delete one to upload another.';

function ResumeItem({ resume, confirming, deleting, onAskDelete, onCancel, onConfirm }) {
  return (
    <li className="resume-list__item">
      <p className="resume-list__name">{resume.fileName}</p>
      <p className="resume-list__meta">
        <span>Uploaded {new Date(resume.createdAt).toLocaleDateString()}</span>
        {resume.isBase && <span className="badge">Base</span>}
        {resume.textLength > 0 && <span>Text extracted</span>}
      </p>
      {resume.skills?.length > 0 && (
        <div className="resume-skills">
          <p className="resume-skills__title muted">Skills found</p>
          <ul className="chip-list" aria-label={`Skills found in ${resume.fileName}`}>
            {resume.skills.map((skill) => (
              <li key={skill} className="skill-chip">
                {skill}
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="resume-list__actions">
        {confirming ? (
          <>
            <span>Delete {resume.fileName}?</span>
            <Button
              variant="secondary"
              onClick={() => onConfirm(resume)}
              loading={deleting}
              loadingText="Deleting…"
            >
              Delete
            </Button>
            <Button variant="secondary" onClick={onCancel} disabled={deleting}>
              Cancel
            </Button>
          </>
        ) : (
          <Button
            variant="secondary"
            onClick={() => onAskDelete(resume.id)}
            disabled={deleting}
            aria-label={`Delete ${resume.fileName}`}
          >
            Delete
          </Button>
        )}
      </div>
    </li>
  );
}

export default function ResumePage() {
  const navigate = useNavigate();
  const [loadState, setLoadState] = useState('loading');
  const [resumes, setResumes] = useState([]);
  const [confirmId, setConfirmId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [deleteError, setDeleteError] = useState(null);
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState('');
  const [uploadError, setUploadError] = useState(null);
  const [uploaded, setUploaded] = useState(false);
  const [uploading, setUploading] = useState(false);
  const uploadingRef = useRef(false);
  const inputRef = useRef(null);

  const load = useCallback(async () => {
    setLoadState('loading');
    try {
      const rows = await listResumes();
      setResumes(rows);
      setLoadState('ready');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      console.error('Loading resume failed', { status: error?.status ?? 'unknown' });
      setLoadState('error');
    }
  }, [navigate]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleDelete(target) {
    if (deletingId !== null) return;
    setDeleteError(null);
    setDeletingId(target.id);
    try {
      await deleteResume(target.id);
      setResumes((current) => current.filter((r) => r.id !== target.id));
      setConfirmId(null);
      setUploaded(false);
      setUploadError(null);
      // Refresh so the promoted base shows; a failed refresh keeps the local list.
      try {
        setResumes(await listResumes());
      } catch (refreshError) {
        if (refreshError?.status === 401) return navigate('/login', { replace: true });
      }
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      console.error('Deleting resume failed', { status: error?.status ?? 'unknown' });
      setConfirmId(null);
      setDeleteError({ resume: target, status: error?.status ?? 0 });
    } finally {
      setDeletingId(null);
    }
  }

  async function retryDelete() {
    const { resume: target, status } = deleteError;
    if (status === 404) {
      // Already gone or not ours: just refresh the list.
      setDeleteError(null);
      return load();
    }
    return handleDelete(target);
  }

  function handleFileChange(event) {
    const chosen = event.target.files?.[0] ?? null;
    setFile(chosen);
    setUploaded(false);
    setUploadError(null);
    setFileError(chosen ? validateResumeFile(chosen) : '');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (uploadingRef.current) return;
    setUploaded(false);
    setUploadError(null);
    if (!file) return setFileError('Choose a PDF or DOCX file.');
    const message = validateResumeFile(file);
    setFileError(message);
    if (message) return;

    uploadingRef.current = true;
    setUploading(true);
    try {
      const created = await uploadResume(file);
      // The newest upload is the base; keep at most MAX_RESUMES.
      setResumes((current) =>
        [created, ...current.map((r) => ({ ...r, isBase: false }))].slice(0, MAX_RESUMES),
      );
      setUploaded(true);
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      // Status only: never log the file or its contents.
      console.error('Uploading resume failed', { status: error?.status ?? 'unknown' });
      if (error?.status === 404) {
        setUploadError({ kind: 'no-profile' });
      } else if (error?.status === 409) {
        setUploadError({ kind: 'message', text: error.data?.message || LIMIT_NOTE });
        // Another tab may have filled the slots; show the real list.
        try {
          setResumes(await listResumes());
        } catch {
          // Keep what we have; the message above is enough.
        }
      } else if (error?.status === 400) {
        setUploadError({
          kind: 'message',
          text: error.data?.message || 'That file could not be used. Try another one.',
        });
      } else {
        setUploadError({
          kind: 'message',
          text: 'We could not upload your resume. Please try again.',
        });
      }
    } finally {
      uploadingRef.current = false;
      setUploading(false);
    }
  }

  const atLimit = resumes.length >= MAX_RESUMES;

  return (
    <main className="page page--narrow">
      <header className="page__header">
        <h1>Your resumes</h1>
        <Link to="/dashboard">Back to dashboard</Link>
      </header>

      <section className="card">
        {loadState === 'loading' && <p role="status">Loading your resume…</p>}
        {loadState === 'error' && (
          <>
            <Alert variant="error">We could not load your resume. Please try again.</Alert>
            <Button variant="secondary" onClick={load}>
              Try again
            </Button>
          </>
        )}
        {loadState === 'ready' && (
          <>
            <h2 id="resumes-heading">
              Your resumes ({resumes.length} of {MAX_RESUMES})
            </h2>
            {deleteError && (
              <>
                <Alert variant="error">
                  {deleteError.status === 404
                    ? 'That resume could not be found.'
                    : `We could not delete ${deleteError.resume.fileName}. Please try again.`}
                </Alert>
                <Button variant="secondary" onClick={retryDelete}>
                  Try again
                </Button>
              </>
            )}
            {resumes.length > 0 ? (
              <ul className="resume-list" aria-labelledby="resumes-heading">
                {resumes.map((resume) => (
                  <ResumeItem
                    key={resume.id}
                    resume={resume}
                    confirming={confirmId === resume.id}
                    deleting={deletingId === resume.id}
                    onAskDelete={(id) => {
                      setDeleteError(null);
                      setConfirmId(id);
                    }}
                    onCancel={() => setConfirmId(null)}
                    onConfirm={handleDelete}
                  />
                ))}
              </ul>
            ) : (
              <p>You have not uploaded a resume yet.</p>
            )}

            <form onSubmit={handleSubmit} noValidate>
              {uploaded && <Alert variant="success">Resume uploaded.</Alert>}
              {uploadError?.kind === 'no-profile' && (
                <Alert variant="error">
                  Create your profile first. <Link to="/profile">Go to profile</Link>
                </Alert>
              )}
              {uploadError?.kind === 'message' && <Alert variant="error">{uploadError.text}</Alert>}

              <div className="field">
                <label className="field__label" htmlFor="resumeFile">
                  Resume file (PDF or DOCX, up to 5 MB)
                </label>
                <input
                  ref={inputRef}
                  id="resumeFile"
                  className="field__input"
                  type="file"
                  accept=".pdf,.docx"
                  onChange={handleFileChange}
                  disabled={uploading || atLimit}
                  aria-invalid={fileError ? 'true' : undefined}
                  aria-describedby={fileError ? 'resumeFile-error' : undefined}
                />
                {fileError && (
                  <p id="resumeFile-error" className="field__error">
                    {fileError}
                  </p>
                )}
              </div>

              {atLimit && <p className="field__hint">{LIMIT_NOTE}</p>}

              <Button type="submit" loading={uploading} loadingText="Uploading…" disabled={atLimit}>
                Upload resume
              </Button>
            </form>
          </>
        )}
      </section>
    </main>
  );
}
