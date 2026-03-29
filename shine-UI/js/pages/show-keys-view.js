import { renderHeader } from '../components/header.js?v=20260327192619';

export const pageMeta = { id: 'show-keys-view', title: 'Показать ключи' };

function randomKey(length = 44) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz123456789';
  let result = '';
  for (let i = 0; i < length; i += 1) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const keys = {
    root: randomKey(),
    blockchain: randomKey(),
    device: randomKey(),
  };

  const visible = {
    root: false,
    blockchain: false,
    device: false,
  };

  screen.append(
    renderHeader({
      title: 'Показать ключи',
      leftAction: { label: '←', onClick: () => navigate('device-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';

  const renderField = (id, label) => {
    const row = document.createElement('div');
    row.className = 'key-card stack';
    row.innerHTML = `
      <div class="row">
        <span class="field-label">${label}</span>
        <button class="icon-btn small-btn" type="button" data-toggle="${id}">Показать</button>
      </div>
      <div class="key-value" data-value="${id}">*****</div>
    `;
    return row;
  };

  card.append(renderField('root', 'root key'), renderField('blockchain', 'blockchain key'), renderField('device', 'device key'));

  const updateField = (id) => {
    const valueEl = card.querySelector(`[data-value="${id}"]`);
    const btnEl = card.querySelector(`[data-toggle="${id}"]`);
    valueEl.textContent = visible[id] ? keys[id] : '*****';
    btnEl.textContent = visible[id] ? 'Скрыть' : 'Показать';
  };

  card.querySelectorAll('[data-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const { toggle } = button.dataset;
      visible[toggle] = !visible[toggle];
      updateField(toggle);
    });
  });

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';
  actions.innerHTML = `
    <button class="primary-btn" type="button" id="save-keys">Сохранить новые</button>
    <button class="ghost-btn" type="button" id="cancel-keys">Отмена</button>
  `;

  const confirmModal = document.createElement('div');
  confirmModal.className = 'modal-shell';
  confirmModal.hidden = true;
  confirmModal.innerHTML = `
    <div class="modal-backdrop" data-close="true"></div>
    <div class="modal-dialog card" role="dialog" aria-modal="true" tabindex="-1">
      <p>Вы уверены, что хотите изменить ключи?</p>
      <div class="auth-footer-actions">
        <button class="primary-btn" type="button" id="confirm-keys-ok">ОК</button>
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

  actions.querySelector('#save-keys').addEventListener('click', openModal);
  actions.querySelector('#cancel-keys').addEventListener('click', () => navigate('device-view'));

  confirmModal.querySelector('#confirm-keys-ok').addEventListener('click', () => {
    keys.root = randomKey();
    keys.blockchain = randomKey();
    keys.device = randomKey();
    updateField('root');
    updateField('blockchain');
    updateField('device');
    closeModal();
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

  screen.append(card, actions, confirmModal);
  return screen;
}
