import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import ResumeSuggestions from '../components/ResumeSuggestions';
import { deleteResume, getResume, getSuggestions } from '../services/resumeService';
import { tailoredLabel } from '../utils/resumes';

function downloadText(fileName, text) {
  const name = /\.txt$/i.test(fileName) ? fileName : `${fileName}.txt`;
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function TailoredResumePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loadState, setLoadState] = useState('loading'); // loading | ready | not-found | error
  const [resume, setResume] = useState(null);
  const [suggestions, setSuggestions] = useState([]);
  const [suggestionsState, setSuggestionsState] = useState('ready'); // loading | ready | error
  const [copyState, setCopyState] = useState('idle'); // idle | copied | failed
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);

  // GET /resumes/{id} has no suggestions, so a tailored copy asks for them separately.
  const loadSuggestions = useCallback(
    async (target) => {
      setSuggestionsState('loading');
      try {
        const result = await getSuggestions(target.id, target.jobId);
        setSuggestions(result?.suggestions ?? []);
        setSuggestionsState('ready');
      } catch (error) {
        if (error?.status === 401) return navigate('/login', { replace: true });
        console.error('Loading suggestions failed', { status: error?.status ?? 'unknown' });
        setSuggestionsState('error');
      }
    },
    [navigate],
  );

  const load = useCallback(async () => {
    setLoadState('loading');
    try {
      const loaded = await getResume(id);
      setResume(loaded);
      setLoadState('ready');
      if (Array.isArray(loaded.suggestions)) {
        setSuggestions(loaded.suggestions);
        setSuggestionsState('ready');
      } else if (loaded.jobId != null) {
        loadSuggestions(loaded);
      } else {
        setSuggestions([]);
        setSuggestionsState('ready');
      }
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      console.error('Loading tailored resume failed', { status: error?.status ?? 'unknown' });
      setLoadState(error?.status === 404 ? 'not-found' : 'error');
    }
  }, [id, navigate, loadSuggestions]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(resume.parsedText ?? '');
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  async function handleDelete() {
    if (deleting) return;
    setDeleting(true);
    setDeleteFailed(false);
    try {
      await deleteResume(resume.id);
      navigate('/resume');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      if (error?.status === 404) return navigate('/resume');
      console.error('Deleting tailored resume failed', { status: error?.status ?? 'unknown' });
      setDeleteFailed(true);
      setConfirming(false);
      setDeleting(false);
    }
  }

  return (
    <main className="page page--narrow">
      <header className="page__header">
        <h1>Tailored resume</h1>
        <Link to="/resume">Back to your resumes</Link>
      </header>

      <section className="card">
        {loadState === 'loading' && <p role="status">Loading your tailored resume…</p>}
        {loadState === 'not-found' && (
          <Alert variant="error">
            We could not find that resume. <Link to="/resume">Go to your resumes</Link>
          </Alert>
        )}
        {loadState === 'error' && (
          <>
            <Alert variant="error">We could not load this resume. Please try again.</Alert>
            <Button variant="secondary" onClick={load}>
              Try again
            </Button>
          </>
        )}
        {loadState === 'ready' && resume && (
          <>
            <h2>{resume.fileName}</h2>
            {resume.jobTitle && <p className="muted">Tailored for {tailoredLabel(resume)}</p>}

            <div className="resume-actions">
              <Button variant="secondary" onClick={handleCopy}>
                Copy text
              </Button>
              <Button
                variant="secondary"
                onClick={() => downloadText(resume.fileName, resume.parsedText ?? '')}
              >
                Download .txt
              </Button>
              {copyState === 'copied' && <span role="status">Copied</span>}
            </div>
            {copyState === 'failed' && (
              <Alert variant="warning">
                We could not copy automatically. Select the text below and copy it yourself.
              </Alert>
            )}

            <pre className="resume-text" tabIndex={0} aria-label="Tailored resume text">
              {resume.parsedText}
            </pre>

            <h2>Suggestions</h2>
            {suggestionsState === 'loading' && <p role="status">Loading suggestions…</p>}
            {suggestionsState === 'error' && (
              <>
                <Alert variant="error">We could not load suggestions. Please try again.</Alert>
                <Button variant="secondary" onClick={() => loadSuggestions(resume)}>
                  Try again
                </Button>
              </>
            )}
            {suggestionsState === 'ready' && (
              <ResumeSuggestions suggestions={suggestions} checklist />
            )}

            {deleteFailed && (
              <Alert variant="error">We could not delete this copy. Please try again.</Alert>
            )}
            <div className="resume-list__actions">
              {confirming ? (
                <>
                  <span>Delete {resume.fileName}?</span>
                  <Button
                    variant="secondary"
                    onClick={handleDelete}
                    loading={deleting}
                    loadingText="Deleting…"
                  >
                    Delete
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => setConfirming(false)}
                    disabled={deleting}
                  >
                    Cancel
                  </Button>
                </>
              ) : (
                <Button
                  variant="secondary"
                  onClick={() => setConfirming(true)}
                  aria-label={`Delete ${resume.fileName}`}
                >
                  Delete
                </Button>
              )}
            </div>
          </>
        )}
      </section>
    </main>
  );
}
