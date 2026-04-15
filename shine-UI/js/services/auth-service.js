import { WsJsonClient } from './ws-client.js';
import {
  bytesToBase64,
  deriveEd25519FromPassword,
  exportEd25519PublicKeyB64,
  exportPkcs8B64,
  generateEd25519Pair,
  importPkcs8Ed25519,
  randomBase64,
  sha256Bytes,
  signBytes,
  signBase64,
  utf8Bytes,
} from './crypto-utils.js';
import {
  loadEncryptedUserSecrets,
  loadSessionMaterial,
  saveEncryptedUserSecrets,
  saveSessionMaterial,
} from './key-vault.js';

const BCH_SUFFIX = '001';
const ZERO_HASH_HEX = '0'.repeat(64);

const CONNECTION_SUBTYPES = Object.freeze({
  friend: { on: 10, off: 11 },
  contact: { on: 20, off: 21 },
  follow: { on: 30, off: 31 },
});

function normalizeServerUrl(url) {
  const value = (url || '').trim();
  if (!value) return 'wss://shineup.me/ws';
  if (value.startsWith('ws://') || value.startsWith('wss://')) return value;
  if (value.startsWith('https://') || value.startsWith('http://')) {
    return `${value.replace(/^http/, 'ws').replace(/\/$/, '')}/ws`;
  }
  return value;
}

function opError(op, response) {
  const message = response?.payload?.message || response?.message || 'Неизвестная ошибка сервера';
  const code = response?.payload?.code || response?.code || 'UNKNOWN';
  const error = new Error(`${op}: ${message} (${code})`);
  error.op = op;
  error.code = code;
  error.status = response?.status || 0;
  return error;
}

function makeClientInfo() {
  const ua = navigator.userAgent || 'unknown';
  return ua.slice(0, 50);
}

function hexToBytes(hex) {
  const clean = String(hex || '').trim().toLowerCase();
  if (!clean || clean.length % 2 !== 0) throw new Error('Некорректный hex');
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

function concatBytes(...chunks) {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  chunks.forEach((chunk) => {
    out.set(chunk, offset);
    offset += chunk.length;
  });
  return out;
}

function int32Bytes(value) {
  const bytes = new Uint8Array(4);
  const view = new DataView(bytes.buffer);
  view.setInt32(0, Number(value), false);
  return bytes;
}

function int16Bytes(value) {
  const bytes = new Uint8Array(2);
  const view = new DataView(bytes.buffer);
  view.setUint16(0, Number(value), false);
  return bytes;
}

function int64Bytes(value) {
  const bytes = new Uint8Array(8);
  const view = new DataView(bytes.buffer);
  view.setBigInt64(0, BigInt(value), false);
  return bytes;
}

function uint8Bytes(value) {
  return new Uint8Array([Number(value) & 0xff]);
}

function makeUserParamBodyBytes({ lineCode, prevLineNumber, prevLineHashHex, thisLineNumber, key, value }) {
  const keyBytes = utf8Bytes(String(key || ''));
  const valueBytes = utf8Bytes(String(value || ''));
  const prevHashBytes = hexToBytes(prevLineHashHex);
  if (!keyBytes.length || !valueBytes.length) throw new Error('Пустые key/value для блока параметра');
  if (prevHashBytes.length !== 32) throw new Error('prevLineHash должен быть 32 байта');

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    prevHashBytes,
    int32Bytes(thisLineNumber),
    int16Bytes(keyBytes.length),
    keyBytes,
    int16Bytes(valueBytes.length),
    valueBytes,
  );
}

