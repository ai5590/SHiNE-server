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

function parseGenderChoice(value) {
  const normalized = String(value || '').trim().toLowerCase();
  if (!normalized) return '';
  if (normalized === '1' || normalized === 'м' || normalized === 'муж' || normalized === 'мужской' || normalized === PROFILE_GENDER_MALE) {
    return PROFILE_GENDER_MALE;
  }
  if (normalized === '2' || normalized === 'ж' || normalized === 'жен' || normalized === 'женский' || normalized === PROFILE_GENDER_FEMALE) {
    return PROFILE_GENDER_FEMALE;
  }
  if (
    normalized === '3' ||
    normalized === 'н' ||
    normalized === 'не указан' ||
    normalized === 'неуказан' ||
    normalized === 'не указано' ||
    normalized === 'неизвестно' ||
    normalized === PROFILE_GENDER_UNKNOWN
  ) {
    return PROFILE_GENDER_UNKNOWN;
  }
  return '';
}

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

  const genderWrap = document.createElement('div');
  genderWrap.className = 'card row profile-param-item';
  genderWrap.innerHTML = `
    <div class="profile-param-value"><b>Пол</b>: <span data-gender-value>Не указан</span></div>
    <button class="ghost-btn" type="button" data-edit-gender="true">Выбрать</button>
  `;

  const listWrap = document.createElement('div');
  listWrap.className = 'stack profile-param-list';

  const reloadBtn = topRow.querySelector('[data-reload="true"]');
  const officialBtn = badgesRow.querySelector('[data-toggle="official"]');
  const shineBtn = badgesRow.querySelector('[data-toggle="shine"]');
  const genderValueEl = genderWrap.querySelector('[data-gender-value]');
  const genderBtn = genderWrap.querySelector('[data-edit-gender="true"]');

  let currentFields = [];
  let currentToggles = [];
  let currentGender = PROFILE_GENDER_UNKNOWN;
  const identityEl = topRow.querySelector('[data-profile-identity="true"]');

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
    if (!genderValueEl) return;
    genderValueEl.textContent = genderLabel(currentGender);
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
        <div class="${valueClass}"><b>${field.label}</b>: ${value}</div>
        <button class="ghost-btn" type="button" data-edit-field="${field.key}">Изменить</button>
      `;
      listWrap.append(row);
    });
  }

  async function refreshProfileSnapshot() {
    status.className = 'status-line';
    status.textContent = 'Загрузка параметров...';
    reloadBtn.disabled = true;
    officialBtn.disabled = true;
    shineBtn.disabled = true;
    genderBtn.disabled = true;

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
      genderBtn.disabled = false;
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

    const entered = window.prompt(`Введите новое значение для «${field.label}»:`, field.value || '');
    if (entered === null) return;

    const confirmed = window.confirm(
      `Записать новое значение параметра «${field.label}» в блокчейн?`,
    );
    if (!confirmed) return;

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
    const entered = window.prompt(
      'Выберите пол:\n1 — Мужской\n2 — Женский\n3 — Не указан\nМожно ввести номер или значение (male/female/unknown).',
      currentGender,
    );
    if (entered === null) return;
    const nextGender = parseGenderChoice(entered);
    if (!nextGender) {
      window.alert('Некорректный выбор пола. Доступно: male, female, unknown.');
      return;
    }

    const confirmed = window.confirm(
      `Установить пол: «${genderLabel(nextGender)}»?\nБудет создана запись в блокчейне.`,
    );
    if (!confirmed) return;

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
    const fieldKey = target.dataset.editField;
    if (!fieldKey) return;
    onEditFieldClick(fieldKey);
  });

  reloadBtn.addEventListener('click', refreshProfileSnapshot);
  officialBtn.addEventListener('click', () => onToggleClick('official'));
  shineBtn.addEventListener('click', () => onToggleClick('shine'));
  genderBtn.addEventListener('click', onGenderClick);

  card.append(topRow, badgesRow, status, genderWrap, listWrap);
  screen.append(card);

  refreshProfileSnapshot();

  return screen;
}
