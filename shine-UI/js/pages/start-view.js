import { clearStartHint, state } from '../state.js';

export const pageMeta = { id: 'start-view', title: 'Старт', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'auth-screen stack';

  const logo = document.createElement('img');
  logo.className = 'auth-logo';
  logo.src = './img/logo.jpg';
  logo.alt = 'Логотип Сияние';

  const title = document.createElement('h1');
  title.className = 'auth-brand';
  title.textContent = 'Сияние';

  const actions = document.createElement('div');
  actions.className = 'auth-actions';

  const loginButton = document.createElement('button');
  loginButton.className = 'primary-btn';
  loginButton.type = 'button';
  loginButton.textContent = 'Войти';
  loginButton.addEventListener('click', () => navigate('login-view'));

  const registerButton = document.createElement('button');
  registerButton.className = 'ghost-btn';
  registerButton.type = 'button';
  registerButton.textContent = 'Зарегистрироваться';
  registerButton.addEventListener('click', () => navigate('register-view'));

  const settingsButton = document.createElement('button');
  settingsButton.className = 'ghost-btn';
  settingsButton.type = 'button';
  settingsButton.textContent = 'Настройки';
  settingsButton.addEventListener('click', () => navigate('entry-settings-view'));

  actions.append(loginButton, registerButton, settingsButton);

  screen.append(logo, title);

  const help = document.createElement('div');
  help.className = 'card auth-status-card';
  help.innerHTML = `
    <strong>Локальный тест SHiNE</strong>
    <p class="meta-muted" style="margin-top:6px;">
      1) Локально: <code>?localWsPort=7071</code>; через tunnel: <code>?wsUrl=wss://.../ws</code>.<br />
      2) Зарегистрируйте пользователя A, затем пользователя B.<br />
      3) Войдите под A, создайте 2 канала и сообщения.<br />
      4) Войдите под B и проверьте каналы, лайк/анлайк и подписки/отписки.
    </p>
  `;
  screen.append(help);

  if (state.startHint) {
    const notice = document.createElement('div');
    notice.className = 'card auth-status-card';
    notice.textContent = state.startHint;
    screen.append(notice);
    clearStartHint();
  }

  screen.append(actions);
  return screen;
}
