import { clearStartHint } from '../state.js';

export const pageMeta = { id: 'start-view', title: 'Старт', showAppChrome: false };

export function render({ navigate }) {
  clearStartHint();

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
  screen.append(logo, title, actions);
  return screen;
}
