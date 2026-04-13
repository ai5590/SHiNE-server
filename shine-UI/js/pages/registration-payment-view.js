import { renderHeader } from '../components/header.js';
import {
  authService,
  refreshRegistrationBalance,
  setAuthError,
  setAuthInfo,
  state,
} from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';

export const pageMeta = { id: 'registration-payment-view', title: 'Оплата регистрации', showAppChrome: false };

const MIN_REGISTER_BALANCE_SOL = 0.01;

function parseBalanceSol(value) {
  const parsed = Number.parseFloat(String(value || '').replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : 0;
}

function getCryptoRuntimeState() {
  const hasCrypto = Boolean(globalThis.crypto);
  const hasGetRandomValues = Boolean(globalThis.crypto && typeof globalThis.crypto.getRandomValues === 'function');
  const hasSubtle = Boolean(globalThis.crypto && (globalThis.crypto.subtle || globalThis.crypto.webkitSubtle));
  const secureContext = window.isSecureContext === true;
  return { hasCrypto, hasGetRandomValues, hasSubtle, secureContext };
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const card = document.createElement('div');
  card.className = 'card stack';

  const status = document.createElement('p');
  status.className = 'status-line is-unavailable';
  status.style.display = 'none';

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
      status.className = 'status-line is-unavailable';
      status.textContent = 'Не удалось скопировать номер кошелька.';
      status.style.display = '';
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
    status.style.display = 'none';

    const balanceSol = parseBalanceSol(state.registrationPayment.balanceSOL);
    if (balanceSol < MIN_REGISTER_BALANCE_SOL) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Недостаточный баланс для регистрации: ${state.registrationPayment.balanceSOL} SOL. Нужно минимум ${MIN_REGISTER_BALANCE_SOL.toFixed(2)} SOL.`;
      status.style.display = '';
      return;
    }

    const cryptoState = getCryptoRuntimeState();
    if (!cryptoState.hasCrypto || !cryptoState.hasGetRandomValues || !cryptoState.hasSubtle) {
      status.className = 'status-line is-unavailable';
      status.textContent = 'Криптография браузера недоступна. Откройте приложение через HTTPS tunnel или localhost и повторите регистрацию.';
      status.style.display = '';
      return;
    }

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

      setAuthInfo(`Регистрация завершена. Вы вошли как @${result.login}. Далее откройте вкладку «Каналы».`);
      navigate('registration-keys-view');
    } catch (error) {
      const message = toUserMessage(error, 'Не удалось завершить регистрацию.');
      setAuthError(message);
      status.className = 'status-line is-unavailable';
      status.textContent = message;
      status.style.display = '';
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
  card.append(topupButton, submitButton, status);

  screen.append(
    renderHeader({
      title: 'Оплата регистрации',
      leftAction: { label: '←', onClick: () => navigate('register-view') },
    }),
    card,
  );

  return screen;
}
