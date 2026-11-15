import { useState } from 'react';
import Alert from './Alert';
import Button from './Button';
import { createApplication } from '../services/applicationsService';

// Adds the job to the user's applications as a draft. One click per job; shows "Tracked" afterwards.
export default function TrackJobButton({ jobId, jobTitle, onTracked }) {
  const [state, setState] = useState('idle'); // idle | saving | tracked | conflict | error
  const [message, setMessage] = useState('');

  async function track() {
    setState('saving');
    setMessage('');
    try {
      await createApplication({ jobId, status: 'draft' });
      setState('tracked');
      onTracked?.();
    } catch (error) {
      // Status only: never log tokens or text.
      console.error('Tracking job failed', { status: error?.status ?? 'unknown' });
      setMessage(
        error?.status === 409
          ? error?.data?.message || 'You already track this job.'
          : 'We could not track this job. Please try again.',
      );
      setState(error?.status === 409 ? 'conflict' : 'error');
    }
  }

  return (
    <div className="job-item__track">
      {state === 'tracked' && (
        <span className="status-badge status-badge--success" role="status">
          Tracked
        </span>
      )}
      {(state === 'idle' || state === 'saving' || state === 'error') && (
        <Button
          variant="secondary"
          loading={state === 'saving'}
          loadingText="Tracking…"
          onClick={track}
          aria-label={`Track this job: ${jobTitle || 'this job'}`}
        >
          Track this job
        </Button>
      )}
      {state === 'conflict' && <Alert variant="warning">{message}</Alert>}
      {state === 'error' && <Alert variant="error">{message}</Alert>}
    </div>
  );
}
