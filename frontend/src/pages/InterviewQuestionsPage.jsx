import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Alert from '../components/Alert';
import Button from '../components/Button';
import { getInterviewQuestions, regenerateInterviewQuestions } from '../services/interviewService';
import { listResumes } from '../services/resumeService';
import { groupQuestions, questionsToText } from '../utils/interview';
import { findBaseResume } from '../utils/resumes';

export default function InterviewQuestionsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loadState, setLoadState] = useState('loading'); // loading | ready | not-found | error
  const [data, setData] = useState(null);
  const [hasResume, setHasResume] = useState(true);
  const [regenerating, setRegenerating] = useState(false);
  const [regenerateFailed, setRegenerateFailed] = useState(false);
  const [copyState, setCopyState] = useState('idle'); // idle | copied | failed

  const load = useCallback(async () => {
    setLoadState('loading');
    try {
      setData(await getInterviewQuestions(id));
      setLoadState('ready');
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      // Status only: never log questions or tokens.
      console.error('Loading interview questions failed', { status: error?.status ?? 'unknown' });
      setLoadState(error?.status === 404 ? 'not-found' : 'error');
    }
  }, [id, navigate]);

  useEffect(() => {
    load();
  }, [load]);

  // The resume only changes the hint; a failure here must not block the questions.
  useEffect(() => {
    let active = true;
    listResumes()
      .then((rows) => {
        if (active) setHasResume(findBaseResume(rows) !== null);
      })
      .catch((error) => {
        console.error('Checking resume failed', { status: error?.status ?? 'unknown' });
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleRegenerate() {
    setRegenerating(true);
    setRegenerateFailed(false);
    setCopyState('idle');
    try {
      setData(await regenerateInterviewQuestions(id));
    } catch (error) {
      if (error?.status === 401) return navigate('/login', { replace: true });
      console.error('Regenerating questions failed', { status: error?.status ?? 'unknown' });
      setRegenerateFailed(true);
    } finally {
      setRegenerating(false);
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(questionsToText(data.questions));
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  const questions = data?.questions ?? [];
  const groups = groupQuestions(questions);
  let counter = 0;

  return (
    <main className="page page--narrow">
      <header className="page__header">
        <h1>Interview questions</h1>
        <Link to="/dashboard">Back to dashboard</Link>
      </header>

      <section className="card" aria-labelledby="interview-heading">
        {loadState === 'loading' && <p role="status">Loading interview questions…</p>}
        {loadState === 'not-found' && (
          <Alert variant="error">
            We could not find that job. <Link to="/dashboard">Go to your dashboard</Link>
          </Alert>
        )}
        {loadState === 'error' && (
          <>
            <Alert variant="error">We could not load the questions. Please try again.</Alert>
            <Button variant="secondary" onClick={load}>
              Try again
            </Button>
          </>
        )}
        {loadState === 'ready' && data && (
          <>
            <h2 id="interview-heading">
              {data.jobTitle || 'Untitled job'}
              {data.company ? ` at ${data.company}` : ''}
            </h2>
            {!hasResume && (
              <Alert variant="warning">
                Upload a resume for questions that fit your skills. These questions come from the
                job only. <Link to="/resume">Go to your resumes</Link>
              </Alert>
            )}

            <div className="resume-actions">
              <Button variant="secondary" onClick={handleCopy} disabled={questions.length === 0}>
                Copy all
              </Button>
              <Button
                variant="secondary"
                onClick={handleRegenerate}
                loading={regenerating}
                loadingText="Regenerating…"
              >
                Regenerate
              </Button>
              <span role="status" aria-live="polite">
                {copyState === 'copied' ? 'Copied' : ''}
              </span>
            </div>
            {copyState === 'failed' && (
              <Alert variant="warning">
                We could not copy automatically. Select the questions below and copy them yourself.
              </Alert>
            )}
            {regenerateFailed && (
              <Alert variant="error">
                We could not regenerate the questions. Please try again.
              </Alert>
            )}

            {questions.length === 0 && (
              <p>No questions yet. Select Regenerate to create a new list.</p>
            )}
            {groups.map((group) => {
              const start = counter + 1;
              counter += group.questions.length;
              return (
                <section key={group.category} className="interview-group">
                  <h3>{group.label}</h3>
                  <ol start={start} className="interview-list">
                    {group.questions.map((q) => (
                      <li key={q.id}>{q.question}</li>
                    ))}
                  </ol>
                </section>
              );
            })}
          </>
        )}
      </section>
    </main>
  );
}
