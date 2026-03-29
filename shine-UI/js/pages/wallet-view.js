import { renderHeader } from '../components/header.js?v=20260327192619';
import { wallet } from '../mock-data.js?v=20260327192619';

export const pageMeta = { id: 'wallet-view', title: 'Кошелёк' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';
  let statusText = 'Данные демонстрационные';

  const status = document.createElement('p');
  status.className = 'meta-muted';

  const updateStatus = (text) => {
    statusText = text;
    status.textContent = statusText;
  };

  screen.append(
    renderHeader({
      title: 'Кошелёк',
      leftAction: { label: '←', onClick: () => navigate('profile-view') },
    })
  );

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <div>
      <p class="meta-muted">Баланс</p>
      <h2 style="font-size:30px;">${wallet.balanceSOL} SOL</h2>
      <p class="meta-muted">Обновлено: ${wallet.updatedAt}</p>
    </div>
    <div class="card" style="padding:10px;">
      <p class="meta-muted" style="margin-bottom:6px;">Публичный адрес</p>
      <p style="font-size:13px; line-height:1.4; word-break:break-all;">${wallet.publicAddress}</p>
    </div>
  `;

  const actions = document.createElement('div');
  actions.className = 'stack';
  actions.innerHTML = `
    <div class="row">
      <button class="text-btn" id="copy-address">Копировать адрес</button>
      <button class="ghost-btn" id="refresh-balance">Обновить баланс</button>
    </div>
    <div class="row">
      <button class="primary-btn" id="send-sol" style="width:100%;">Перевести</button>
      <button class="primary-btn" id="topup-sol" style="width:100%;">Пополнить</button>
    </div>
  `;

  actions.querySelector('#copy-address').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(wallet.publicAddress);
      updateStatus('Адрес скопирован в буфер обмена');
    } catch {
      updateStatus('Не удалось скопировать в этом браузере');
    }
  });

  actions.querySelector('#refresh-balance').addEventListener('click', () => {
    updateStatus(`Баланс обновлен: ${wallet.balanceSOL} SOL`);
  });

  actions.querySelector('#send-sol').addEventListener('click', () => {
    updateStatus('Демо-функция: перевод будет добавлен позже');
  });

  actions.querySelector('#topup-sol').addEventListener('click', () => {
    updateStatus('Демо-функция: пополнение будет добавлено позже');
  });

  updateStatus(statusText);

  screen.append(card, actions, status);
  return screen;
}
