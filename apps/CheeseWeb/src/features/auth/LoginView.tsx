import React from 'react';

import type { LoginCredentials, SessionStage } from '../../domain/types';
import type { UiCopy, UiLocale, UiTheme } from '../../app/ui';
import { translateSessionText } from '../../app/ui';

export function LoginView({
  copy,
  theme,
  locale,
  onThemeChange,
  onLocaleChange,
  stage,
  statusLabel,
  environmentLabel,
  errorMessage,
  onSubmit,
}: {
  copy: UiCopy;
  theme: UiTheme;
  locale: UiLocale;
  onThemeChange(theme: UiTheme): void;
  onLocaleChange(locale: UiLocale): void;
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
  const [activeField, setActiveField] = React.useState<'account' | 'password' | null>(null);

  const isBusy = stage === 'signing_in' || stage === 'issuing_ticket' || stage === 'connecting';

  return (
    <main className="app-shell auth-shell">
      <section className="auth-minimal">
        <div className="auth-login-layout">
          <section className="auth-illustration" data-field={activeField ?? 'idle'} aria-hidden="true">
            <div className="auth-illustration-stage">
              <div className="auth-glow auth-glow-a" />
              <div className="auth-glow auth-glow-b" />
              <div className="auth-grid" />
              <p className="auth-brand auth-brand-orb">
                {copy.brand} <span>{copy.brandAccent}</span>
              </p>
              <div className="auth-character auth-character-a">
                <span className="auth-eye auth-eye-left" />
                <span className="auth-eye auth-eye-right" />
              </div>
              <div className="auth-character auth-character-b">
                <span className="auth-eye auth-eye-left" />
                <span className="auth-eye auth-eye-right" />
              </div>
              <div className="auth-orbit auth-orbit-a" />
              <div className="auth-orbit auth-orbit-b" />
            </div>
          </section>

          <div className="auth-login-side">
            <div className="auth-toolbar">
              <div className="header-controls">
                <label className="theme-switch">
                  <span className="theme-switch-label">{theme === 'light' ? copy.themeLight : copy.themeDark}</span>
                  <button
                    aria-label="Theme switch"
                    aria-pressed={theme === 'dark'}
                    className={theme === 'dark' ? 'theme-switch-track is-dark' : 'theme-switch-track'}
                    type="button"
                    onClick={() => onThemeChange(theme === 'light' ? 'dark' : 'light')}
                  >
                    <span className="theme-switch-thumb" />
                  </button>
                </label>
                <label className="locale-select">
                  <span>{copy.header.language}</span>
                  <select
                    aria-label={copy.header.language}
                    value={locale}
                    onChange={(event) => onLocaleChange(event.target.value as UiLocale)}
                  >
                    <option value="en">{copy.localeEnglish}</option>
                    <option value="zh">{copy.localeChinese}</option>
                  </select>
                </label>
              </div>
            </div>

            <section className="auth-card auth-card-minimal" aria-label="Operator Sign-In">
              <div className="auth-card-head auth-card-head-minimal">
                <h1 className="auth-card-title auth-card-title-minimal">{copy.login.title}</h1>
                <p className="auth-card-status">{copy.login.subtitle}</p>
              </div>

              <form
                className="auth-form auth-form-light"
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
                  <span>{copy.login.account}</span>
                  <input
                    aria-label="Account"
                    value={account}
                    onFocus={() => setActiveField('account')}
                    onBlur={() => setActiveField(null)}
                    onChange={(event) => setAccount(event.target.value)}
                    autoComplete="username"
                    placeholder="operator@cheese.im"
                  />
                </label>
                <label>
                  <span>{copy.login.password}</span>
                  <input
                    aria-label="Password"
                    value={password}
                    onFocus={() => setActiveField('password')}
                    onBlur={() => setActiveField(null)}
                    onChange={(event) => setPassword(event.target.value)}
                    type="password"
                    autoComplete="current-password"
                    placeholder="Password"
                  />
                </label>
                <label>
                  <span>{copy.login.deviceName}</span>
                  <input
                    aria-label="Device Name"
                    value={deviceName}
                    onChange={(event) => setDeviceName(event.target.value)}
                    placeholder="Studio Browser"
                  />
                </label>
                <label>
                  <span>{copy.login.platform}</span>
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

                <p className="auth-status auth-status-light">{translateSessionText(statusLabel, locale)}</p>
                {errorMessage == null ? null : <p className="auth-error auth-error-light">{errorMessage}</p>}

                <div className="form-actions auth-form-actions auth-form-actions-stacked">
                  <button className="send-btn auth-submit-btn" type="submit" disabled={isBusy}>
                    {isBusy ? copy.login.submitBusy : copy.login.submitIdle}
                  </button>
                  <p className="auth-footnote auth-footnote-minimal">
                    <span>{copy.login.environment}</span>
                    <strong>{translateSessionText(environmentLabel, locale)}</strong>
                  </p>
                </div>
              </form>
            </section>
          </div>
        </div>
      </section>
    </main>
  );
}
