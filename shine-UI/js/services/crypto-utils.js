const encoder = new TextEncoder();
const WEB_CRYPTO_REQUIRED_MESSAGE = 'Регистрация и подпись блоков требуют WebCrypto (crypto.subtle). Откройте приложение через HTTPS или localhost в современном браузере и повторите попытку.';

function getCryptoApi() {
  const api = globalThis.crypto;
  if (!api || typeof api.getRandomValues !== 'function') {
    throw new Error(WEB_CRYPTO_REQUIRED_MESSAGE);
  }
  return api;
}

function getSubtleApi() {
  const api = getCryptoApi();
  if (!api.subtle) {
    throw new Error(WEB_CRYPTO_REQUIRED_MESSAGE);
  }
  return api.subtle;
}


function base64UrlToBase64(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padLen = (4 - (normalized.length % 4)) % 4;
  return normalized + '='.repeat(padLen);
}

export function randomBase64(byteLen = 32) {
  const bytes = getCryptoApi().getRandomValues(new Uint8Array(byteLen));
  return bytesToBase64(bytes);
}

export function bytesToBase64(bytes) {
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
}

export function base64ToBytes(base64) {
  const normalized = (base64 || '').trim();
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function utf8Bytes(value) {
  return encoder.encode(value);
}

export async function sha256Bytes(bytes) {
  const digest = await getSubtleApi().digest('SHA-256', bytes);
  return new Uint8Array(digest);
}

export async function sha256Text(text) {
  return sha256Bytes(utf8Bytes(text));
}

export async function derivePasswordSeed(password, suffix) {
  const base = await sha256Text(password || '');
  const concat = `${bytesToBase64(base)}${suffix}`;
  return sha256Text(concat);
}

function ed25519Pkcs8FromSeed(seed32) {
  if (seed32.length !== 32) {
    throw new Error('Для Ed25519 нужен seed длиной 32 байта');
  }
  const prefix = new Uint8Array([
    0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20,
  ]);
  const out = new Uint8Array(prefix.length + seed32.length);
  out.set(prefix, 0);
  out.set(seed32, prefix.length);
  return out;
}

export async function deriveEd25519FromPassword(password, suffix) {
  const seed = await derivePasswordSeed(password, suffix);
  const pkcs8 = ed25519Pkcs8FromSeed(seed);
  const subtle = getSubtleApi();
  const privateKey = await subtle.importKey('pkcs8', pkcs8, { name: 'Ed25519' }, true, ['sign']);
  const jwk = await subtle.exportKey('jwk', privateKey);
  if (!jwk.x) throw new Error('Не удалось получить публичный ключ Ed25519');

  return {
    privateKey,
    publicKeyB64: bytesToBase64(base64ToBytes(base64UrlToBase64(jwk.x))),
    privatePkcs8B64: bytesToBase64(pkcs8),
  };
}

export async function deriveAesKeyFromStoragePwd(storagePwd, saltBytes) {
  const subtle = getSubtleApi();
  const baseKey = await subtle.importKey(
    'raw',
    utf8Bytes(storagePwd),
    { name: 'PBKDF2' },
    false,
    ['deriveKey'],
  );

  return subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: saltBytes,
      iterations: 210000,
      hash: 'SHA-256',
    },
    baseKey,
    {
      name: 'AES-GCM',
      length: 256,
    },
    false,
    ['encrypt', 'decrypt'],
  );
}

export async function encryptJsonWithStoragePwd(value, storagePwd) {
  const cryptoApi = getCryptoApi();
  const subtle = getSubtleApi();
  const salt = cryptoApi.getRandomValues(new Uint8Array(16));
  const iv = cryptoApi.getRandomValues(new Uint8Array(12));
  const key = await deriveAesKeyFromStoragePwd(storagePwd, salt);
  const plainBytes = utf8Bytes(JSON.stringify(value));
  const cipher = await subtle.encrypt({ name: 'AES-GCM', iv }, key, plainBytes);

  return {
    saltB64: bytesToBase64(salt),
    ivB64: bytesToBase64(iv),
    cipherB64: bytesToBase64(new Uint8Array(cipher)),
  };
}

export async function decryptJsonWithStoragePwd(envelope, storagePwd) {
  const salt = base64ToBytes(envelope.saltB64);
  const iv = base64ToBytes(envelope.ivB64);
  const cipher = base64ToBytes(envelope.cipherB64);
  const key = await deriveAesKeyFromStoragePwd(storagePwd, salt);
  const plain = await getSubtleApi().decrypt({ name: 'AES-GCM', iv }, key, cipher);
  const text = new TextDecoder().decode(plain);
  return JSON.parse(text);
}

export async function generateEd25519Pair() {
  return getSubtleApi().generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);
}

export async function exportEd25519PublicKeyB64(publicKey) {
  const raw = await getSubtleApi().exportKey('raw', publicKey);
  return bytesToBase64(new Uint8Array(raw));
}

export async function exportPkcs8B64(privateKey) {
  const raw = await getSubtleApi().exportKey('pkcs8', privateKey);
  return bytesToBase64(new Uint8Array(raw));
}

export async function importPkcs8Ed25519(pkcs8B64) {
  return getSubtleApi().importKey('pkcs8', base64ToBytes(pkcs8B64), { name: 'Ed25519' }, false, ['sign']);
}

export async function signBase64(privateKey, text) {
  const signature = await getSubtleApi().sign({ name: 'Ed25519' }, privateKey, utf8Bytes(text));
  return bytesToBase64(new Uint8Array(signature));
}

export async function signBytes(privateKey, bytes) {
  const signature = await getSubtleApi().sign({ name: 'Ed25519' }, privateKey, bytes);
  return new Uint8Array(signature);
}
