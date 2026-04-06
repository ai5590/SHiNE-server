import { renderHeader } from '../components/header.js?v=20260405171816';
import { authorizeSession, state } from '../state.js?v=20260405171816';

export const pageMeta = { id: 'key-storage-view', title: 'Какие ключи сохранить', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const card = document.createElement('div');
  card.className = 'card stack';

  const rootToggle = document.createElement('input');
  rootToggle.type = 'checkbox';
  rootToggle.checked = state.keyStorage.saveRoot;
  rootToggle.addEventListener('change', () => {
    state.keyStorage.saveRoot = rootToggle.checked;
    if (rootToggle.checked) {
      window.alert('Мы советуем не сохранять главный ключ на устройстве, он используется только для смены паролей и основных настроек.');
    }
  });

  const blockchainToggle = document.createElement('input');
  blockchainToggle.type = 'checkbox';
  blockchainToggle.checked = state.keyStorage.saveBlockchain;
  blockchainToggle.addEventListener('change', () => {
    state.keyStorage.saveBlockchain = blockchainToggle.checked;
  });

  const deviceToggle = document.createElement('input');
  deviceToggle.type = 'checkbox';
  deviceToggle.checked = true;
  deviceToggle.disabled = true;

  card.innerHTML = `
    <div class="key-card stack">
      <label class="checkbox-row"><span class="field-label">Root Key</span></label>
      <input class="input" type="text" value="${state.keyStorage.rootKey}" />
    </div>
    <div class="key-card stack">
      <label class="checkbox-row"><span class="field-label">Blockchain Key</span></label>
      <input class="input" type="text" value="${state.keyStorage.blockchainKey}" />
    </div>
    <div class="key-card stack">
      <label class="checkbox-row"><span class="field-label">Device Key</span></label>
      <input class="input" type="text" value="${state.keyStorage.deviceKey}" />
    </div>
  `;

  card.children[0].querySelector('label').prepend(rootToggle);
  card.children[1].querySelector('label').prepend(blockchainToggle);
  card.children[2].querySelector('label').prepend(deviceToggle);

  const rootInput = card.children[0].querySelector('.input');
  rootInput.addEventListener('input', () => {
    state.keyStorage.rootKey = rootInput.value;
  });

  const blockchainInput = card.children[1].querySelector('.input');
  blockchainInput.addEventListener('input', () => {
    state.keyStorage.blockchainKey = blockchainInput.value;
  });

  const deviceInput = card.children[2].querySelector('.input');
  deviceInput.addEventListener('input', () => {
    state.keyStorage.deviceKey = deviceInput.value;
  });

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const cancelButton = document.createElement('button');
  cancelButton.className = 'ghost-btn';
  cancelButton.type = 'button';
  cancelButton.textContent = 'Отмена';
  cancelButton.addEventListener('click', () => navigate('login-password-view'));

  const okButton = document.createElement('button');
  okButton.className = 'primary-btn';
  okButton.type = 'button';
  okButton.textContent = 'OK';
  okButton.addEventListener('click', () => {
    authorizeSession();
    navigate('profile-view');
  });

  actions.append(cancelButton, okButton);

  screen.append(
    renderHeader({
      title: 'Какие ключи сохранить',
      leftAction: { label: '←', onClick: () => navigate('login-password-view') },
    }),
    card,
    actions,
  );

  return screen;
}
