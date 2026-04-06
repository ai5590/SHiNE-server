import { authService, state } from '../state.js?v=20260403081123';

export const profileFieldDefs = [
  { key: 'first_name', readKeys: ['first_name', 'name'], label: 'First name', placeholder: 'Введите имя' },
  { key: 'last_name', readKeys: ['last_name'], label: 'Last name', placeholder: 'Введите фамилию' },
  { key: 'address_physical', readKeys: ['address_physical'], label: 'Address physical', placeholder: 'Город, улица, дом' },
  { key: 'address_web', readKeys: ['address_web'], label: 'Address web', placeholder: 'Сайт или профиль' },
  { key: 'phone', readKeys: ['phone'], label: 'Phone', placeholder: '+7 ...' },
];

export const profileToggleDefs = [
  { key: 'official', label: 'Официальный' },
  { key: 'shine', label: 'Сияющий' },
];

function normalizeItems(responsePayload) {
  const params = responsePayload?.params;
  if (!Array.isArray(params)) return [];
  return params
    .map((item) => ({
      param: String(item?.param || '').trim(),
      value: String(item?.value || ''),
      timeMs: Number(item?.time_ms || 0),
    }))
    .filter((item) => item.param);
}

function getLatestByAliases(items, aliases) {
  let latest = null;
  items.forEach((item) => {
    if (!aliases.includes(item.param)) return;
    if (!latest || item.timeMs >= latest.timeMs) {
      latest = item;
    }
  });
  return latest;
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

export async function loadProfileSnapshot(login) {
  const payload = await authService.listUserParams(login);
  const items = normalizeItems(payload);

  const fields = profileFieldDefs.map((field) => {
    const latest = getLatestByAliases(items, field.readKeys);
    return {
      key: field.key,
      label: field.label,
      placeholder: field.placeholder,
      value: latest?.value || '',
      timeMs: latest?.timeMs || 0,
    };
  });

  const toggles = profileToggleDefs.map((toggle) => {
    const latest = getLatestByAliases(items, [toggle.key]);
    return {
      key: toggle.key,
      label: toggle.label,
      enabled: latest ? parseToggleValue(latest.value) : false,
      rawValue: latest?.value || 'no',
      timeMs: latest?.timeMs || 0,
    };
  });

  return { fields, toggles };
}

export async function saveProfileParams(login, valuesByKey) {
  const storagePwd = await getStoragePwd();
  const baseTime = Date.now();

  for (let i = 0; i < profileFieldDefs.length; i += 1) {
    const field = profileFieldDefs[i];
    await authService.upsertUserParam({
      login,
      param: field.key,
      value: String(valuesByKey[field.key] || '').trim(),
      timeMs: baseTime + i,
      storagePwd,
    });
  }
}

export async function saveProfileToggle(login, key, enabled) {
  const storagePwd = await getStoragePwd();
  await authService.upsertUserParam({
    login,
    param: key,
    value: enabled ? 'yes' : 'no',
    timeMs: Date.now(),
    storagePwd,
  });
}
