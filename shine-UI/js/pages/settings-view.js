import { renderHeader } from '../components/header.js';

export const pageMeta = { id: 'settings-view', title: 'Настройки' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Настройки',
      leftAction: { label: '←', onClick: () => navigate('profile-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <button class="text-btn" type="button" id="settings-device">Устройства</button>
    <button class="text-btn" type="button" id="settings-servers">Настройки серверов</button>
    <button class="text-btn" type="button" id="settings-language">Язык / Language</button>
    <button class="text-btn" type="button" id="settings-app-log">Лог приложения</button>
  `;

  card.querySelector('#settings-device').addEventListener('click', () => navigate('device-view'));
  card.querySelector('#settings-servers').addEventListener('click', () => navigate('server-settings-view'));
  card.querySelector('#settings-language').addEventListener('click', () => navigate('language-view'));
  card.querySelector('#settings-app-log').addEventListener('click', () => navigate('app-log-view'));

  screen.append(card);
  return screen;
}
