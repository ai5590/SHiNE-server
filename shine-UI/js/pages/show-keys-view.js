import { renderHeader } from '../components/header.js?v=20260405171816';
import { state } from '../state.js?v=20260405171816';
import { loadEncryptedUserSecrets } from '../services/key-vault.js?v=20260405171816';

export const pageMeta = { id: 'show-keys-view', title: 'Показать ключи' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const visible = {
    root: false,
    blockchain: false,
    device: false,
  };

  const keys = {
    root: '',
    blockchain: '',
    device: '',
  };

  screen.append(
    renderHeader({
      title: 'Показать ключи',
      leftAction: { label: '←', onClick: () => navigate('device-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';

  const status = document.createElement('p');
  status.className = 'meta-muted';
  status.textContent = 'Загружаем сохранённые ключи...';
  card.append(status);

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

  card.append(
    renderField('root', 'root key'),
    renderField('blockchain', 'blockchain.key'),
    renderField('device', 'device key'),
  );

  const setMissingState = (id) => {
    const valueEl = card.querySelector(`[data-value="${id}"]`);
    const btnEl = card.querySelector(`[data-toggle="${id}"]`);
    valueEl.textContent = 'нет данных';
    btnEl.disabled = true;
    btnEl.textContent = 'Нет';
  };

  const updateField = (id) => {
    const valueEl = card.querySelector(`[data-value="${id}"]`);
    const btnEl = card.querySelector(`[data-toggle="${id}"]`);
    if (!keys[id]) {
      setMissingState(id);
      return;
    }
    valueEl.textContent = visible[id] ? keys[id] : '*****';
    btnEl.disabled = false;
    btnEl.textContent = visible[id] ? 'Скрыть' : 'Показать';
  };

  card.querySelectorAll('[data-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const { toggle } = button.dataset;
      if (!keys[toggle]) return;
      visible[toggle] = !visible[toggle];
      updateField(toggle);
    });
  });

  ['root', 'blockchain', 'device'].forEach((id) => updateField(id));

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const closeButton = document.createElement('button');
  closeButton.className = 'ghost-btn';
  closeButton.type = 'button';
  closeButton.textContent = 'Назад';
  closeButton.addEventListener('click', () => navigate('device-view'));
  actions.append(closeButton);

  (async () => {
    try {
      if (!state.session.login || !state.session.storagePwdInMemory) {
        throw new Error('Нет активной сессии для чтения ключей');
      }

      const savedKeys = await loadEncryptedUserSecrets(
        state.session.login,
        state.session.storagePwdInMemory,
      );

      keys.root = savedKeys.rootKey || '';
      keys.blockchain = savedKeys.blockchainKey || '';
      keys.device = savedKeys.deviceKey || '';

      if (keys.root || keys.blockchain || keys.device) {
        status.textContent = 'Показаны только ключи, сохранённые на этом устройстве.';
      } else {
        status.textContent = 'На этом устройстве нет сохранённых ключей.';
      }
    } catch (error) {
      status.textContent = 'На этом устройстве нет сохранённых ключей.';
    }

    ['root', 'blockchain', 'device'].forEach((id) => updateField(id));
  })();

  screen.append(card, actions);
  return screen;
}
