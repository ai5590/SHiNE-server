import { renderHeader } from '../components/header.js?v=20260407105357';
import { profile } from '../mock-data.js?v=20260407105357';
import { state } from '../state.js?v=20260407105357';

export const pageMeta = { id: 'device-qr-view', title: 'Показать QR-код' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const selectedKeys = [];
  if (state.deviceConnect.root) selectedKeys.push('root key');
  if (state.deviceConnect.blockchain) selectedKeys.push('blockchain key');
  if (state.deviceConnect.device) selectedKeys.push('device key');

  screen.append(
    renderHeader({
      title: 'Показать QR-код',
      leftAction: { label: '←', onClick: () => navigate('connect-device-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack qr-card';
  card.innerHTML = `
    <img class="qr-image" src="img/device-qr-64.svg" width="64" height="64" alt="QR-код для подключения" />
    <p class="meta-muted">Логин пользователя: ${profile.login}</p>
    <p class="meta-muted">Передаваемые ключи: ${selectedKeys.join(', ')}</p>
    <button class="primary-btn" type="button" id="qr-ok">OK</button>
  `;

  card.querySelector('#qr-ok').addEventListener('click', () => navigate('connect-device-view'));

  screen.append(card);
  return screen;
}
