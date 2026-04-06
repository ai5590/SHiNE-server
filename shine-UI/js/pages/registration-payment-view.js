import { renderHeader } from '../components/header.js?v=20260405171816';
import {
  authService,
  refreshRegistrationBalance,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js?v=20260405171816';

export const pageMeta = { id: 'registration-payment-view', title: 'Оплата регистрации', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const card = document.createElement('div');
  card.className = 'card stack';

  const walletValue = document.createElement('input');
  walletValue.className = 'input';
  walletValue.type = 'text';
  walletValue.value = state.registrationPayment.walletAddress;
  walletValue.addEventListener('input', () => {
    state.registrationPayment.walletAddress = walletValue.value;
  });

  const walletRow = document.createElement('div');
  walletRow.className = 'inline-input-row';

  const copyButton = document.createElement('button');
  copyButton.className = 'ghost-btn';
  copyButton.type = 'button';
  copyButton.textContent = 'Скопировать номер';
  copyButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(walletValue.value);
      copyButton.textContent = 'Скопировано';
      window.setTimeout(() => {
        copyButton.textContent = 'Скопировать номер';
      }, 1500);
    } catch {
      window.alert('Не удалось скопировать номер кошелька.');
    }
  });

  walletRow.append(walletValue, copyButton);

  const balanceRow = document.createElement('div');
  balanceRow.className = 'row wrap-row';

  const balanceValue = document.createElement('strong');
  balanceValue.textContent = `${state.registrationPayment.balanceSOL} SOL`;

  const refreshButton = document.createElement('button');
  refreshButton.className = 'square-btn';
  refreshButton.type = 'button';
  refreshButton.textContent = '↻';
  refreshButton.title = 'Обновить';
  refreshButton.addEventListener('click', () => {
    balanceValue.textContent = `${refreshRegistrationBalance()} SOL`;
  });

  balanceRow.append(balanceValue, refreshButton);

  const topupButton = document.createElement('button');
  topupButton.className = 'ghost-btn';
  topupButton.type = 'button';
  topupButton.textContent = 'Пополнить счет';
  topupButton.addEventListener('click', () => navigate('topup-view'));

  const submitButton = document.createElement('button');
  submitButton.className = 'primary-btn';
  submitButton.type = 'button';
  submitButton.textContent = 'Зарегистрироваться';
  submitButton.addEventListener('click', async () => {
    try {
      submitButton.disabled = true;
      submitButton.textContent = 'Регистрация...';

      await authService.reconnect(state.entrySettings.shineServer);
      const result = await authService.registerUser(state.registrationDraft.login, state.registrationDraft.password);
      state.registrationDraft.flowType = 'registration';
      state.registrationDraft.sessionId = result.sessionId;
      state.registrationDraft.storagePwd = result.storagePwd;
      state.registrationDraft.pendingKeyBundle = result.keyBundle;
      state.registrationDraft.pendingSessionMaterial = result.sessionMaterial;

      setAuthInfo(`Отлично, вы зарегистрировались: ${result.login}`);
      window.alert('Отлично, вы зарегистрировались');
      navigate('registration-keys-view');
    } catch (error) {
      setAuthError(error.message);
      window.alert(error.message);
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = 'Зарегистрироваться';
    }
  });

  card.innerHTML = `
    <p class="auth-copy">Для регистрации в Solana нужно заплатить 0,01 SOL (примерно 1 доллар).</p>
    <label class="stack"><span class="field-label">Номер кошелька</span></label>
    <div class="stack">
      <span class="field-label">Баланс</span>
    </div>
  `;
  card.children[1].append(walletRow);
  card.children[2].append(balanceRow);
  card.append(topupButton, submitButton);

  screen.append(
    renderHeader({
      title: 'Оплата регистрации',
      leftAction: { label: '←', onClick: () => navigate('register-view') },
    }),
    card,
  );

  return screen;
}
