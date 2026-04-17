import { renderHeader } from '../components/header.js';
import { profile } from '../mock-data.js';
import { state } from '../state.js';
import {
  PROFILE_GENDER_FEMALE,
  PROFILE_GENDER_MALE,
  PROFILE_GENDER_UNKNOWN,
  loadProfileSnapshot,
  saveProfileGender,
  saveProfileParamBlock,
  saveProfileToggle,
} from '../services/user-profile-params.js';
import { buildIdentityLines } from '../services/user-connections.js';

export const pageMeta = { id: 'profile-view', title: 'Профиль' };

function toggleText(enabled) {
  return enabled ? 'Yes' : 'No';
}

function showLocalErrorAlert(prefix, error) {
  const message = error?.message || 'Неизвестная ошибка';
  const stack = error?.stack ? `\n\nStack:\n${error.stack}` : '';
  window.alert(`${prefix}: ${message}${stack}`);
}

function genderLabel(value) {
  if (value === PROFILE_GENDER_MALE) return 'Мужской';
  if (value === PROFILE_GENDER_FEMALE) return 'Женский';
  return 'Не указан';
}

const GENDER_OPTIONS = Object.freeze([
  { value: PROFILE_GENDER_MALE, label: 'Мужской' },
  { value: PROFILE_GENDER_FEMALE, label: 'Женский' },
  { value: PROFILE_GENDER_UNKNOWN, label: 'Не указан' },
]);