function makeConnectionBodyBytes({
  lineCode,
  prevLineNumber,
  prevLineHashHex,
  thisLineNumber,
  toBlockchainName,
  toBlockNumber,
  toBlockHashHex,
}) {
  const cleanBchName = String(toBlockchainName || '').trim();
  if (!cleanBchName) throw new Error('Пустой toBlockchainName для CONNECTION');
  const toBchBytes = utf8Bytes(cleanBchName);
  if (!toBchBytes.length || toBchBytes.length > 255) {
    throw new Error('toBlockchainName должен быть 1..255 байт UTF-8');
  }

  const prevHashBytes = hexToBytes(prevLineHashHex);
  const toBlockHashBytes = hexToBytes(toBlockHashHex);
  if (prevHashBytes.length !== 32) throw new Error('prevLineHash должен быть 32 байта');
  if (toBlockHashBytes.length !== 32) throw new Error('toBlockHash должен быть 32 байта');

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    prevHashBytes,
    int32Bytes(thisLineNumber),
    uint8Bytes(toBchBytes.length),
    toBchBytes,
    int32Bytes(toBlockNumber),
    toBlockHashBytes,
  );
}

export class AuthService {
  constructor(serverUrl) {
    this.serverUrl = normalizeServerUrl(serverUrl);
    this.ws = new WsJsonClient(this.serverUrl);
  }

  async reconnect(serverUrl) {
    const normalized = normalizeServerUrl(serverUrl);
    if (normalized === this.serverUrl) return;
    this.ws.close();
    this.serverUrl = normalized;
    this.ws = new WsJsonClient(this.serverUrl);
  }

  async getUser(login) {
    const response = await this.ws.request('GetUser', { login });
    if (response.status !== 200) throw opError('GetUser', response);
    return response.payload || {};
  }

  async ensureLoginFree(login) {
    const payload = await this.getUser(login);
    return payload.exists !== true;
  }

  async derivePasswordKeyBundle(password) {
    if (!password) throw new Error('Введите пароль');
    const rootPair = await deriveEd25519FromPassword(password, 'root.key');
    const blockchainPair = await deriveEd25519FromPassword(password, 'bch.key');
    const devicePair = await deriveEd25519FromPassword(password, 'dev.key');
    return { rootPair, blockchainPair, devicePair };
  }

  async createAuthSession(login, keyBundle) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Введите логин');

    const sessionPair = await generateEd25519Pair();
    const sessionKeyPub = await exportEd25519PublicKeyB64(sessionPair.publicKey);
    const sessionKey = `ed25519/${sessionKeyPub}`;
    const storagePwd = randomBase64(32);

    const challengeResp = await this.ws.request('AuthChallenge', { login: cleanLogin });
    if (challengeResp.status !== 200) throw opError('AuthChallenge', challengeResp);

    const authNonce = challengeResp?.payload?.authNonce;
    if (!authNonce) throw new Error('AuthChallenge: сервер не вернул authNonce');

    const timeMs = Date.now();
    const preimage = `AUTH_CREATE_SESSION:${cleanLogin}:${sessionKey}:${storagePwd}:${timeMs}:${authNonce}`;
    const signatureB64 = await signBase64(keyBundle.devicePair.privateKey, preimage);

    const createResp = await this.ws.request('CreateAuthSession', {
      login: cleanLogin,
      storagePwd,
      sessionKey,
      timeMs,
      authNonce,
      deviceKey: keyBundle.devicePair.publicKeyB64,
      signatureB64,
      clientInfo: makeClientInfo(),
    });
    if (createResp.status !== 200) throw opError('CreateAuthSession', createResp);

    const sessionId = createResp?.payload?.sessionId;
    if (!sessionId) throw new Error('CreateAuthSession: не вернулся sessionId');

