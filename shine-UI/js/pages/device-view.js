import { renderHeader } from '../components/header.js?v=20260327192619';
import { deviceSessions } from '../mock-data.js?v=20260327192619';
import { terminateCurrentSession } from '../state.js?v=20260327192619';

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
    <button class="primary-btn" type="button" id="connect-device-btn">Подключить устройство</button>
    <button class="text-btn" type="button" id="show-keys-btn">Показать ключи</button>
  `;

  actions.querySelector('#connect-device-btn').addEventListener('click', () => navigate('connect-device-view'));
  actions.querySelector('#show-keys-btn').addEventListener('click', () => navigate('show-keys-view'));

  const sessionsBlock = document.createElement('div');
  sessionsBlock.className = 'card stack';

  const currentSession = deviceSessions[0];
  const otherSessions = deviceSessions.slice(1);

  const createSessionItem = (session, isCurrent) => {
    const item = document.createElement('button');
    item.className = 'session-item';
    item.type = 'button';
    item.innerHTML = `
      <div class="row" style="align-items:flex-start;">
        <div class="stack" style="gap:4px; text-align:left;">
          <strong>${session.clientInfoFromClient}</strong>
          <span class="meta-muted">${session.geo}</span>
        </div>
        <span class="meta-muted">${formatSessionTime(session.lastAuthenticatedAtMs)}</span>
      </div>
      ${
        isCurrent
          ? '<div><span class="session-current-badge">Текущий сеанс</span></div>'
          : ''
      }
    `;
    item.addEventListener('click', () => navigate(`device-session-view/${session.sessionId}`));
    return item;
  };

  const currentMenu = document.createElement('div');
  currentMenu.className = 'stack';
  currentMenu.innerHTML = '<p class="meta-muted">Текущий сеанс</p>';
  currentMenu.append(createSessionItem(currentSession, true));

  const endCurrentSessionBtn = document.createElement('button');
  endCurrentSessionBtn.className = 'text-btn';
  endCurrentSessionBtn.type = 'button';
  endCurrentSessionBtn.textContent = 'Завершить текущую сессию';
  currentMenu.append(endCurrentSessionBtn);

  const othersMenu = document.createElement('div');
  othersMenu.className = 'stack';
  othersMenu.innerHTML = '<p class="meta-muted">Остальные активные сеансы</p>';

  if (otherSessions.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'meta-muted';
    empty.textContent = 'Других активных сеансов нет.';
    othersMenu.append(empty);
  } else {
    otherSessions.forEach((session) => {
      othersMenu.append(createSessionItem(session, false));
    });
  }

  const confirmModal = document.createElement('div');
  confirmModal.className = 'modal-shell';
  confirmModal.hidden = true;
  confirmModal.innerHTML = `
    <div class="modal-backdrop" data-close="true"></div>
    <div class="modal-dialog card" role="dialog" aria-modal="true" tabindex="-1">
      <p>Завершить текущую сессию?</p>
      <div class="auth-footer-actions">
        <button class="primary-btn" type="button" id="confirm-end-yes">Да</button>
        <button class="ghost-btn" type="button" data-close="true">Нет</button>
      </div>
    </div>
  `;

  const openModal = () => {
    confirmModal.hidden = false;
    confirmModal.querySelector('.modal-dialog').focus();
  };

  const closeModal = () => {
    confirmModal.hidden = true;
  };

  endCurrentSessionBtn.addEventListener('click', openModal);

  confirmModal.querySelector('#confirm-end-yes').addEventListener('click', () => {
    terminateCurrentSession();
    navigate('start-view');
  });

  confirmModal.addEventListener('click', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && target.dataset.close === 'true') {
      closeModal();
    }
  });

  confirmModal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeModal();
    }
  });

  sessionsBlock.append(currentMenu, othersMenu);
  screen.append(actions, sessionsBlock, confirmModal);
  return screen;
}
