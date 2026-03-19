import React from 'react';

import type { LoginCredentials, SessionStage } from '../../domain/types';

export function LoginView({
  stage,
  statusLabel,
  environmentLabel,
  errorMessage,
  onSubmit,
}: {
  stage: SessionStage;
  statusLabel: string;
  environmentLabel: string;
  errorMessage?: string;
  onSubmit(input: LoginCredentials): Promise<void>;
}) {
  const [account, setAccount] = React.useState('operator@cheese.im');
  const [password, setPassword] = React.useState('Password123!');
  const [deviceName, setDeviceName] = React.useState('Studio Browser');
  const [platform, setPlatform] = React.useState<LoginCredentials['platform']>('web');

  const isBusy = stage === 'signing_in' || stage === 'issuing_ticket' || stage === 'connecting';

  return (
    <div className="auth-shell">
      <section className="auth-hero">
        <p className="eyebrow">IM Web Client</p>
        <h1>Signal Deck</h1>
        <p className="hero-copy">
          A relay console for login, ticket exchange, connection state, and direct chat flow.
        </p>
        <div className="hero-grid">
          <div>
            <span>Flow</span>
            <strong>Login → Ticket → Gateway</strong>
          </div>
          <div>
            <span>Mode</span>
            <strong>Mock Auth / Fake Gateway</strong>
          </div>
          <div>
            <span>Scope</span>
            <strong>Direct chat MVP</strong>
          </div>
        </div>
      </section>

      <section className="auth-panel">
        <div className="panel-header">
          <p className="eyebrow">Operator Sign-In</p>
          <span className="environment-pill">{environmentLabel}</span>
        </div>
        <p className="auth-status">{statusLabel}</p>
        <form
          className="auth-form"
          onSubmit={async (event) => {
            event.preventDefault();
            await onSubmit({
              account,
              password,
              deviceName,
              platform,
            });
          }}
        >
          <label>
            <span>Account</span>
            <input
              aria-label="Account"
              value={account}
              onChange={(event) => setAccount(event.target.value)}
              autoComplete="username"
            />
          </label>
          <label>
            <span>Password</span>
            <input
              aria-label="Password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              autoComplete="current-password"
            />
          </label>
          <label>
            <span>Device Name</span>
            <input
              aria-label="Device Name"
              value={deviceName}
              onChange={(event) => setDeviceName(event.target.value)}
            />
          </label>
          <label>
            <span>Platform</span>
            <select
              aria-label="Platform"
              value={platform}
              onChange={(event) => setPlatform(event.target.value as LoginCredentials['platform'])}
            >
              <option value="web">Web</option>
              <option value="ios">iOS</option>
              <option value="android">Android</option>
              <option value="pc">PC</option>
            </select>
          </label>

          {errorMessage == null ? null : <p className="auth-error">{errorMessage}</p>}

          <button className="primary-action" type="submit" disabled={isBusy}>
            {isBusy ? 'Establishing session…' : 'Sign in'}
          </button>
        </form>
      </section>
    </div>
  );
}