function escapeHtml(text) {
  return String(text || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function render({ navigate }) {
  const login = state.session.login || profile.login;

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Профиль',
      rightActions: [
        { label: 'Кошелёк', onClick: () => navigate('wallet-view') },
        { label: 'Настройки', onClick: () => navigate('settings-view') },
      ],
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';

  const topRow = document.createElement('div');
  topRow.className = 'row';
  topRow.innerHTML = `
    <div class="row" style="gap:12px; align-items:center;">
      <div class="avatar large">${profile.avatarInitials}</div>
      <div class="profile-identity-lines" data-profile-identity="true">
        <div class="profile-identity-line profile-identity-login">${String(login || '').trim() || 'unknown'}</div>
      </div>
    </div>
    <button class="primary-btn" type="button" data-reload="true">Обновить</button>
  `;

  const badgesRow = document.createElement('div');
  badgesRow.className = 'row';
  badgesRow.innerHTML = `
    <button class="badge profile-toggle-btn is-no" type="button" data-toggle="official">Официальный: No</button>
    <button class="badge profile-toggle-btn is-no" type="button" data-toggle="shine">Сияющий: No</button>
  `;

  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка параметров...';

  const listWrap = document.createElement('div');
  listWrap.className = 'stack profile-param-list';

  const reloadBtn = topRow.querySelector('[data-reload="true"]');
  const officialBtn = badgesRow.querySelector('[data-toggle="official"]');
  const shineBtn = badgesRow.querySelector('[data-toggle="shine"]');

  let currentFields = [];
  let currentToggles = [];
  let currentGender = PROFILE_GENDER_UNKNOWN;
  const identityEl = topRow.querySelector('[data-profile-identity="true"]');

  function openGenderPickerModal(initialGender) {
    const root = document.getElementById('modal-root');
    if (!root) return Promise.resolve(null);
    root.innerHTML = '';

    const selected = GENDER_OPTIONS.some((item) => item.value === initialGender)
      ? initialGender
      : PROFILE_GENDER_UNKNOWN;

    root.innerHTML = `
      <div class="modal" id="profile-gender-modal">
        <div class="modal-card stack">
          <h3 class="modal-title">Выбор пола</h3>
          <p class="meta-muted">Выберите значение профиля из списка:</p>
          <select class="input profile-gender-select" id="profile-gender-select">
            ${GENDER_OPTIONS.map((item) => (
              `<option value="${item.value}" ${item.value === selected ? 'selected' : ''}>${item.label}</option>`
            )).join('')}
          </select>
          <div class="form-actions-grid">
            <button class="secondary-btn" id="profile-gender-cancel" type="button">Отмена</button>
            <button class="primary-btn" id="profile-gender-save" type="button">Сохранить</button>
          </div>
        </div>
      </div>
    `;

    return new Promise((resolve) => {
      const modal = root.querySelector('#profile-gender-modal');
      const selectEl = root.querySelector('#profile-gender-select');
      const saveEl = root.querySelector('#profile-gender-save');
      const cancelEl = root.querySelector('#profile-gender-cancel');
      if (!(modal instanceof HTMLElement) || !(selectEl instanceof HTMLSelectElement)) {
        root.innerHTML = '';
        resolve(null);
        return;
      }

      const close = (value = null) => {
        root.innerHTML = '';
        resolve(value);
      };

      modal.addEventListener('click', (event) => {
        if (event.target === modal) close(null);
      });
      cancelEl?.addEventListener('click', () => close(null));
      saveEl?.addEventListener('click', () => close(selectEl.value || PROFILE_GENDER_UNKNOWN));
      window.setTimeout(() => selectEl.focus(), 0);
    });
  }

  function syncIdentity() {
    if (!identityEl) return;
    const firstName = currentFields.find((field) => field.key === 'first_name')?.value || '';
    const lastName = currentFields.find((field) => field.key === 'last_name')?.value || '';
    const lines = buildIdentityLines({ login, firstName, lastName });
    identityEl.innerHTML = lines.map((line, idx) => (
      `<div class="profile-identity-line${idx === lines.length - 1 ? ' profile-identity-login' : ''}">${escapeHtml(line)}</div>`
    )).join('');
  }

  function updateToggleButton(button, prefix, enabled) {
    button.textContent = `${prefix}: ${toggleText(enabled)}`;
    button.classList.remove('is-no', 'is-yes-official', 'is-yes-shine');

    if (!enabled) {
      button.classList.add('is-no');
      return;
    }

    if (prefix === 'Официальный') {
      button.classList.add('is-yes-official');
    } else {
      button.classList.add('is-yes-shine');
    }
  }

  function updateTogglesUi() {
    const official = currentToggles.find((item) => item.key === 'official') || { enabled: false };
    const shine = currentToggles.find((item) => item.key === 'shine') || { enabled: false };
    updateToggleButton(officialBtn, 'Официальный', official.enabled);
    updateToggleButton(shineBtn, 'Сияющий', shine.enabled);
  }

  function updateGenderUi() {
    const genderValueEl = listWrap.querySelector('[data-gender-value]');
    if (!genderValueEl) return;
    genderValueEl.textContent = genderLabel(currentGender);
  }

  function openFieldEditModal({ label, value, placeholder = '' }) {
    const root = document.getElementById('modal-root');
    if (!root) return Promise.resolve(null);
    root.innerHTML = `
      <div class="modal" id="profile-field-edit-modal">
        <div class="modal-card stack">
          <h3 class="modal-title">Изменить: ${escapeHtml(label)}</h3>
          <input
            id="profile-field-edit-input"
            class="input"
            type="text"
            maxlength="300"
            placeholder="${escapeHtml(placeholder || `Введите ${label.toLowerCase()}`)}"
            value="${escapeHtml(String(value || ''))}"
          />
          <div class="form-actions-grid">
            <button class="secondary-btn" id="profile-field-edit-cancel" type="button">Отмена</button>
            <button class="primary-btn" id="profile-field-edit-save" type="button">Сохранить</button>
          </div>
        </div>
      </div>
    `;

    return new Promise((resolve) => {
      const modal = root.querySelector('#profile-field-edit-modal');
      const inputEl = root.querySelector('#profile-field-edit-input');
      const saveEl = root.querySelector('#profile-field-edit-save');
      const cancelEl = root.querySelector('#profile-field-edit-cancel');
      if (!(modal instanceof HTMLElement) || !(inputEl instanceof HTMLInputElement)) {
        root.innerHTML = '';
        resolve(null);
        return;
      }

      const close = (nextValue = null) => {
        root.innerHTML = '';
        resolve(nextValue);
      };

      modal.addEventListener('click', (event) => {
        if (event.target === modal) close(null);
      });
      cancelEl?.addEventListener('click', () => close(null));
      saveEl?.addEventListener('click', () => close(inputEl.value));
      inputEl.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          close(inputEl.value);
        }
      });
      window.setTimeout(() => {
        inputEl.focus();
        inputEl.selectionStart = inputEl.value.length;
        inputEl.selectionEnd = inputEl.value.length;
      }, 0);
    });
  }

  function renderFields(fields) {
    listWrap.innerHTML = '';
    fields.forEach((field) => {
      const row = document.createElement('div');
      row.className = 'card profile-param-item row';
      const value = String(field.value || '').trim() || 'не заполнено';
      const isNameField = field.key === 'first_name' || field.key === 'last_name';
      const valueClass = isNameField ? 'profile-param-value profile-param-value-small' : 'profile-param-value';
      row.innerHTML = `
        <div class="${valueClass}"><b>${field.label}</b>: ${escapeHtml(value)}</div>
        <button class="ghost-btn" type="button" data-edit-field="${field.key}">Изменить</button>
      `;
      listWrap.append(row);

      if (field.key === 'last_name') {
        const genderRow = document.createElement('div');
        genderRow.className = 'card profile-param-item row';
        genderRow.innerHTML = `
          <div class="profile-param-value"><b>Пол</b>: <span data-gender-value>${escapeHtml(genderLabel(currentGender))}</span></div>
          <button class="ghost-btn" type="button" data-edit-gender="true">Изменить</button>
        `;
        listWrap.append(genderRow);
      }
    });
  }

  async function refreshProfileSnapshot() {
    status.className = 'status-line';
    status.textContent = 'Загрузка параметров...';
    reloadBtn.disabled = true;
    officialBtn.disabled = true;
    shineBtn.disabled = true;
    const genderActionBtn = listWrap.querySelector('[data-edit-gender="true"]');
    if (genderActionBtn instanceof HTMLButtonElement) {
      genderActionBtn.disabled = true;
    }

    try {
      const snapshot = await loadProfileSnapshot(login);
      currentFields = snapshot.fields;
      currentToggles = snapshot.toggles;
      currentGender = snapshot.gender || PROFILE_GENDER_UNKNOWN;

      syncIdentity();
      renderFields(currentFields);
      updateTogglesUi();
      updateGenderUi();

      status.className = 'status-line is-available';
      status.textContent = 'Актуальные параметры загружены.';
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось загрузить параметры: ${error.message || 'ошибка сети'}`;
      showLocalErrorAlert('Ошибка загрузки параметров профиля', error);
    } finally {
      reloadBtn.disabled = false;
      officialBtn.disabled = false;
      shineBtn.disabled = false;
      const genderActionBtnAfter = listWrap.querySelector('[data-edit-gender="true"]');
      if (genderActionBtnAfter instanceof HTMLButtonElement) {
        genderActionBtnAfter.disabled = false;
      }
    }
  }

  async function onToggleClick(toggleKey) {
    const toggle = currentToggles.find((item) => item.key === toggleKey) || { enabled: false };
    const nextEnabled = !toggle.enabled;
    const title = toggleKey === 'official' ? 'официальный' : 'сияющий';

    const confirmed = window.confirm(
      `Хотите изменить «${title}» на ${toggleText(nextEnabled)}?\n` +
      'Будет создана запись в блокчейне.',
    );
    if (!confirmed) return;

    status.className = 'status-line';
    status.textContent = 'Сохранение в блокчейн...';

    try {
      await saveProfileToggle(login, toggleKey, nextEnabled);
      await refreshProfileSnapshot();
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось изменить ${toggleKey}: ${error.message || 'ошибка сети'}`;
      showLocalErrorAlert(`Ошибка изменения ${toggleKey}`, error);
    }
  }

  async function onEditFieldClick(fieldKey) {
    const field = currentFields.find((item) => item.key === fieldKey);
    if (!field) return;

    const entered = await openFieldEditModal({
      label: field.label,
      value: field.value || '',
      placeholder: field.placeholder || '',
    });
    if (entered === null) return;

    status.className = 'status-line';
    status.textContent = 'Сохранение в блокчейн...';

    try {
      await saveProfileParamBlock(login, field.key, entered);
      await refreshProfileSnapshot();
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось изменить ${field.key}: ${error.message || 'ошибка сети'}`;
      showLocalErrorAlert(`Ошибка изменения ${field.key}`, error);
    }
  }

  async function onGenderClick() {
    const nextGender = await openGenderPickerModal(currentGender);
    if (!nextGender) return;

    status.className = 'status-line';
    status.textContent = 'Сохранение в блокчейн...';

    try {
      await saveProfileGender(login, nextGender);
      await refreshProfileSnapshot();
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Не удалось изменить пол: ${error.message || 'ошибка сети'}`;
      showLocalErrorAlert('Ошибка изменения пола', error);
    }
  }

  listWrap.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (target.dataset.editGender === 'true') {
      onGenderClick();
      return;
    }
    const fieldKey = target.dataset.editField;
    if (!fieldKey) return;
    onEditFieldClick(fieldKey);
  });

  reloadBtn.addEventListener('click', refreshProfileSnapshot);
  officialBtn.addEventListener('click', () => onToggleClick('official'));
  shineBtn.addEventListener('click', () => onToggleClick('shine'));

  card.append(topRow, badgesRow, status, listWrap);
  screen.append(card);

  refreshProfileSnapshot();

  return screen;
}
