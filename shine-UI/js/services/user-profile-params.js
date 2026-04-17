import { authService, state } from '../state.js';

export const profileFieldDefs = [
  { key: 'first_name', readKeys: ['first_name'], label: 'Имя', placeholder: 'Введите имя' },
  { key: 'last_name', readKeys: ['last_name'], label: 'Фамилия', placeholder: 'Введите фамилию' },
  { key: 'address', readKeys: ['address'], label: 'Адрес', placeholder: 'Город, улица, дом' },
  { key: 'web', readKeys: ['web'], label: 'Веб', placeholder: 'Сайт или профиль' },
  { key: 'phone', readKeys: ['phone'], label: 'Телефон', placeholder: '+7 ...' },
];

export const profileToggleDefs = [
  { key: 'official', label: 'Официальный' },
  { key: 'shine', label: 'Сияющий' },
];

export const PROFILE_GENDER_MALE = 'male';
export const PROFILE_GENDER_FEMALE = 'female';
export const PROFILE_GENDER_UNKNOWN = 'unknown';
export const PROFILE_GENDER_VALUES = Object.freeze([
  PROFILE_GENDER_MALE,
  PROFILE_GENDER_FEMALE,
  PROFILE_GENDER_UNKNOWN,
]);

function normalizeItem(param, payload) {
  if (!param) return null;

  if (payload && typeof payload === 'object') {
    const value = String(payload?.value || payload?.param_value || '');
    const timeMs = Number(payload?.time_ms || payload?.timeMs || 0);
    if (!value && !timeMs) return null;
    return { param, value, timeMs };
  }

  return null;
}

function parseToggleValue(value) {
  const normalized = String(value || '').trim().toLowerCase();
  return normalized === 'true' || normalized === 'yes' || normalized === '1';
}

function normalizeGenderValue(value) {
  const normalized = String(value || '').trim().toLowerCase();
  if (normalized === PROFILE_GENDER_MALE) return PROFILE_GENDER_MALE;
  if (normalized === PROFILE_GENDER_FEMALE) return PROFILE_GENDER_FEMALE;
  return PROFILE_GENDER_UNKNOWN;
}

async function getStoragePwd() {
  const storagePwd = state.session.storagePwdInMemory;
  if (!storagePwd) {
    throw new Error('Нет storagePwd в памяти сессии. Выполните вход заново.');
  }
  return storagePwd;
}

function normalizeListItems(payload) {
  const rows = Array.isArray(payload?.params) ? payload.params : [];
  const normalized = [];
  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i];
    if (!row || typeof row !== 'object') continue;
    const param = String(row.param || '').trim();
    if (!param) continue;
    const item = normalizeItem(param, row);
    if (item) normalized.push(item);
  }
  return normalized;
}

function loadLatestByAliasesFromItems(items, aliases) {
  if (!Array.isArray(items) || !items.length || !Array.isArray(aliases) || !aliases.length) return null;
  const aliasSet = new Set(aliases.map((alias) => String(alias || '').trim().toLowerCase()).filter(Boolean));
  if (!aliasSet.size) return null;

  let latest = null;
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i];
    const itemParam = String(item?.param || '').trim().toLowerCase();
    if (!itemParam || !aliasSet.has(itemParam)) continue;
    if (!latest || Number(item.timeMs || 0) > Number(latest.timeMs || 0)) {
      latest = item;
    }
  }
  return latest;
}

export async function loadProfileSnapshot(login) {
  const payload = await authService.listUserParams(login);
  const items = normalizeListItems(payload);

  const fields = [];
  for (let i = 0; i < profileFieldDefs.length; i += 1) {
    const field = profileFieldDefs[i];
    const latest = loadLatestByAliasesFromItems(items, field.readKeys);
    fields.push({
      key: field.key,
      label: field.label,
      placeholder: field.placeholder,
      value: latest?.value || '',
      timeMs: latest?.timeMs || 0,
    });
  }

  const toggles = [];
  for (let i = 0; i < profileToggleDefs.length; i += 1) {
    const toggle = profileToggleDefs[i];
    const latest = loadLatestByAliasesFromItems(items, [toggle.key]);
    toggles.push({
      key: toggle.key,
      label: toggle.label,
      enabled: latest ? parseToggleValue(latest.value) : false,
      rawValue: latest?.value || 'no',
      timeMs: latest?.timeMs || 0,
    });
  }

  const latestGender = loadLatestByAliasesFromItems(items, ['gender']);
  const gender = normalizeGenderValue(latestGender?.value || PROFILE_GENDER_UNKNOWN);

  return {
    fields,
    toggles,
    gender,
    genderTimeMs: latestGender?.timeMs || 0,
  };
}

export async function saveProfileParamBlock(login, key, value) {
  const storagePwd = await getStoragePwd();
  await authService.addBlockUserParam({
    login,
    param: key,
    value: String(value ?? '').trim(),
    storagePwd,
  });
}

export async function saveProfileToggle(login, key, enabled) {
  const storagePwd = await getStoragePwd();
  await authService.addBlockUserParam({
    login,
    param: key,
    value: enabled ? 'yes' : 'no',
    storagePwd,
  });
}

export async function saveProfileGender(login, gender) {
  const normalized = normalizeGenderValue(gender);
  const storagePwd = await getStoragePwd();
  await authService.addBlockUserParam({
    login,
    param: 'gender',
    value: normalized,
    storagePwd,
  });
}
