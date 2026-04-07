import {
  decryptJsonWithStoragePwd,
  encryptJsonWithStoragePwd,
} from './crypto-utils.js?v=20260407105357';

const DB_NAME = 'shine-ui-auth';
const DB_VERSION = 1;
const STORE_SECRETS = 'encrypted-secrets';
const STORE_SESSIONS = 'session-keys';

function openDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_SECRETS)) {
        db.createObjectStore(STORE_SECRETS, { keyPath: 'login' });
      }
      if (!db.objectStoreNames.contains(STORE_SESSIONS)) {
        db.createObjectStore(STORE_SESSIONS, { keyPath: 'login' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('IndexedDB недоступен'));
  });
}

async function put(storeName, value) {
  const db = await openDb();
  await new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    tx.objectStore(storeName).put(value);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error || new Error('Ошибка записи в IndexedDB'));
  });
  db.close();
}

async function get(storeName, key) {
  const db = await openDb();
  const result = await new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const req = tx.objectStore(storeName).get(key);
    req.onsuccess = () => resolve(req.result || null);
    req.onerror = () => reject(req.error || new Error('Ошибка чтения из IndexedDB'));
  });
  db.close();
  return result;
}

export async function saveEncryptedUserSecrets(login, storagePwd, keys) {
  const encrypted = await encryptJsonWithStoragePwd(keys, storagePwd);
  await put(STORE_SECRETS, {
    login,
    encrypted,
    updatedAtMs: Date.now(),
  });
}

export async function loadEncryptedUserSecrets(login, storagePwd) {
  const row = await get(STORE_SECRETS, login);
  if (!row?.encrypted) {
    throw new Error('На устройстве нет сохранённых ключей для этого логина');
  }
  return decryptJsonWithStoragePwd(row.encrypted, storagePwd);
}

export async function saveSessionMaterial(login, material) {
  await put(STORE_SESSIONS, {
    login,
    ...material,
    updatedAtMs: Date.now(),
  });
}

export async function loadSessionMaterial(login) {
  return get(STORE_SESSIONS, login);
}

export async function clearClientAuthData() {
  await new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(DB_NAME);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error || new Error('Не удалось очистить IndexedDB'));
    request.onblocked = () => reject(new Error('Очистка IndexedDB заблокирована открытыми соединениями'));
  });
}
