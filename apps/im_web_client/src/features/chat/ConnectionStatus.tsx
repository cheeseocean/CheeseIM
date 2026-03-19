import type { SessionState } from '../../domain/types';

export function ConnectionStatus({ session }: { session: SessionState }) {
  return (
    <header className="top-status-shell">
      <div className="top-status-bar">
        <div className="status-stack">
          <span className={`status-pill status-${session.lifecycle}`}>{session.statusLabel}</span>
          <span className="environment-pill">{session.environmentLabel}</span>
        </div>
        <div className="status-metrics">
          <div>
            <span>Access</span>
            <strong>{session.accessToken == null ? 'Awaiting login' : 'Access token active'}</strong>
          </div>
          <div>
            <span>Ticket</span>
            <strong>{session.ticketStatusLabel}</strong>
          </div>
          <div>
            <span>Transport</span>
            <strong>{session.transportLabel}</strong>
          </div>
        </div>
      </div>
      {session.errorMessage == null ? null : <p className="status-alert">{session.errorMessage}</p>}
    </header>
  );
}
