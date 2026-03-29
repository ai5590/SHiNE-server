import { renderHeader } from '../components/header.js?v=20260327192619';
import {
  authService,
  authorizeSession,
  clearAuthMessages,
  refreshSessions,
  setAuthBusy,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js?v=20260327192619';

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
  hint.textContent = 'Root/dev/bch ключи вычисляются из пароля через SHA-256, storagePwd каждый вход приходит с сервера.';

  form.innerHTML = `
    <label class="stack"><span class="field-label">Логин</span></label>
    <label class="stack"><span class="field-label">Пароль</span></label>
  `;
  form.children[0].append(loginInput);
  form.children[1].append(passwordInput);
  form.append(hint);

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
    state.loginDraft.login = loginInput.value.trim();
    state.loginDraft.password = passwordInput.value;

    if (!state.loginDraft.login || !state.loginDraft.password) {
      window.alert('Введите логин и пароль');
      return;
    }

    setAuthBusy(true);
    setAuthError('');
    enterButton.disabled = true;
    enterButton.textContent = 'Входим...';

    try {
      await authService.reconnect(state.entrySettings.shineServer);
      const result = await authService.createSessionForExistingUser(state.loginDraft.login, state.loginDraft.password);
      await authService.persistSessionMaterial(state.loginDraft.login, result.sessionMaterial);
      authorizeSession(result);
      await refreshSessions();
      setAuthInfo('Успешный вход выполнен.');
      navigate('profile-view');
    } catch (error) {
      setAuthError(error.message);
      window.alert(error.message);
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
