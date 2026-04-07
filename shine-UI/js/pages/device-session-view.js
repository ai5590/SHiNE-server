import { renderHeader } from '../components/header.js?v=20260407105357';
import {
  authService,
  isSessionInvalidError,
  refreshSessions,
  setAuthError,
  state,
  terminateCurrentSession,
} from '../state.js?v=20260407105357';

export const pageMeta = { id: 'device-session-view', title: 'Сеанс устройства' };

function formatSessionTime(ms) {
  return new Date(ms).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function render({ navigate, route }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const sessionId = route?.params?.sessionId || '';
  const session = (state.sessions || []).find((item) => item.sessionId === sessionId) || state.sessions[0];

  screen.append(
    renderHeader({
      title: 'Сеанс устройства',
      leftAction: { label: '←', onClick: () => navigate('device-view') },
    }),
  );

  if (!session) {
    const empty = document.createElement('div');
    empty.className = 'card';
    empty.textContent = 'Сеанс не найден.';
    screen.append(empty);
    return screen;
  }

  const details = document.createElement('div');
  details.className = 'card stack';
  details.innerHTML = `
    <div><p class="meta-muted">sessionId</p><p>${session.sessionId}</p></div>
    <div><p class="meta-muted">clientInfoFromClient</p><p>${session.clientInfoFromClient || '-'}</p></div>
    <div><p class="meta-muted">clientInfoFromRequest</p><p>${session.clientInfoFromRequest || '-'}</p></div>
    <div><p class="meta-muted">geo</p><p>${session.geo || 'unknown'}</p></div>
    <div><p class="meta-muted">дата/время</p><p>${formatSessionTime(session.lastAuthenticatedAtMs || Date.now())}</p></div>
  `;

  const actionBtn = document.createElement('button');
  actionBtn.className = 'text-btn';
  actionBtn.type = 'button';
  actionBtn.textContent = 'Завершить сеанс';

  actionBtn.addEventListener('click', async () => {
    const isCurrentSession = session.sessionId === state.session.sessionId;
    const confirmed = window.confirm(
      isCurrentSession ? 'Хотите завершить текущую сессию?' : 'Хотите завершить этот сеанс?',
    );
    if (!confirmed) return;

    try {
      await authService.closeSession(session.sessionId);
    } catch (error) {
      if (!isSessionInvalidError(error)) {
        setAuthError(error.message);
        window.alert(error.message);
        return;
      }
    }

    if (isCurrentSession) {
      await terminateCurrentSession({
        infoMessage: 'Текущая сессия завершена, данные на устройстве очищены.',
      });
      return;
    }

    try {
      await refreshSessions();
      navigate('device-view');
    } catch (error) {
      if (isSessionInvalidError(error)) {
        await terminateCurrentSession({
          infoMessage: 'Сессия на этом устройстве уже завершена. Выполните вход заново.',
        });
        return;
      }
      setAuthError(error.message);
      window.alert(error.message);
    }
  });

  screen.append(details, actionBtn);
  return screen;
}
