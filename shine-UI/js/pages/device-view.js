import { renderHeader } from '../components/header.js?v=20260406221807';
import {
  authService,
  isSessionInvalidError,
  refreshSessions,
  setAuthError,
  setAuthInfo,
  state,
  terminateCurrentSession,
} from '../state.js?v=20260406221807';

export const pageMeta = { id: 'device-view', title: 'Устройства' };

function formatSessionTime(ms) {
  return new Date(ms).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Устройства',
      leftAction: { label: '←', onClick: () => navigate('settings-view') },
    }),
  );

  const actions = document.createElement('div');
  actions.className = 'card stack';
  actions.innerHTML = `
    <button class="primary-btn" type="button" id="reload-sessions-btn">Обновить сессии</button>
    <button class="text-btn" type="button" id="show-keys-btn">Показать ключи</button>
  `;

  actions.querySelector('#show-keys-btn').addEventListener('click', () => navigate('show-keys-view'));

  const sessionsBlock = document.createElement('div');
  sessionsBlock.className = 'card stack';

  const buildList = () => {
    sessionsBlock.innerHTML = '';
    const sessions = state.sessions || [];
    const current = sessions.find((s) => s.sessionId === state.session.sessionId) || sessions[0];
    const others = sessions.filter((s) => s.sessionId !== current?.sessionId);

    const createSessionItem = (session, isCurrent) => {
      const item = document.createElement('button');
      item.className = 'session-item';
      item.type = 'button';
      item.innerHTML = `
        <div class="row" style="align-items:flex-start;">
          <div class="stack" style="gap:4px; text-align:left;">
            <strong>${session.clientInfoFromClient || 'unknown client'}</strong>
            <span class="meta-muted">${session.geo || 'unknown'}</span>
          </div>
          <span class="meta-muted">${formatSessionTime(session.lastAuthenticatedAtMs || Date.now())}</span>
        </div>
        ${isCurrent ? '<div><span class="session-current-badge">Текущий сеанс</span></div>' : ''}
      `;
      item.addEventListener('click', () => navigate(`device-session-view/${session.sessionId}`));
      return item;
    };

    if (!current) {
      const empty = document.createElement('p');
      empty.className = 'meta-muted';
      empty.textContent = 'Активные сессии не найдены.';
      sessionsBlock.append(empty);
      return;
    }

    const currentMenu = document.createElement('div');
    currentMenu.className = 'stack';
    currentMenu.innerHTML = '<p class="meta-muted">Текущий сеанс</p>';
    currentMenu.append(createSessionItem(current, true));

    const endCurrentSessionBtn = document.createElement('button');
    endCurrentSessionBtn.className = 'text-btn';
    endCurrentSessionBtn.type = 'button';
    endCurrentSessionBtn.textContent = 'Завершить текущую сессию';
    endCurrentSessionBtn.addEventListener('click', async () => {
      const confirmed = window.confirm('Хотите завершить текущую сессию?');
      if (!confirmed) return;

      try {
        await authService.closeSession(state.session.sessionId);
      } catch (error) {
        if (!isSessionInvalidError(error)) {
          setAuthError(error.message);
          window.alert(error.message);
          return;
        }
      }

      await terminateCurrentSession({
        infoMessage: 'Текущая сессия завершена, данные на устройстве очищены.',
      });
    });
    currentMenu.append(endCurrentSessionBtn);

    const othersMenu = document.createElement('div');
    othersMenu.className = 'stack';
    othersMenu.innerHTML = '<p class="meta-muted">Остальные активные сеансы</p>';

    if (others.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'meta-muted';
      empty.textContent = 'Других активных сеансов нет.';
      othersMenu.append(empty);
    } else {
      others.forEach((session) => {
        othersMenu.append(createSessionItem(session, false));
      });
    }

    sessionsBlock.append(currentMenu, othersMenu);
  };

  actions.querySelector('#reload-sessions-btn').addEventListener('click', async () => {
    try {
      await refreshSessions();
      buildList();
      setAuthInfo('Список сессий обновлён.');
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

  buildList();
  screen.append(actions, sessionsBlock);
  return screen;
}
