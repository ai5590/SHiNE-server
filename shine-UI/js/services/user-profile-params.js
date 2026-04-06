import { authService, state } from '../state.js?v=20260403081123';

export const profileFieldDefs = [
  { key: 'first_name', readKeys: ['first_name', 'ашкые_name'], label: 'Имя', placeholder: 'Введите имя' },
  { key: 'last_name', readKeys: ['last_name'], label: 'Фамилия', placeholder: 'Введите фамилию' },
  { key: 'address_physical', readKeys: ['address_physical'], label: 'Физический адрес', placeholder: 'Город, улица, дом' },
  { key: 'address_web', readKeys: ['address_web'], label: 'Веб-адрес', placeholder: 'Сайт или профиль' },
  { key: 'phone', readKeys: ['phone'], label: 'Телефон', placeholder: '+7 ...' },
];

export const profileToggleDefs = [
  { key: 'official', label: 'Официальный' },
  { key: 'shine', label: 'Сияющий' },
];

function normalizeItem(param, payload) {
  if (!param) return null;

  if (Array.isArray(payload?.params)) {
    const latest = payload.params
      .map((item) => ({
        value: String(item?.value || ''),
        timeMs: Number(item?.time_ms || 0),
      }))
      .sort((a, b) => b.timeMs - a.timeMs)[0];

    if (!latest) return null;
    return { param, value: latest.value, timeMs: latest.timeMs };
  }

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

async function getStoragePwd() {
  const storagePwd = state.session.storagePwdInMemory;
  if (!storagePwd) {
    throw new Error('Нет storagePwd в памяти сессии. Выполните вход заново.');
  }
  return storagePwd;
}

async function loadLatestByAliases(login, aliases) {
  const collected = [];

  for (let i = 0; i < aliases.length; i += 1) {
    const alias = aliases[i];
    try {
      const payload = await authService.getUserParam(login, alias);
      const normalized = normalizeItem(alias, payload);
      if (normalized) collected.push(normalized);
    } catch {
      // Пусто — параметр ещё не создан или endpoint не отвечает для конкретного ключа.
    }
  }

  if (!collected.length) return null;
  return collected.sort((a, b) => b.timeMs - a.timeMs)[0];
}

export async function loadProfileSnapshot(login) {
  const fields = [];
  for (let i = 0; i < profileFieldDefs.length; i += 1) {
    const field = profileFieldDefs[i];
    const latest = await loadLatestByAliases(login, field.readKeys);
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
    const latest = await loadLatestByAliases(login, [toggle.key]);
    toggles.push({
      key: toggle.key,
      label: toggle.label,
      enabled: latest ? parseToggleValue(latest.value) : false,
      rawValue: latest?.value || 'no',
      timeMs: latest?.timeMs || 0,
    });
  }

  return { fields, toggles };
}

export async function saveProfileParamBlock(login, key, value) {
  const storagePwd = await getStoragePwd();
  await authService.addUserParamBlock({
    login,
    param: key,
    value: String(value ?? '').trim(),
    timeMs: Date.now(),
    storagePwd,
  });
}

export async function saveProfileToggle(login, key, enabled) {
  const storagePwd = await getStoragePwd();
  await authService.addUserParamBlock({
    login,
    param: key,
    value: enabled ? 'yes' : 'no',
    timeMs: Date.now(),
    storagePwd,
  });
}
