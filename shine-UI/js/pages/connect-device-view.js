import { renderHeader } from '../components/header.js?v=20260327192619';
import { state } from '../state.js?v=20260327192619';

export const pageMeta = { id: 'connect-device-view', title: 'Подключить устройство' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Подключить устройство',
      leftAction: { label: '←', onClick: () => navigate('device-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <p>Выберите, какие ключи передать на подключаемое устройство</p>
    <label class="checkbox-row"><input type="checkbox" id="connect-root" ${state.deviceConnect.root ? 'checked' : ''} /> root key</label>
    <label class="checkbox-row"><input type="checkbox" id="connect-blockchain" ${state.deviceConnect.blockchain ? 'checked' : ''} /> blockchain key</label>
    <label class="checkbox-row"><input type="checkbox" id="connect-device" checked disabled /> device key</label>
    <div class="row">
      <button class="icon-btn small-btn" type="button" id="tech-help">Техсправка</button>
    </div>
    <div class="stack">
      <button class="primary-btn" type="button" id="open-qr">Показать QR-код для подключения</button>
      <button class="text-btn" type="button" id="open-camera">Подключить через камеру</button>
    </div>
  `;

  const rootToggle = card.querySelector('#connect-root');
  const blockchainToggle = card.querySelector('#connect-blockchain');
  const deviceToggle = card.querySelector('#connect-device');
  deviceToggle.checked = true;

  rootToggle.addEventListener('change', () => {
    state.deviceConnect.root = rootToggle.checked;
  });
  blockchainToggle.addEventListener('change', () => {
    state.deviceConnect.blockchain = blockchainToggle.checked;
  });
  deviceToggle.addEventListener('change', () => {
    state.deviceConnect.device = true;
    deviceToggle.checked = true;
  });

  const helpModal = document.createElement('div');
  helpModal.className = 'modal-shell';
  helpModal.hidden = true;
  helpModal.innerHTML = `
    <div class="modal-backdrop" data-close="true"></div>
    <div class="modal-dialog card" role="dialog" aria-modal="true" tabindex="-1">
      <div class="row" style="align-items:flex-start;">
        <h3 style="font-size:18px;">Техсправка</h3>
        <button class="icon-btn" type="button" data-close="true" aria-label="Закрыть">✕</button>
      </div>
      <div class="stack" style="gap:6px;">
        <p class="meta-muted">пользователь выбирает ключи для передачи</p>
        <p class="meta-muted">передать можно только существующие ключи</p>
        <p class="meta-muted">если ключа нет — он недоступен</p>
        <p class="meta-muted">blockchain key — можно передать или нет</p>
        <p class="meta-muted">root key — только если существует</p>
        <p class="meta-muted">device key передаётся всегда</p>
        <p class="meta-muted">подключение происходит напрямую через QR</p>
        <p class="meta-muted">сервер не используется</p>
        <p class="meta-muted">текущая логика: устройство 1 показывает QR, устройство 2 сканирует</p>
        <p class="meta-muted">обратный сценарий пока не реализован</p>
      </div>
      <button class="primary-btn" type="button" data-close="true">OK</button>
    </div>
  `;

  const openHelp = () => {
    helpModal.hidden = false;
    helpModal.querySelector('.modal-dialog').focus();
  };

  const closeHelp = () => {
    helpModal.hidden = true;
  };

  card.querySelector('#tech-help').addEventListener('click', openHelp);
  card.querySelector('#open-qr').addEventListener('click', () => navigate('device-qr-view'));
  card.querySelector('#open-camera').addEventListener('click', () => navigate('device-camera-view'));

  helpModal.addEventListener('click', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && target.dataset.close === 'true') {
      closeHelp();
    }
  });

  helpModal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeHelp();
    }
  });

  screen.append(card, helpModal);
  return screen;
}
