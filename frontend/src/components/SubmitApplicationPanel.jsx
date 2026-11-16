import { useId, useState } from 'react';
import Alert from './Alert';
import Button from './Button';
import TextArea from './TextArea';
import { submitApplication, updateApplication } from '../services/applicationsService';

export const COVER_LETTER_MAX = 10000;

// Inline panel for a draft application: edit the cover letter, save it, or submit (after a confirm).
// Submitting only marks the application as applied inside this app; it never contacts the employer.
export default function SubmitApplicationPanel({
  application,
  onClose,
  onUpdated,
  onUnauthorized,
}) {
  const headingId = useId();
  const [coverLetter, setCoverLetter] = useState(application.coverLetter ?? '');
  const [busy, setBusy] = useState(''); // '' | saving | submitting
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState({ kind: '', text: '' });

  function handleError(error, fallback) {
    if (error?.status === 401) return onUnauthorized?.();
    // Status only: never log the cover letter or token.
    console.error('Application update failed', { status: error?.status ?? 'unknown' });
    if (error?.status === 409) {
      setMessage({ kind: 'error', text: 'Only a draft can be submitted.' });
    } else {
      setMessage({ kind: 'error', text: fallback });
    }
    setConfirming(false);
  }

  async function save() {
    setBusy('saving');
    setMessage({ kind: '', text: '' });
    try {
      const row = await updateApplication(application.id, { coverLetter });
      onUpdated?.(row);
      setMessage({ kind: 'success', text: 'Cover letter saved.' });
    } catch (error) {
      handleError(error, 'We could not save the cover letter. Please try again.');
    } finally {
      setBusy('');
    }
  }

  async function submit() {
    setBusy('submitting');
    setMessage({ kind: '', text: '' });
    try {
      const row = await submitApplication(application.id, coverLetter);
      setConfirming(false);
      onUpdated?.(row);
      onClose?.({ submitted: true });
    } catch (error) {
      handleError(error, 'We could not submit this application. Please try again.');
    } finally {
      setBusy('');
    }
  }

  const label = `${application.jobTitle}${application.company ? ` at ${application.company}` : ''}`;

  return (
    <div className="submit-panel" role="group" aria-labelledby={headingId}>
      <h3 id={headingId}>Edit and submit: {label}</h3>
      <TextArea
        label="Cover letter"
        rows={8}
        maxLength={COVER_LETTER_MAX}
        value={coverLetter}
        disabled={busy !== ''}
        hint={`${coverLetter.length} of ${COVER_LETTER_MAX} characters`}
        onChange={(event) => {
          setCoverLetter(event.target.value);
          setMessage({ kind: '', text: '' });
        }}
      />
      {message.text && (
        <Alert variant={message.kind === 'success' ? 'success' : 'error'}>{message.text}</Alert>
      )}
      {confirming ? (
        <div role="group" aria-label="Confirm submission">
          <p>
            This marks the application as applied in the app. It does not send it to the
            employer&apos;s site.
          </p>
          <div className="submit-panel__actions">
            <Button onClick={submit} loading={busy === 'submitting'} loadingText="Submitting…">
              Yes, mark as applied
            </Button>
            <Button variant="secondary" onClick={() => setConfirming(false)} disabled={busy !== ''}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <div className="submit-panel__actions">
          <Button
            variant="secondary"
            onClick={save}
            loading={busy === 'saving'}
            loadingText="Saving…"
            disabled={busy !== ''}
          >
            Save
          </Button>
          <Button onClick={() => setConfirming(true)} disabled={busy !== ''}>
            Submit application
          </Button>
          <Button variant="secondary" onClick={() => onClose?.({ submitted: false })}>
            Close
          </Button>
        </div>
      )}
    </div>
  );
}