    return {
      login: cleanLogin,
      sessionId,
      storagePwd,
      sessionMaterial: {
        sessionId,
        sessionKey,
        sessionPrivPkcs8: await exportPkcs8B64(sessionPair.privateKey),
      },
    };
  }

  async registerUser(login, password) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Введите логин');
    if (!password) throw new Error('Введите пароль');

    const isFree = await this.ensureLoginFree(cleanLogin);
    if (!isFree) throw new Error('Этот логин уже занят');

    const keyBundle = await this.derivePasswordKeyBundle(password);

    const addResp = await this.ws.request('AddUser', {
      login: cleanLogin,
      blockchainName: `${cleanLogin}-${BCH_SUFFIX}`,
      solanaKey: keyBundle.rootPair.publicKeyB64,
      blockchainKey: keyBundle.blockchainPair.publicKeyB64,
      deviceKey: keyBundle.devicePair.publicKeyB64,
      bchLimit: 1000000,
    });
    if (addResp.status !== 200) throw opError('AddUser', addResp);

    const session = await this.createAuthSession(cleanLogin, keyBundle);
    return { ...session, keyBundle };
  }

  async createSessionForExistingUser(login, password) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Введите логин');
    if (!password) throw new Error('Введите пароль');

    const user = await this.getUser(cleanLogin);
    if (!user.exists) throw new Error('Пользователь не найден');

    const keyBundle = await this.derivePasswordKeyBundle(password);
    const session = await this.createAuthSession(cleanLogin, keyBundle);
    return { ...session, keyBundle };
  }

  async persistSelectedKeys(login, storagePwd, keyBundle, saveOptions = { saveRoot: true, saveBlockchain: true }) {
    const secrets = { deviceKey: keyBundle.devicePair.privatePkcs8B64 };
    if (saveOptions.saveRoot) secrets.rootKey = keyBundle.rootPair.privatePkcs8B64;
    if (saveOptions.saveBlockchain) secrets.blockchainKey = keyBundle.blockchainPair.privatePkcs8B64;
    await saveEncryptedUserSecrets(login, storagePwd, secrets);
  }

  async persistSessionMaterial(login, sessionMaterial) {
    await saveSessionMaterial(login, sessionMaterial);
  }


  async resumeSession(login, preferredSessionId = '') {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Нет login для авто-входа');

    const sessionMaterial = await loadSessionMaterial(cleanLogin);
    if (!sessionMaterial?.sessionId || !sessionMaterial?.sessionKey || !sessionMaterial?.sessionPrivPkcs8) {
      throw new Error('На устройстве нет сохраненного ключа сессии');
    }

    const targetSessionId = preferredSessionId || sessionMaterial.sessionId;
    const privateKey = await importPkcs8Ed25519(sessionMaterial.sessionPrivPkcs8);

    const challengeResp = await this.ws.request('SessionChallenge', { sessionId: targetSessionId });
    if (challengeResp.status !== 200) throw opError('SessionChallenge', challengeResp);

    const nonce = challengeResp?.payload?.nonce;
    if (!nonce) throw new Error('SessionChallenge: не вернулся nonce');

    const timeMs = Date.now();
    const preimage = `SESSION_LOGIN:${targetSessionId}:${timeMs}:${nonce}`;
    const signatureB64 = await signBase64(privateKey, preimage);

    const loginResp = await this.ws.request('SessionLogin', {
      sessionId: targetSessionId,
      sessionKey: sessionMaterial.sessionKey,
      timeMs,
      signatureB64,
      clientInfo: makeClientInfo(),
    });
    if (loginResp.status !== 200) throw opError('SessionLogin', loginResp);

    const storagePwd = loginResp?.payload?.storagePwd;
    if (!storagePwd) throw new Error('SessionLogin: не вернулся storagePwd');

    return {
      login: cleanLogin,
      sessionId: targetSessionId,
      storagePwd,
    };
  }

  async listSessions() {
    const response = await this.ws.request('ListSessions', {});
    if (response.status !== 200) throw opError('ListSessions', response);
    return response?.payload?.sessions || [];
  }

  async closeSession(sessionId) {
    const response = await this.ws.request('CloseActiveSession', { sessionId });
    if (response.status !== 200) throw opError('CloseActiveSession', response);
  }

  async listSubscriptionsFeed(login, limit = 200) {
    const response = await this.ws.request('ListSubscriptionsFeed', { login, limit });
    if (response.status !== 200) throw opError('ListSubscriptionsFeed', response);
    return response.payload || {};
  }

  async getChannelMessages(channel, limit = 200, sort = 'asc') {
    const response = await this.ws.request('GetChannelMessages', { channel, limit, sort });
    if (response.status !== 200) throw opError('GetChannelMessages', response);
    return response.payload || {};
  }

  async getMessageThread(message, depthUp = 20, depthDown = 2, limitChildrenPerNode = 50) {
    const response = await this.ws.request('GetMessageThread', { message, depthUp, depthDown, limitChildrenPerNode });
    if (response.status !== 200) throw opError('GetMessageThread', response);
    return response.payload || {};
  }


  onEvent(op, handler) {
    return this.ws.onEvent(op, handler);
  }

  async upsertPushToken({ tokenId, token, provider = 'fcm', platform = 'web', userAgent = navigator.userAgent || '' }) {
    const response = await this.ws.request('UpsertPushToken', { tokenId, token, provider, platform, userAgent });
    if (response.status !== 200) throw opError('UpsertPushToken', response);
    return response.payload || {};
  }

  async sendDirectMessage(toLogin, text) {
    const response = await this.ws.request('SendDirectMessage', { toLogin, text });
    if (response.status !== 200) throw opError('SendDirectMessage', response);
    return response.payload || {};
  }

  async ackIncomingMessage(eventId, messageId) {
    const response = await this.ws.request('AckIncomingMessage', { eventId, messageId });
    if (response.status !== 200) throw opError('AckIncomingMessage', response);
    return response.payload || {};
  }


  async callInviteBroadcast({ toLogin, callId, type = 100 }) {
    const response = await this.ws.request('CallInviteBroadcast', { toLogin, callId, type });
    if (response.status !== 200) throw opError('CallInviteBroadcast', response);
    return response.payload || {};
  }

  async callSignalToSession({ toLogin, targetSessionId, callId, type, data = '' }) {
    const response = await this.ws.request('CallSignalToSession', { toLogin, targetSessionId, callId, type, data });
    if (response.status !== 200) throw opError('CallSignalToSession', response);
    return response.payload || {};
  }
  async listContacts() {
    const response = await this.ws.request('ListContacts', {});
    if (response.status !== 200) throw opError('ListContacts', response);
    return response.payload || {};
  }


  async addCloseFriend(toLogin) {
    const response = await this.ws.request('AddCloseFriend', { toLogin });
    if (response.status !== 200) throw opError('AddCloseFriend', response);
    return response.payload || {};
  }

  async getUserConnectionsGraph(login) {
    const response = await this.ws.request('GetUserConnectionsGraph', { login });
    if (response.status !== 200) throw opError('GetUserConnectionsGraph', response);
    return response.payload || {};
  }

  async searchUsers(prefix) {
    const response = await this.ws.request('SearchUsers', { prefix });
    if (response.status !== 200) throw opError('SearchUsers', response);
    return response.payload?.logins || [];
  }



  async getUserParam(login, param) {
    const cleanLogin = (login || '').trim();
    const cleanParam = (param || '').trim();
    if (!cleanLogin || !cleanParam) throw new Error('Не переданы login/param');

    const response = await this.ws.request('GetUserParam', { login: cleanLogin, param: cleanParam });
    if (response.status === 200) return response.payload || {};

    if (response.status === 404 || response.status === 204) return {};

    throw opError('GetUserParam', response);
  }

  async setUserRelation({ login, toLogin, kind, enabled, storagePwd }) {
    const cleanKind = String(kind || '').trim().toLowerCase();
    const kinds = CONNECTION_SUBTYPES[cleanKind];
    if (!kinds) throw new Error(`Неподдерживаемый тип связи: ${kind}`);
    const subType = enabled ? kinds.on : kinds.off;
    return this.addBlockConnection({ login, toLogin, subType, storagePwd });
  }

  async addBlockUserParam({ login, param, value, storagePwd }) {
    const cleanLogin = (login || '').trim();
    const cleanParam = (param || '').trim();
    const cleanValue = String(value ?? '').trim();
    if (!cleanLogin || !cleanParam) throw new Error('Не переданы login/param.');
    if (!cleanValue) throw new Error('Значение параметра не может быть пустым.');
    if (!storagePwd) throw new Error('Не передан storagePwd для подписи AddBlock.');

    const user = await this.getUser(cleanLogin);
    const blockchainName = String(user?.blockchainName || `${cleanLogin}-${BCH_SUFFIX}`).trim();
    const freshNum = Number(user?.serverLastGlobalNumber);
    const freshHash = String(user?.serverLastGlobalHash || '').trim().toLowerCase();
    const freshCursor = {
      serverLastGlobalNumber: Number.isFinite(freshNum) ? freshNum : -1,
      serverLastGlobalHash: freshHash.length === 64 ? freshHash : ZERO_HASH_HEX,
    };

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const blockchainPrivatePkcs8 = savedKeys?.blockchainKey;
    if (!blockchainPrivatePkcs8) {
      throw new Error('На устройстве нет сохраненного приватного blockchainKey');
    }

    const privateKey = await importPkcs8Ed25519(blockchainPrivatePkcs8);

    const tryAdd = async (cursor) => {
      const blockNumber = Number(cursor?.serverLastGlobalNumber ?? -1) + 1;
      const prevBlockHash = String(cursor?.serverLastGlobalHash || ZERO_HASH_HEX);

      // Для USER_PARAM отправляем старт новой line-цепочки:
      // prevLineNumber=-1, prevLineHash=0x00..00, thisLineNumber=-1.
      // Этот формат соответствует BodyHasLine правилам на сервере.
      const bodyBytes = makeUserParamBodyBytes({
        lineCode: 0,
        prevLineNumber: -1,
        prevLineHashHex: ZERO_HASH_HEX,
        thisLineNumber: -1,
        key: cleanParam,
        value: cleanValue,
      });

      const preimage = concatBytes(
        int16Bytes(0),
        hexToBytes(prevBlockHash),
        int32Bytes(2 + 32 + 4 + 4 + 8 + 2 + 2 + 2 + bodyBytes.length),
        int32Bytes(blockNumber),
        int64Bytes(Math.floor(Date.now() / 1000)),
        int16Bytes(4),
        int16Bytes(1),
        int16Bytes(1),
        bodyBytes,
      );

      const hash32 = await sha256Bytes(preimage);
      const signatureBytes = await signBytes(privateKey, hash32);
      const fullBlock = concatBytes(preimage, int16Bytes(0x0100), signatureBytes);

      const response = await this.ws.request('AddBlock', {
        blockchainName,
        blockNumber,
        prevBlockHash,
        blockBytesB64: bytesToBase64(fullBlock),
      });

      return response;
    };

    let cursor = freshCursor;
    let response = await tryAdd(cursor);
    if (response.status !== 200) {
      const knownNum = Number(response?.payload?.serverLastGlobalNumber);
      const knownHash = String(response?.payload?.serverLastGlobalHash || '');
      if (Number.isFinite(knownNum) && knownHash.length === 64) {
        cursor = { serverLastGlobalNumber: knownNum, serverLastGlobalHash: knownHash };
        response = await tryAdd(cursor);
      }
    }

    if (response.status !== 200) throw opError('AddBlock', response);
    return response.payload || {};
  }

  async addBlockConnection({ login, toLogin, subType, storagePwd }) {
    const cleanLogin = (login || '').trim();
    const cleanToLogin = (toLogin || '').trim();
    const cleanSubType = Number(subType);
    if (!cleanLogin || !cleanToLogin) throw new Error('Не переданы login/toLogin для CONNECTION.');
    if (!Number.isFinite(cleanSubType)) throw new Error('Не передан subType для CONNECTION.');
    if (!storagePwd) throw new Error('Не передан storagePwd для подписи AddBlock.');
    if (cleanLogin.toLowerCase() === cleanToLogin.toLowerCase()) {
      throw new Error('Нельзя создать связь на самого себя.');
    }

    const user = await this.getUser(cleanLogin);
    if (user?.exists === false) throw new Error('Текущий пользователь не найден.');
    const blockchainName = String(user?.blockchainName || `${cleanLogin}-${BCH_SUFFIX}`).trim();
    const freshNum = Number(user?.serverLastGlobalNumber);
    const freshHash = String(user?.serverLastGlobalHash || '').trim().toLowerCase();
    const freshCursor = {
      serverLastGlobalNumber: Number.isFinite(freshNum) ? freshNum : -1,
      serverLastGlobalHash: freshHash.length === 64 ? freshHash : ZERO_HASH_HEX,
    };

    const targetUser = await this.getUser(cleanToLogin);
    if (!targetUser?.exists) throw new Error('Пользователь цели не найден.');
    const toBlockchainName = String(targetUser?.blockchainName || `${cleanToLogin}-${BCH_SUFFIX}`).trim();

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const blockchainPrivatePkcs8 = savedKeys?.blockchainKey;
    if (!blockchainPrivatePkcs8) {
      throw new Error('На устройстве нет сохраненного приватного blockchainKey');
    }
    const privateKey = await importPkcs8Ed25519(blockchainPrivatePkcs8);

    const tryAdd = async (cursor) => {
      const blockNumber = Number(cursor?.serverLastGlobalNumber ?? -1) + 1;
      const prevBlockHash = String(cursor?.serverLastGlobalHash || ZERO_HASH_HEX);

      // Для CONNECTION в UI-MVP всегда стартуем новую line-цепочку:
      // prevLineNumber=-1, prevLineHash=0x00..00, thisLineNumber=-1.
      // target для user-связей указывает на HEADER пользователя (blockNumber=0).
      const bodyBytes = makeConnectionBodyBytes({
        lineCode: 0,
        prevLineNumber: -1,
        prevLineHashHex: ZERO_HASH_HEX,
        thisLineNumber: -1,
        toBlockchainName,
        toBlockNumber: 0,
        toBlockHashHex: ZERO_HASH_HEX,
      });

      const preimage = concatBytes(
        int16Bytes(0),
        hexToBytes(prevBlockHash),
        int32Bytes(2 + 32 + 4 + 4 + 8 + 2 + 2 + 2 + bodyBytes.length),
        int32Bytes(blockNumber),
        int64Bytes(Math.floor(Date.now() / 1000)),
        int16Bytes(3),
        int16Bytes(cleanSubType),
        int16Bytes(1),
        bodyBytes,
      );

      const hash32 = await sha256Bytes(preimage);
      const signatureBytes = await signBytes(privateKey, hash32);
      const fullBlock = concatBytes(preimage, int16Bytes(0x0100), signatureBytes);

      return this.ws.request('AddBlock', {
        blockchainName,
        blockNumber,
        prevBlockHash,
        blockBytesB64: bytesToBase64(fullBlock),
      });
    };

    let cursor = freshCursor;
    let response = await tryAdd(cursor);
    if (response.status !== 200) {
      const knownNum = Number(response?.payload?.serverLastGlobalNumber);
      const knownHash = String(response?.payload?.serverLastGlobalHash || '').trim().toLowerCase();
      if (Number.isFinite(knownNum) && knownHash.length === 64) {
        cursor = { serverLastGlobalNumber: knownNum, serverLastGlobalHash: knownHash };
        response = await tryAdd(cursor);
      }
    }

    if (response.status !== 200) throw opError('AddBlock', response);
    return response.payload || {};
  }

  async reportClientError(details) {
    try {
      const response = await this.ws.request('ClientErrorLog', details || {}, 3000);
      return response?.status === 200;
    } catch {
      return false;
    }
  }

  close() {
    this.ws.close();
  }
}
