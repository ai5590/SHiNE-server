import { renderHeader } from '../components/header.js';
import { authService, state } from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';
import {
  channelNameErrorText,
  normalizeChannelDescription,
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

function validateDescription(value) {
  const normalized = normalizeChannelDescription(value);
  const bytes = new TextEncoder().encode(normalized).length;
  if (bytes > 200) {
    return { ok: false, normalized, bytes, error: 'Описание слишком длинное: максимум 200 байт UTF-8.' };
  }
  return { ok: true, normalized, bytes, error: '' };
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--add';

  screen.append(
    renderHeader({
      title: 'Создать канал',
      leftAction: { label: '<', onClick: () => navigate('channels-list') },
    }),
  );

  const form = document.createElement('form');
  form.className = 'card stack';
  form.innerHTML = `
    <strong class="channel-head-title">Создание канала</strong>
    <p class="channel-head-meta">Можно использовать кириллицу, латиницу, цифры, пробел, _ и -.</p>
    <p class="channel-head-meta">Длина названия: от 3 до 32 символов. Название уникально во всей системе.</p>

    <label for="channel-name">Название канала</label>
    <input id="channel-name" class="input" maxlength="64" placeholder="Например: Поток силы" required />
    <div id="channel-name-error" class="meta-muted inline-error"></div>

    <label for="channel-description">Описание канала (необязательно)</label>
    <textarea id="channel-description" class="input" rows="4" maxlength="400" placeholder="Коротко о канале, до 200 байт UTF-8"></textarea>
    <div class="meta-muted" id="channel-description-counter">0 / 200 байт</div>
    <div id="channel-description-error" class="meta-muted inline-error"></div>

    <div id="channel-create-error" class="meta-muted inline-error"></div>
    <div class="form-actions-grid">
      <button type="button" class="secondary-btn" id="cancel-create-channel">Отмена</button>
      <button type="submit" class="primary-btn" id="submit-create-channel">Создать</button>
    </div>
  `;

  const nameEl = form.querySelector('#channel-name');
  const descriptionEl = form.querySelector('#channel-description');
  const nameErrorEl = form.querySelector('#channel-name-error');
  const descriptionErrorEl = form.querySelector('#channel-description-error');
  const descriptionCounterEl = form.querySelector('#channel-description-counter');
  const errorEl = form.querySelector('#channel-create-error');
  const submitEl = form.querySelector('#submit-create-channel');
  const cancelEl = form.querySelector('#cancel-create-channel');

  let submitInFlight = false;

  const setBusy = (busy) => {
    submitInFlight = !!busy;
    submitEl.disabled = submitInFlight;
    cancelEl.disabled = submitInFlight;
    nameEl.disabled = submitInFlight;
    descriptionEl.disabled = submitInFlight;
    submitEl.textContent = submitInFlight ? 'Создаём...' : 'Создать';
  };

  const updateValidation = () => {
    const nameCheck = validateChannelDisplayName(nameEl.value);
    const descriptionCheck = validateDescription(descriptionEl.value);

    nameErrorEl.textContent = nameCheck.ok ? '' : channelNameErrorText(nameCheck.code);
    descriptionErrorEl.textContent = descriptionCheck.error;

    const descLength = Number(descriptionCheck.bytes || 0);
    descriptionCounterEl.textContent = `${descLength} / 200 байт`;

    const ok = nameCheck.ok && descriptionCheck.ok;
    submitEl.disabled = submitInFlight || !ok;

    return {
      ok,
      name: nameCheck.normalized,
      description: descriptionCheck.normalized,
    };
  };

  nameEl.addEventListener('input', updateValidation);
  descriptionEl.addEventListener('input', updateValidation);

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
      const created = await authService.addBlockCreateChannel({
        login,
        storagePwd,
        channelName: normalizeChannelDisplayName(check.name),
        channelDescription: normalizeChannelDescription(check.description),
      });

      const baseMessage = `Канал "${normalizeChannelDisplayName(check.name)}" создан.`;
      const successMessage = created?.usedLegacyDescriptionFallback && created?.savedDescriptionViaUserParam
        ? `${baseMessage} Описание сохранено через блок параметра.`
        : baseMessage;
      persistCreateSuccessFlash(successMessage);
      navigate('channels-list');
    } catch (error) {
      errorEl.textContent = toUserMessage(error, 'Не удалось создать канал.');
      setBusy(false);
      updateValidation();
    }
  });

  cancelEl.addEventListener('click', () => navigate('channels-list'));

  screen.append(form);
  nameEl.focus();
  updateValidation();
  return screen;
}
