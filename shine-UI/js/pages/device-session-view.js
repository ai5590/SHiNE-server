import { renderHeader } from '../components/header.js?v=20260327192619';
import { deviceSessions } from '../mock-data.js?v=20260327192619';

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
  const session = deviceSessions.find((item) => item.sessionId === sessionId) || deviceSessions[0];

  screen.append(
    renderHeader({
      title: 'Сеанс устройства',
      leftAction: { label: '←', onClick: () => navigate('device-view') },
    }),
  );

  const details = document.createElement('div');
  details.className = 'card stack';
  details.innerHTML = `
    <div><p class="meta-muted">sessionId</p><p>${session.sessionId}</p></div>
    <div><p class="meta-muted">clientInfoFromClient</p><p>${session.clientInfoFromClient}</p></div>
    <div><p class="meta-muted">clientInfoFromRequest</p><p>${session.clientInfoFromRequest}</p></div>
    <div><p class="meta-muted">geo</p><p>${session.geo}</p></div>
    <div><p class="meta-muted">дата/время</p><p>${formatSessionTime(session.lastAuthenticatedAtMs)}</p></div>
  `;

  const actionBtn = document.createElement('button');
  actionBtn.className = 'text-btn';
  actionBtn.type = 'button';
  actionBtn.textContent = 'Завершить сеанс';

  const confirmModal = document.createElement('div');
  confirmModal.className = 'modal-shell';
  confirmModal.hidden = true;
  confirmModal.innerHTML = `
    <div class="modal-backdrop" data-close="true"></div>
    <div class="modal-dialog card" role="dialog" aria-modal="true" tabindex="-1">
      <p>Вы уверены, что хотите завершить этот сеанс?</p>
      <div class="auth-footer-actions">
        <button class="primary-btn" type="button" id="confirm-session-ok">ОК</button>
        <button class="ghost-btn" type="button" data-close="true">Отмена</button>
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

  actionBtn.addEventListener('click', openModal);

  confirmModal.querySelector('#confirm-session-ok').addEventListener('click', closeModal);

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

  screen.append(details, actionBtn, confirmModal);
  return screen;
}
