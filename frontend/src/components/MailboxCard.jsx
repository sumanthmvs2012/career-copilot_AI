import { useCallback, useEffect, useState } from 'react';
import Alert from './Alert';
import Button from './Button';
import EmptyState from './EmptyState';
import Icon from './Icon';
import {
  disconnectMail,
  dismissReminder,
  getMailConnection,
  getReminders,
  startMailConnection,
  syncMail,
} from '../services/mailService';
import { formatRelativeTime } from '../utils/relativeTime';

function repliesText(count) {
  return `${count} new ${count === 1 ? 'reply' : 'replies'}`;
}

// Dashboard card: Gmail connection (read-only), "Check now", and follow-up reminders.
// `notice` is an optional {kind, text} shown above (e.g. the result of the Google redirect).
export default function MailboxCard({ notice, onUnauthorized, onSynced }) {
  const [state, setState] = useState('loading'); // loading | ready | error
  const [mail, setMail] = useState(null);
  const [reminders, setReminders] = useState([]);
  const [remindersState, setRemindersState] = useState('loading'); // loading | ready | error
  const [action, setAction] = useState(''); // '' | connecting | syncing | disconnecting
  const [confirming, setConfirming] = useState(false);
  const [feedback, setFeedback] = useState({ kind: '', text: '' });
  const [dismissing, setDismissing] = useState(null);

  // Returns true when the caller should show an error; false after handing a 401 to the page.
  const fail = useCallback(
    (error, label) => {
      if (error?.status === 401) {
        onUnauthorized?.();
        return false;
      }
      // Status only: never log tokens or mail data.
      console.error(label, { status: error?.status ?? 'unknown' });
      return true;
    },
    [onUnauthorized],
  );

  const loadReminders = useCallback(async () => {
    setRemindersState('loading');
    try {
      setReminders(await getReminders());
      setRemindersState('ready');
    } catch (error) {
      if (fail(error, 'Loading reminders failed')) setRemindersState('error');
    }
  }, [fail]);

  const loadMail = useCallback(async () => {
    setState('loading');
    try {
      setMail(await getMailConnection());
      setState('ready');
    } catch (error) {
      if (fail(error, 'Loading mailbox failed')) setState('error');
    }
  }, [fail]);

  useEffect(() => {
    loadMail();
    loadReminders();
  }, [loadMail, loadReminders]);

  async function connect() {
    setAction('connecting');
    setFeedback({ kind: '', text: '' });
    try {
      const { authUrl } = await startMailConnection();
      window.location.assign(authUrl);
    } catch (error) {
      if (!fail(error, 'Starting mailbox connection failed')) return;
      if (error?.status === 503) {
        setMail((current) => ({ ...(current ?? {}), configured: false }));
      } else if (error?.status === 409) {
        await loadMail();
      } else {
        setFeedback({
          kind: 'error',
          text: 'We could not start the connection. Please try again.',
        });
      }
    } finally {
      setAction('');
    }
  }

  async function checkNow() {
    setAction('syncing');
    setFeedback({ kind: '', text: '' });
    try {
      const result = await syncMail();
      setFeedback({ kind: 'success', text: repliesText(result?.newReplies ?? 0) });
      loadReminders();
      onSynced?.(result);
    } catch (error) {
      if (fail(error, 'Checking mail failed')) {
        setFeedback({ kind: 'error', text: 'We could not check your mailbox. Please try again.' });
      }
    } finally {
      setAction('');
    }
  }

  async function disconnect() {
    setAction('disconnecting');
    setFeedback({ kind: '', text: '' });
    try {
      await disconnectMail(mail.connection.id);
      setConfirming(false);
      setMail((current) => ({ ...current, connection: null }));
      setFeedback({ kind: 'success', text: 'Mailbox disconnected.' });
    } catch (error) {
      if (error?.status === 404) {
        // Already gone on the server.
        setConfirming(false);
        await loadMail();
      } else if (fail(error, 'Disconnecting mailbox failed')) {
        setFeedback({ kind: 'error', text: 'We could not disconnect. Please try again.' });
      }
    } finally {
      setAction('');
    }
  }

  async function dismiss(id) {
    setDismissing(id);
    try {
      await dismissReminder(id);
      setReminders((list) => list.filter((r) => r.id !== id));
    } catch (error) {
      if (fail(error, 'Dismissing reminder failed')) {
        setFeedback({
          kind: 'error',
          text: 'We could not dismiss the reminder. Please try again.',
        });
      }
    } finally {
      setDismissing(null);
    }
  }

  const connected = Boolean(mail?.connection);

  return (
    <section className="card" aria-labelledby="mailbox-heading">
      <h2 id="mailbox-heading">
        <Icon name="mail" size={20} /> Mailbox
      </h2>
      {notice?.text && <Alert variant={notice.kind}>{notice.text}</Alert>}
      {state === 'loading' && <p role="status">Loading mailbox…</p>}
      {state === 'error' && (
        <>
          <Alert variant="error">We could not load your mailbox. Please try again.</Alert>
          <Button variant="secondary" onClick={loadMail}>
            Try again
          </Button>
        </>
      )}
      {state === 'ready' && mail && !mail.configured && (
        <p>Mailbox connection is not set up on this server.</p>
      )}
      {state === 'ready' && mail?.configured && !connected && (
        <>
          <p>
            Connect Gmail to spot employer replies. Access is read-only, and only the subject, time
            and type of each reply are kept.
          </p>
          <Button onClick={connect} loading={action === 'connecting'} loadingText="Connecting…">
            Connect Gmail (read-only)
          </Button>
        </>
      )}
      {state === 'ready' && mail?.configured && connected && (
        <>
          <p>
            <span className="status-badge status-badge--success">Connected</span>
          </p>
          <div className="mailbox__actions">
            <Button
              variant="secondary"
              onClick={checkNow}
              loading={action === 'syncing'}
              loadingText="Checking…"
              disabled={action !== '' || confirming}
            >
              Check now
            </Button>
            {confirming ? (
              <>
                <span>Disconnect your mailbox?</span>
                <Button
                  variant="secondary"
                  onClick={disconnect}
                  loading={action === 'disconnecting'}
                  loadingText="Disconnecting…"
                >
                  Yes, disconnect
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setConfirming(false)}
                  disabled={action === 'disconnecting'}
                >
                  Cancel
                </Button>
              </>
            ) : (
              <Button
                variant="secondary"
                onClick={() => setConfirming(true)}
                disabled={action !== ''}
              >
                Disconnect
              </Button>
            )}
          </div>
        </>
      )}
      <div aria-live="polite">
        {feedback.text && (
          <Alert variant={feedback.kind === 'error' ? 'error' : 'success'}>{feedback.text}</Alert>
        )}
      </div>

      <h3>Reminders</h3>
      {remindersState === 'loading' && <p role="status">Loading reminders…</p>}
      {remindersState === 'error' && (
        <>
          <Alert variant="error">We could not load your reminders. Please try again.</Alert>
          <Button variant="secondary" onClick={loadReminders}>
            Try again
          </Button>
        </>
      )}
      {remindersState === 'ready' && reminders.length === 0 && (
        <EmptyState icon="check">No reminders right now.</EmptyState>
      )}
      {remindersState === 'ready' && reminders.length > 0 && (
        <ul className="reminder-list">
          {reminders.map((r) => (
            <li key={r.id}>
              <span>
                Follow up on <strong>{r.jobTitle}</strong>
                {r.company ? ` at ${r.company}` : ''}
                {r.dueAt && <span className="muted"> · due {formatRelativeTime(r.dueAt)}</span>}
              </span>
              <Button
                variant="secondary"
                onClick={() => dismiss(r.id)}
                disabled={dismissing === r.id}
                aria-label={`Dismiss reminder for ${r.jobTitle}`}
              >
                Dismiss
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
