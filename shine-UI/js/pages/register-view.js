import { renderHeader } from '../components/header.js?v=20260406221807';
import { authService, clearAuthMessages, state } from '../state.js?v=20260406221807';

export const pageMeta = { id: 'register-view', title: 'Зарегистрироваться', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  clearAuthMessages();

  const form = document.createElement('div');
  form.className = 'card stack';

  const loginInput = document.createElement('input');
  loginInput.className = 'input';
  loginInput.type = 'text';
  loginInput.value = state.registrationDraft.login;
  loginInput.placeholder = 'Введите логин';

  const passwordInput = document.createElement('input');
  passwordInput.className = 'input';
  passwordInput.type = 'password';
  passwordInput.value = state.registrationDraft.password;
  passwordInput.placeholder = 'Введите пароль';

  const statusText = document.createElement('p');
  statusText.className = 'meta-muted';
  statusText.textContent = 'Проверка логина: не выполнена';

  const checkButton = document.createElement('button');
  checkButton.className = 'ghost-btn';
  checkButton.type = 'button';
  checkButton.textContent = 'Проверить логин';

  async function runAvailabilityCheck() {
    const login = loginInput.value.trim();
    if (!login) {
      statusText.textContent = 'Введите логин';
      return false;
    }

    checkButton.disabled = true;
    checkButton.textContent = 'Проверка...';
    try {
      await authService.reconnect(state.entrySettings.shineServer);
      const isFree = await authService.ensureLoginFree(login);
      statusText.textContent = isFree ? 'Логин свободен ✅' : 'Логин уже занят ❌';
      statusText.className = isFree ? 'is-available' : 'is-unavailable';
      return isFree;
    } catch (error) {
      statusText.textContent = error.message;
      statusText.className = 'is-unavailable';
      return false;
    } finally {
      checkButton.disabled = false;
      checkButton.textContent = 'Проверить логин';
    }
  }

  checkButton.addEventListener('click', runAvailabilityCheck);

  form.innerHTML = `
    <label class="stack"><span class="field-label">Логин</span></label>
    <label class="stack"><span class="field-label">Пароль</span></label>
  `;
  form.children[0].append(loginInput);
  form.children[1].append(passwordInput);
  form.append(checkButton, statusText);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('start-view'));

  const nextButton = document.createElement('button');
  nextButton.className = 'primary-btn';
  nextButton.type = 'button';
  nextButton.textContent = 'Далее';
  nextButton.addEventListener('click', async () => {
    const isFree = await runAvailabilityCheck();
    if (!isFree) {
      window.alert('Выберите свободный логин');
      return;
    }

    state.registrationDraft.login = loginInput.value.trim();
    state.registrationDraft.password = passwordInput.value;

    if (!state.registrationDraft.password) {
      window.alert('Введите пароль');
      return;
    }

    navigate('registration-payment-view');
  });

  actions.append(backButton, nextButton);

  screen.append(
    renderHeader({
      title: 'Зарегистрироваться',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    form,
    actions,
  );

  return screen;
}
