import { renderHeader } from '../components/header.js?v=20260327192619';
import { authorizeSession, state } from '../state.js?v=20260327192619';

export const pageMeta = { id: 'registration-keys-view', title: 'Сохранение ключей', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const normalizedLogin = (state.registrationDraft.login || '').trim();
  const displayLogin = normalizedLogin || '@new.user';

  const card = document.createElement('div');
  card.className = 'card stack';

  const title = document.createElement('p');
  title.className = 'auth-copy';
  title.textContent = `Поздравляю, ваш логин ${displayLogin} зарегистрирован.`;

  const question = document.createElement('p');
  question.className = 'auth-copy';
  question.textContent = 'Какие ключи вы хотите сохранить на этом устройстве?';

  const rootToggle = document.createElement('input');
  rootToggle.type = 'checkbox';
  rootToggle.checked = state.keyStorage.saveRoot;

  const blockchainToggle = document.createElement('input');
  blockchainToggle.type = 'checkbox';
  blockchainToggle.checked = state.keyStorage.saveBlockchain;

  const deviceToggle = document.createElement('input');
  deviceToggle.type = 'checkbox';
  deviceToggle.checked = state.keyStorage.saveDevice;

  rootToggle.addEventListener('change', () => {
    state.keyStorage.saveRoot = rootToggle.checked;
  });

  blockchainToggle.addEventListener('change', () => {
    state.keyStorage.saveBlockchain = blockchainToggle.checked;
  });

  deviceToggle.addEventListener('change', () => {
    state.keyStorage.saveDevice = deviceToggle.checked;
  });

  const rootRow = document.createElement('label');
  rootRow.className = 'checkbox-row';
  rootRow.append(rootToggle, document.createTextNode('root key'));

  const blockchainRow = document.createElement('label');
  blockchainRow.className = 'checkbox-row';
  blockchainRow.append(blockchainToggle, document.createTextNode('blockchain key'));

  const deviceRow = document.createElement('label');
  deviceRow.className = 'checkbox-row';
  deviceRow.append(deviceToggle, document.createTextNode('device key'));

  card.append(title, question, rootRow, deviceRow, blockchainRow);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const cancelButton = document.createElement('button');
  cancelButton.className = 'ghost-btn';
  cancelButton.type = 'button';
  cancelButton.textContent = 'Отмена';
  cancelButton.addEventListener('click', () => navigate('start-view'));

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
      title: 'Сохранение ключей',
      leftAction: { label: '←', onClick: () => navigate('registration-payment-view') },
    }),
    card,
    actions,
  );

  return screen;
}
