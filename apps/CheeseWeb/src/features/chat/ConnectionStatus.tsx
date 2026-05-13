import type { SessionState } from '../../domain/types';
import type { UiCopy, UiLocale, UiTheme } from '../../app/ui';
import { translateSessionText } from '../../app/ui';

export function ConnectionStatus({
  copy,
  locale,
  theme,
  onThemeChange,
  onLocaleChange,
  onSignOut,
  session,
}: {
  copy: UiCopy;
  locale: UiLocale;
  theme: UiTheme;
  onThemeChange(theme: UiTheme): void;
  onLocaleChange(locale: UiLocale): void;
  onSignOut(): void;
  session: SessionState;
}) {
  return (
    <header className="app-header">
      <div className="app-header-main">
        <p className="page-title">
          {copy.brand} <span>{copy.brandAccent}</span>
        </p>
        <span className={`badge status-${session.lifecycle}`}>{translateSessionText(session.statusLabel, locale)}</span>
      </div>
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
      <div className="header-user">
        <span className="header-user-name">{session.profile?.displayName ?? copy.chat.offlineOperator}</span>
        <button className="action-btn header-signout" type="button" onClick={onSignOut}>
          {copy.header.logout}
        </button>
      </div>
    </header>
  );
}
