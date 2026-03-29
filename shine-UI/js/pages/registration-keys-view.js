import { renderHeader } from '../components/header.js?v=20260327192619';
import {
  authService,
  authorizeSession,
  refreshSessions,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js?v=20260327192619';

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
  title.textContent = `Отлично, логин ${displayLogin} зарегистрирован.`;

  const question = document.createElement('p');
  question.className = 'auth-copy';
  question.textContent = 'Какие ключи сохранить в зашифрованном контейнере IndexedDB?';

  const rootToggle = document.createElement('input');
  rootToggle.type = 'checkbox';
  rootToggle.checked = state.keyStorage.saveRoot;

  const blockchainToggle = document.createElement('input');
  blockchainToggle.type = 'checkbox';
  blockchainToggle.checked = state.keyStorage.saveBlockchain;

  const deviceToggle = document.createElement('input');
  deviceToggle.type = 'checkbox';
  deviceToggle.checked = true;
  deviceToggle.disabled = true;

  const rootRow = document.createElement('label');
  rootRow.className = 'checkbox-row';
  rootRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveRoot ? 'checked' : ''} disabled /> <span>root key</span>`;

  const blockchainRow = document.createElement('label');
  blockchainRow.className = 'checkbox-row';
  blockchainRow.innerHTML = `<input type="checkbox" ${state.keyStorage.saveBlockchain ? 'checked' : ''} disabled /> <span>blockchain key</span>`;

  const deviceRow = document.createElement('label');
  deviceRow.className = 'checkbox-row';
  deviceRow.append(deviceToggle, document.createTextNode('device key (всегда)'));

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
  okButton.addEventListener('click', async () => {
    try {
      if (!state.registrationDraft.pendingKeyBundle || !state.registrationDraft.pendingSessionMaterial) {
        throw new Error('Сначала завершите шаг регистрации на предыдущем экране');
      }

      state.keyStorage.saveRoot = rootToggle.checked;
      state.keyStorage.saveBlockchain = blockchainToggle.checked;

      await authService.persistSelectedKeys(
        state.registrationDraft.login,
        state.registrationDraft.storagePwd,
        state.registrationDraft.pendingKeyBundle,
        {
          saveRoot: state.keyStorage.saveRoot,
          saveBlockchain: state.keyStorage.saveBlockchain,
        },
      );
      await authService.persistSessionMaterial(
        state.registrationDraft.login,
        state.registrationDraft.pendingSessionMaterial,
      );

      authorizeSession({
        login: state.registrationDraft.login,
        sessionId: state.registrationDraft.sessionId,
        storagePwd: state.registrationDraft.storagePwd,
      });
      await refreshSessions();
      setAuthInfo('Ключи сохранены, регистрация завершена.');
      navigate('profile-view');
    } catch (error) {
      setAuthError(error.message);
      window.alert(error.message);
    }
  });

  actions.append(cancelButton, okButton);

  screen.append(
    renderHeader({
      title: 'Сохранение ключей',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    card,
    actions,
  );

  return screen;
}
