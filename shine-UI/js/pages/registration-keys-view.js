import { renderHeader } from '../components/header.js';
import {
  authService,
  authorizeSession,
  refreshSessions,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';

export const pageMeta = { id: 'registration-keys-view', title: 'Сохранение ключей', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const isLoginFlow = state.registrationDraft.flowType === 'login';
  const normalizedLogin = (state.registrationDraft.login || '').trim();
  const displayLogin = normalizedLogin || '@new.user';

  const card = document.createElement('div');
  card.className = 'card stack';

  const title = document.createElement('p');
  title.className = 'auth-copy';
  title.textContent = isLoginFlow
    ? `Вход выполнен для логина ${displayLogin}.`
    : `Отлично, логин ${displayLogin} зарегистрирован.`;

  const question = document.createElement('p');
  question.className = 'auth-copy';
  question.textContent = 'Какие ключи сохранить в зашифрованном контейнере IndexedDB?';

  const nextStep = document.createElement('p');
  nextStep.className = 'meta-muted';
  nextStep.textContent = 'После сохранения откроется профиль. Для проверки откройте вкладку «Каналы».';

  const status = document.createElement('p');
  status.className = 'status-line is-unavailable';
  status.style.display = 'none';

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
  rootRow.append(rootToggle, document.createTextNode('Ключ root'));

  const blockchainRow = document.createElement('label');
  blockchainRow.className = 'checkbox-row';
  blockchainRow.append(blockchainToggle, document.createTextNode('Ключ blockchain'));

  const deviceRow = document.createElement('label');
  deviceRow.className = 'checkbox-row';
  deviceRow.append(deviceToggle, document.createTextNode('Ключ device (всегда)'));

  card.append(title, question, nextStep, rootRow, blockchainRow, deviceRow, status);

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
    status.style.display = 'none';
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

      if (!state.keyStorage.saveRoot && state.registrationDraft.pendingKeyBundle) {
        state.registrationDraft.pendingKeyBundle.rootPair = null;
      }
      if (!state.keyStorage.saveBlockchain && state.registrationDraft.pendingKeyBundle) {
        state.registrationDraft.pendingKeyBundle.blockchainPair = null;
      }

      authorizeSession({
        login: state.registrationDraft.login,
        sessionId: state.registrationDraft.sessionId,
        storagePwd: state.registrationDraft.storagePwd,
      });

      state.loginDraft.login = state.registrationDraft.login;
      state.loginDraft.password = '';
      state.registrationDraft.flowType = '';
      state.registrationDraft.password = '';
      state.registrationDraft.storagePwd = '';
      state.registrationDraft.sessionId = '';
      state.registrationDraft.pendingKeyBundle = null;
      state.registrationDraft.pendingSessionMaterial = null;

      await refreshSessions();
      setAuthInfo(isLoginFlow
        ? `Ключи сохранены. Вы вошли как @${state.registrationDraft.login}. Далее откройте вкладку «Каналы».`
        : `Ключи сохранены. Регистрация завершена для @${state.registrationDraft.login}. Далее откройте вкладку «Каналы».`);
      navigate('profile-view');
    } catch (error) {
      const message = toUserMessage(error, 'Не удалось сохранить ключи на устройстве.');
      setAuthError(message);
      status.textContent = message;
      status.style.display = '';
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
