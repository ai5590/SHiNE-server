import { renderHeader } from '../components/header.js';
import { authService, state } from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';
import {
  channelNameErrorText,
  normalizeChannelDisplayName,
  validateChannelDisplayName,
} from '../services/channel-name-rules.js';

export const pageMeta = { id: 'add-channel-view', title: 'Создать канал' };

const CREATE_CHANNEL_FLASH_KEY = 'shine-channels-create-success';

function persistCreateSuccessFlash(message) {
  try {
    sessionStorage.setItem(CREATE_CHANNEL_FLASH_KEY, String(message || '').trim());
  } catch {
    // ignore storage errors
  }
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--add';

  screen.append(
    renderHeader({
      title: 'Создать канал',
      leftAction: { label: '<', onClick: () => navigate('channels-list') },
    })
  );

  const form = document.createElement('form');
  form.className = 'card stack';
  form.innerHTML = `
    <strong class="channel-head-title">Создание канала</strong>
    <p class="channel-head-meta">Можно использовать кириллицу, латиницу, цифры, пробел, _ и -.</p>
    <p class="channel-head-meta">Длина: от 3 до 32 символов. Название уникально во всей системе.</p>
    <label for="channel-name">Название канала</label>
    <input id="channel-name" class="input" maxlength="64" placeholder="Например: Поток силы" required />
    <div id="channel-create-error" class="meta-muted inline-error"></div>
    <div class="form-actions-grid">
      <button type="button" class="secondary-btn" id="cancel-create-channel">Отмена</button>
      <button type="submit" class="primary-btn" id="submit-create-channel">Создать</button>
    </div>
  `;

  const inputEl = form.querySelector('#channel-name');
  const errorEl = form.querySelector('#channel-create-error');
  const submitEl = form.querySelector('#submit-create-channel');
  const cancelEl = form.querySelector('#cancel-create-channel');

  let submitInFlight = false;

  const setBusy = (busy) => {
    submitInFlight = !!busy;
    submitEl.disabled = submitInFlight;
    cancelEl.disabled = submitInFlight;
    inputEl.disabled = submitInFlight;
    submitEl.textContent = submitInFlight ? 'Создаём...' : 'Создать';
  };

  const updateValidation = () => {
    const check = validateChannelDisplayName(inputEl.value);
    if (!check.ok) {
      errorEl.textContent = channelNameErrorText(check.code);
    } else {
      errorEl.textContent = '';
    }
    submitEl.disabled = submitInFlight || !check.ok;
    return check;
  };

  inputEl.addEventListener('input', () => {
    updateValidation();
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (submitInFlight) return;

    const login = state.session.login;
    const storagePwd = state.session.storagePwdInMemory;
    if (!login || !storagePwd) {
      errorEl.textContent = 'Сессия недействительна. Выполните вход заново.';
      return;
    }

    const check = updateValidation();
    if (!check.ok) return;

    setBusy(true);
    errorEl.textContent = '';

    try {
      const channelName = normalizeChannelDisplayName(check.normalized);
      await authService.addBlockCreateChannel({
        login,
        storagePwd,
        channelName,
      });

      persistCreateSuccessFlash(`Канал "${channelName}" создан.`);
      navigate('channels-list');
    } catch (error) {
      errorEl.textContent = toUserMessage(error, 'Не удалось создать канал.');
      setBusy(false);
      const checkAfterError = validateChannelDisplayName(inputEl.value);
      submitEl.disabled = submitInFlight || !checkAfterError.ok;
    }
  });

  cancelEl.addEventListener('click', () => {
    navigate('channels-list');
  });

  screen.append(form);
  if (inputEl) {
    inputEl.focus();
    updateValidation();
  }
  return screen;
}
