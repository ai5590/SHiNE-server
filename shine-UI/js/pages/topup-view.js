import { renderHeader } from '../components/header.js?v=20260407105357';
import { state } from '../state.js?v=20260407105357';

export const pageMeta = { id: 'topup-view', title: 'Пополнение счета', showAppChrome: false };

const BUY_LINK = 'https://www.moonpay.com/buy/sol';

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const walletValue = document.createElement('input');
  walletValue.className = 'input';
  walletValue.type = 'text';
  walletValue.value = state.registrationPayment.walletAddress;
  walletValue.readOnly = true;
  walletValue.style.fontSize = '13px';

  const copyButton = document.createElement('button');
  copyButton.className = 'ghost-btn';
  copyButton.type = 'button';
  copyButton.textContent = 'Скопировать';
  copyButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(walletValue.value);
      copyButton.textContent = 'Скопировано';
      window.setTimeout(() => {
        copyButton.textContent = 'Скопировать';
      }, 1500);
    } catch {
      window.alert('Не удалось скопировать номер кошелька.');
    }
  });

  const walletRow = document.createElement('div');
  walletRow.className = 'inline-input-row';
  walletRow.append(walletValue, copyButton);

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <p class="auth-copy">Для пополнения счета скопируйте номер кошелька.</p>
    <div class="stack" style="gap:6px;">
      <p class="meta-muted">1. Пополните через любое свое приложение, используя этот кошелек в сети Solana.</p>
      <p class="meta-muted">2. Либо откройте страницу для покупки SOL.</p>
      <p class="meta-muted">3. Либо используйте кнопку «Тестовое пополнение» (работает в тестовой Solana).</p>
    </div>
    <a class="link-card" href="${BUY_LINK}" target="_blank" rel="noreferrer">Открыть страницу покупки SOL</a>
    <div class="card stack" style="padding:12px; max-width:320px;">
      <div class="field-label" style="margin-bottom:6px;">Кошелёк для пополнения</div>
    </div>
  `;
  card.children[3].append(walletRow);

  const testButton = document.createElement('button');
  testButton.className = 'ghost-btn';
  testButton.type = 'button';
  testButton.textContent = 'Тестовое пополнение';
  testButton.addEventListener('click', () => {
    state.registrationPayment.balanceSOL = '0.0250';
    window.alert('Тестовое пополнение выполнено. Баланс обновлён.');
  });

  const backButton = document.createElement('button');
  backButton.className = 'primary-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('registration-payment-view'));

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';
  actions.append(testButton, backButton);

  screen.append(
    renderHeader({
      title: 'Пополнение счета',
      leftAction: { label: '←', onClick: () => navigate('registration-payment-view') },
    }),
    card,
    actions,
  );

  return screen;
}
