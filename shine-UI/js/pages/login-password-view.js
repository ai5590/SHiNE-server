import { renderHeader } from '../components/header.js';
import {
  authService,
  clearAuthMessages,
  setAuthBusy,
  setAuthError,
  state,
} from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';

export const pageMeta = { id: 'login-password-view', title: 'Войти по логину', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  clearAuthMessages();

  const form = document.createElement('div');
  form.className = 'card stack';

  const loginInput = document.createElement('input');
  loginInput.className = 'input';
  loginInput.type = 'text';
  loginInput.value = state.loginDraft.login;
  loginInput.placeholder = 'Введите логин';

  const passwordInput = document.createElement('input');
  passwordInput.className = 'input';
  passwordInput.type = 'password';
  passwordInput.value = state.loginDraft.password;
  passwordInput.placeholder = 'Введите пароль';

  const hint = document.createElement('p');
  hint.className = 'meta-muted';
  hint.textContent = 'Введите логин и пароль. На следующем шаге сохраните ключи на устройстве.';

  const status = document.createElement('p');
  status.className = 'status-line is-unavailable';
  status.style.display = 'none';

  form.innerHTML = `
    <label class="stack"><span class="field-label">Логин</span></label>
    <label class="stack"><span class="field-label">Пароль</span></label>
  `;
  form.children[0].append(loginInput);
  form.children[1].append(passwordInput);
  form.append(hint, status);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('login-view'));

  const enterButton = document.createElement('button');
  enterButton.className = 'primary-btn';
  enterButton.type = 'button';
  enterButton.textContent = 'Войти';
  enterButton.addEventListener('click', async () => {
    status.style.display = 'none';
    state.loginDraft.login = loginInput.value.trim();
    state.loginDraft.password = passwordInput.value;

    if (!state.loginDraft.login || !state.loginDraft.password) {
      status.textContent = 'Введите логин и пароль.';
      status.style.display = '';
      return;
    }

    setAuthBusy(true);
    setAuthError('');
    enterButton.disabled = true;
    enterButton.textContent = 'Входим...';

    try {
      await authService.reconnect(state.entrySettings.shineServer);
      const result = await authService.createSessionForExistingUser(state.loginDraft.login, state.loginDraft.password);
      state.registrationDraft.flowType = 'login';
      state.registrationDraft.login = result.login;
      state.registrationDraft.password = state.loginDraft.password;
      state.registrationDraft.sessionId = result.sessionId;
      state.registrationDraft.storagePwd = result.storagePwd;
      state.registrationDraft.pendingKeyBundle = result.keyBundle;
      state.registrationDraft.pendingSessionMaterial = result.sessionMaterial;
      navigate('registration-keys-view');
    } catch (error) {
      const message = toUserMessage(error, 'Не удалось выполнить вход.');
      setAuthError(message);
      status.textContent = message;
      status.style.display = '';
    } finally {
      setAuthBusy(false);
      enterButton.disabled = false;
      enterButton.textContent = 'Войти';
    }
  });

  actions.append(backButton, enterButton);

  screen.append(
    renderHeader({
      title: 'Войти по логину',
      leftAction: { label: '←', onClick: () => navigate('login-view') },
    }),
    form,
    actions,
  );

  return screen;
}
