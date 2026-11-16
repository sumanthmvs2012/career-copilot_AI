import { useState } from 'react';
import { Link } from 'react-router-dom';
import Alert from './Alert';
import Button from './Button';
import { runAgent } from '../services/agentService';

function plural(count, word) {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}

// "Find new matches": asks the draft agent to look at new jobs once. Only writes drafts.
export default function DraftAgentPanel({ onDone, onUnauthorized }) {
  const [state, setState] = useState('idle'); // idle | loading | success | no-resume | no-profile | error
  const [result, setResult] = useState(null);

  async function handleRun() {
    setState('loading');
    try {
      const data = await runAgent();
      setResult(data);
      setState('success');
      if (onDone) onDone();
    } catch (error) {
      if (error?.status === 401) return onUnauthorized?.();
      // Status only; the response is never logged.
      console.error('Draft agent run failed', { status: error?.status ?? 'unknown' });
      if (error?.status === 409) setState('no-resume');
      else if (error?.status === 404) setState('no-profile');
      else setState('error');
    }
  }

  const considered = Number(result?.considered) || 0;
  const created = Number(result?.draftsCreated) || 0;

  return (
    <div className="agent-panel">
      <div className="agent-panel__row">
        <p className="muted agent-panel__text">
          Looks at new jobs and saves a draft for every job that matches your resume at 70 or more.
          It never submits anything.
        </p>
        <Button
          variant="secondary"
          onClick={handleRun}
          loading={state === 'loading'}
          loadingText="Checking…"
        >
          Find new matches
        </Button>
      </div>
      <div aria-live="polite">
        {state === 'success' && (
          <Alert variant="success">
            Checked {plural(considered, 'new job')}, created {plural(created, 'draft')}
          </Alert>
        )}
        {state === 'no-resume' && (
          <Alert variant="warning">
            Upload a resume first. <Link to="/resume">Go to your resumes</Link>
          </Alert>
        )}
        {state === 'no-profile' && (
          <Alert variant="warning">
            Create your profile first. <Link to="/profile">Go to your profile</Link>
          </Alert>
        )}
        {state === 'error' && (
          <Alert variant="error">We could not check for new matches. Please try again.</Alert>
        )}
      </div>
    </div>
  );
}
