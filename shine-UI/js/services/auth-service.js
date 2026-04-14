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
  channelNameErrorText,
  normalizeChannelDisplayName,
  toCanonicalChannelSlug,
  validateChannelDisplayName,
} from './channel-name-rules.js';
import {
  loadEncryptedUserSecrets,
  loadSessionMaterial,
  saveEncryptedUserSecrets,
  saveSessionMaterial,
} from './key-vault.js';

const BCH_SUFFIX = '001';
const ZERO64 = '0'.repeat(64);

const MSG_TYPE_TECH = 0;
const MSG_TYPE_TEXT = 1;
const MSG_TYPE_REACTION = 2;
const MSG_TYPE_CONNECTION = 3;

const MSG_SUBTYPE_TECH_CREATE_CHANNEL = 1;
const MSG_SUBTYPE_TEXT_POST = 10;
const MSG_SUBTYPE_TEXT_REPLY = 20;
const MSG_SUBTYPE_REACTION_LIKE = 1;
const MSG_SUBTYPE_REACTION_UNLIKE = 2;
const MSG_SUBTYPE_CONNECTION_FOLLOW = 30;
const MSG_SUBTYPE_CONNECTION_UNFOLLOW = 31;
const CREATE_CHANNEL_BODY_VERSION = 2;

function normalizeServerUrl(url) {
  const value = (url || '').trim();
  if (!value) return 'wss://shineup.me/ws';
  if (value.startsWith('ws://') || value.startsWith('wss://')) {
    try {
      const parsed = new URL(value);
      if (!parsed.pathname || parsed.pathname === '/') parsed.pathname = '/ws';
      return parsed.toString();
    } catch {
      return value;
    }
  }
  if (value.startsWith('https://') || value.startsWith('http://')) {
    try {
      const parsed = new URL(value);
      parsed.protocol = parsed.protocol === 'https:' ? 'wss:' : 'ws:';
      if (!parsed.pathname || parsed.pathname === '/') parsed.pathname = '/ws';
      return parsed.toString();
    } catch {
      return `${value.replace(/^http/, 'ws').replace(/\/$/, '')}/ws`;
    }
  }
  return value;
}

function opError(op, response) {
  const payload = response?.payload || {};
  const message = payload?.message || response?.message || payload?.error || response?.error || 'Unknown server error';
  const code = String(payload?.code || response?.code || payload?.error || response?.error || 'UNKNOWN').toUpperCase();
  const error = new Error(`${op}: ${message} (${code})`);
  error.op = op;
  error.code = code;
  error.status = response?.status || 0;
  return error;
}

function isLegacyCreateChannelFormatError(error) {
  const code = String(error?.code || '').trim().toUpperCase();
  const text = String(error?.message || '').toLowerCase();
  if (code === 'BAD_BLOCK_FORMAT') return true;
  return (
    text.includes('unknown body type/version') ||
    text.includes('unknown tech body type/version/subtype') ||
    text.includes('bad_block_format')
  );
}

function channelDescriptionParamKeyFromSelector(selector) {
  const owner = String(selector?.ownerBlockchainName || '').trim();
  const rootNo = Number(selector?.channelRootBlockNumber);
  const rootHash = String(selector?.channelRootBlockHash || '').trim().toLowerCase();
  if (!owner || !Number.isFinite(rootNo) || rootNo < 0 || !/^[0-9a-f]{64}$/.test(rootHash)) {
    return '';
  }
  return `channel_desc:${owner}:${rootNo}:${rootHash}`;
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
    const byte = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
    if (Number.isNaN(byte)) throw new Error('Некорректный hex');
    out[i] = byte;
  }
  return out;
}

function normalizeHex32(value, fallback = ZERO64) {
  const raw = String(value || '').trim().toLowerCase();
  if (!raw) return fallback;
  if (/^0+$/.test(raw)) return ZERO64;
  if (!/^[0-9a-f]{64}$/.test(raw)) throw new Error('Bad hash32 format');
  return raw;
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

function int8Byte(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || n > 255) throw new Error('Bad uint8 value');
  return new Uint8Array([n & 0xff]);
}

function int64Bytes(value) {
  const bytes = new Uint8Array(8);
  const view = new DataView(bytes.buffer);
  view.setBigInt64(0, BigInt(value), false);
  return bytes;
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

function makeReactionLikeBodyBytes({ toBlockchainName, toBlockNumber, toBlockHashHex }) {
  const cleanBch = String(toBlockchainName || '').trim();
  if (!cleanBch) throw new Error('toBlockchainName is required for like');

  const blockNumber = Number(toBlockNumber);
  if (!Number.isFinite(blockNumber) || blockNumber < 0) {
    throw new Error('Invalid toBlockNumber for like');
  }

  const bchBytes = utf8Bytes(cleanBch);
  if (bchBytes.length < 1 || bchBytes.length > 255) {
    throw new Error('toBlockchainName must be 1..255 bytes');
  }

  return concatBytes(
    int8Byte(bchBytes.length),
    bchBytes,
    int32Bytes(blockNumber),
    hexToBytes(normalizeHex32(toBlockHashHex))
  );
}

function makeTextReplyBodyBytes({ toBlockchainName, toBlockNumber, toBlockHashHex, text }) {
  const cleanBch = String(toBlockchainName || '').trim();
  if (!cleanBch) throw new Error('toBlockchainName is required for reply');

  const blockNumber = Number(toBlockNumber);
  if (!Number.isFinite(blockNumber) || blockNumber < 0) {
    throw new Error('Invalid toBlockNumber for reply');
  }

  const message = String(text || '').trim();
  if (!message) throw new Error('Reply text is required');

  const bchBytes = utf8Bytes(cleanBch);
  if (bchBytes.length < 1 || bchBytes.length > 255) {
    throw new Error('toBlockchainName must be 1..255 bytes');
  }

  const textBytes = utf8Bytes(message);
  if (textBytes.length < 1 || textBytes.length > 65535) {
    throw new Error('Reply text must be 1..65535 UTF-8 bytes');
  }

  return concatBytes(
    int8Byte(bchBytes.length),
    bchBytes,
    int32Bytes(blockNumber),
    hexToBytes(normalizeHex32(toBlockHashHex)),
    int16Bytes(textBytes.length),
    textBytes
  );
}

function makeConnectionBodyBytes({
  lineCode = 0,
  prevLineNumber = -1,
  prevLineHashHex = ZERO64,
  thisLineNumber = -1,
  toBlockchainName,
  toBlockNumber,
  toBlockHashHex,
}) {
  const cleanBch = String(toBlockchainName || '').trim();
  if (!cleanBch) throw new Error('toBlockchainName is required for connection');

  const blockNumber = Number(toBlockNumber);
  if (!Number.isFinite(blockNumber) || blockNumber < 0) {
    throw new Error('Invalid toBlockNumber for connection');
  }

  const bchBytes = utf8Bytes(cleanBch);
  if (bchBytes.length < 1 || bchBytes.length > 255) {
    throw new Error('toBlockchainName must be 1..255 bytes');
  }

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    hexToBytes(normalizeHex32(prevLineHashHex)),
    int32Bytes(thisLineNumber),
    int8Byte(bchBytes.length),
    bchBytes,
    int32Bytes(blockNumber),
    hexToBytes(normalizeHex32(toBlockHashHex))
  );
}

function makeCreateChannelBodyBytes({ lineCode, prevLineNumber, prevLineHashHex, thisLineNumber, channelName }) {
  const check = validateChannelDisplayName(channelName);
  if (!check.ok) throw new Error(channelNameErrorText(check.code));
  const cleanName = check.normalized;

  const nameBytes = utf8Bytes(cleanName);
  if (nameBytes.length < 1 || nameBytes.length > 255) {
    throw new Error('Channel name must be 1..255 bytes');
  }

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    hexToBytes(normalizeHex32(prevLineHashHex)),
    int32Bytes(thisLineNumber),
    int8Byte(nameBytes.length),
    nameBytes
  );
}

function normalizeChannelDescription(value) {
  const text = String(value == null ? '' : value).trim().replace(/\s+/g, ' ');
  const bytes = utf8Bytes(text);
  if (bytes.length > 200) {
    throw new Error('Описание канала слишком длинное: максимум 200 символов.');
  }
  return text;
}

function makeCreateChannelBodyV2Bytes({
  lineCode,
  prevLineNumber,
  prevLineHashHex,
  thisLineNumber,
  channelName,
  channelDescription = '',
}) {
  const check = validateChannelDisplayName(channelName);
  if (!check.ok) throw new Error(channelNameErrorText(check.code));
  const cleanName = check.normalized;
  const cleanDescription = normalizeChannelDescription(channelDescription);

  const nameBytes = utf8Bytes(cleanName);
  if (nameBytes.length < 1 || nameBytes.length > 255) {
    throw new Error('Channel name must be 1..255 bytes');
  }

  const descriptionBytes = utf8Bytes(cleanDescription);
  if (descriptionBytes.length > 200) {
    throw new Error('Описание канала слишком длинное: максимум 200 символов.');
  }

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    hexToBytes(normalizeHex32(prevLineHashHex)),
    int32Bytes(thisLineNumber),
    int8Byte(nameBytes.length),
    nameBytes,
    int16Bytes(descriptionBytes.length),
    descriptionBytes,
  );
}

function makeTextPostBodyBytes({ lineCode, prevLineNumber, prevLineHashHex, thisLineNumber, text }) {
  const message = String(text || '').trim();
  if (!message) throw new Error('Message text is required');

  const textBytes = utf8Bytes(message);
  if (textBytes.length < 1 || textBytes.length > 65535) {
    throw new Error('Message text must be 1..65535 UTF-8 bytes');
  }

  return concatBytes(
    int32Bytes(lineCode),
    int32Bytes(prevLineNumber),
    hexToBytes(normalizeHex32(prevLineHashHex)),
    int32Bytes(thisLineNumber),
    int16Bytes(textBytes.length),
    textBytes
  );
}

function normalizeMessageRefTarget(target, actionName = 'action') {
  const cleanBch = String(target?.blockchainName || '').trim();
  const cleanBlockNumber = Number(target?.blockNumber);
  const cleanBlockHash = String(target?.blockHash || '').trim().toLowerCase();

  if (!cleanBch) {
    throw new Error(`Missing message target blockchain for ${actionName}`);
  }
  if (!Number.isFinite(cleanBlockNumber) || cleanBlockNumber < 0) {
    throw new Error(`Invalid message target block number for ${actionName}`);
  }
  if (!/^[0-9a-f]{64}$/.test(cleanBlockHash) || /^0+$/.test(cleanBlockHash)) {
    throw new Error(`Invalid message target hash for ${actionName}`);
  }

  return {
    blockchainName: cleanBch,
    blockNumber: cleanBlockNumber,
    blockHash: cleanBlockHash,
  };
}

function buildBlockPreimage({ prevBlockHashHex, blockNumber, msgType, msgSubType, msgVersion = 1, bodyBytes }) {
  const prevHashBytes = hexToBytes(normalizeHex32(prevBlockHashHex));
  const body = bodyBytes || new Uint8Array(0);
  const blockSize = 2 + 32 + 4 + 4 + 8 + 2 + 2 + 2 + body.length;

  return concatBytes(
    int16Bytes(0),
    prevHashBytes,
    int32Bytes(blockSize),
    int32Bytes(blockNumber),
    int64Bytes(Math.floor(Date.now() / 1000)),
    int16Bytes(msgType),
    int16Bytes(msgSubType),
    int16Bytes(msgVersion),
    body
  );
}

export class AuthService {
  constructor(serverUrl) {
    this.serverUrl = normalizeServerUrl(serverUrl);
    this.ws = new WsJsonClient(this.serverUrl);
    this.headerHashCache = new Map();
    this.writeLocks = new Map();
  }

  async reconnect(serverUrl) {
    const normalized = normalizeServerUrl(serverUrl);
    if (normalized === this.serverUrl) return;
    this.ws.close();
    this.serverUrl = normalized;
    this.ws = new WsJsonClient(this.serverUrl);
    this.headerHashCache = new Map();
    this.writeLocks.clear();
  }

  runWriteLocked(lockKey, runAction) {
    const key = String(lockKey || '').trim() || 'write';
    if (this.writeLocks.has(key)) return this.writeLocks.get(key);

    const task = (async () => runAction())().finally(() => {
      this.writeLocks.delete(key);
    });

    this.writeLocks.set(key, task);
    return task;
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

  async getChannelMessages(channel, limit = 200, sort = 'asc', login = '') {
    const payload = { channel, limit, sort };
    const cleanLogin = String(login || '').trim();
    if (cleanLogin) payload.login = cleanLogin;
    const response = await this.ws.request('GetChannelMessages', payload);
    if (response.status !== 200) throw opError('GetChannelMessages', response);
    return response.payload || {};
  }

  async getMessageThread(message, depthUp = 20, depthDown = 2, limitChildrenPerNode = 50, login = '') {
    const payload = { message, depthUp, depthDown, limitChildrenPerNode };
    const cleanLogin = String(login || '').trim();
    if (cleanLogin) payload.login = cleanLogin;
    const response = await this.ws.request('GetMessageThread', payload);
    if (response.status !== 200) throw opError('GetMessageThread', response);
    return response.payload || {};
  }

  async addBlockSigned({ login, storagePwd, msgType, msgSubType, msgVersion = 1, bodyBytes }) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Missing login for AddBlock');
    if (!storagePwd) throw new Error('Missing storagePwd for AddBlock signing');

    const user = await this.getUser(cleanLogin);
    const blockchainName = String(user?.blockchainName || `${cleanLogin}-${BCH_SUFFIX}`).trim();
    const freshNum = Number(user?.serverLastGlobalNumber);
    const freshHash = normalizeHex32(user?.serverLastGlobalHash, ZERO64);
    const freshCursor = {
      serverLastGlobalNumber: Number.isFinite(freshNum) ? freshNum : -1,
      serverLastGlobalHash: freshHash,
    };

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const blockchainPrivatePkcs8 = savedKeys?.blockchainKey;
    if (!blockchainPrivatePkcs8) {
      throw new Error('Missing saved blockchain private key on device');
    }

    const privateKey = await importPkcs8Ed25519(blockchainPrivatePkcs8);

    const tryAdd = async (cursor) => {
      const blockNumber = Number(cursor?.serverLastGlobalNumber ?? -1) + 1;
      const prevBlockHash = normalizeHex32(cursor?.serverLastGlobalHash, ZERO64);
      const preimage = buildBlockPreimage({
        prevBlockHashHex: prevBlockHash,
        blockNumber,
        msgType,
        msgSubType,
        msgVersion,
        bodyBytes,
      });

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
      const knownHash = String(response?.payload?.serverLastGlobalHash || '');
      if (Number.isFinite(knownNum) && /^[0-9a-fA-F]{64}$/.test(knownHash)) {
        cursor = { serverLastGlobalNumber: knownNum, serverLastGlobalHash: knownHash.toLowerCase() };
        response = await tryAdd(cursor);
      }
    }

    if (response.status !== 200) throw opError('AddBlock', response);

    const payload = response.payload || {};
    const acceptedNum = Number(payload?.serverLastGlobalNumber);
    const acceptedHash = normalizeHex32(payload?.serverLastGlobalHash, ZERO64);
    if (Number.isFinite(acceptedNum) && acceptedNum === 0 && acceptedHash !== ZERO64) {
      this.headerHashCache.set(blockchainName, acceptedHash);
    }

    return payload;
  }

  async ensureChainInitializedForLineOps(login, storagePwd) {
    const current = await this.getUser(login);
    const lastNum = Number(current?.serverLastGlobalNumber);
    if (Number.isFinite(lastNum) && lastNum >= 0) return current;
    if (!(Number.isFinite(lastNum) && lastNum === -1)) return current;

    // Bootstrap an empty chain with a minimal USER_PARAM block so line-based
    // channel operations have a valid anchor at block #0.
    await this.addBlockUserParam({
      login,
      storagePwd,
      param: 'shine',
      value: 'yes',
    });

    return this.getUser(login);
  }

  async addBlockLike({ login, message, storagePwd }) {
    const cleanLogin = String(login || '').trim();
    const target = normalizeMessageRefTarget(message, 'like');
    const key = `like:${cleanLogin}:${target.blockchainName}:${target.blockNumber}:${target.blockHash}`;

    return this.runWriteLocked(key, async () => {
      const bodyBytes = makeReactionLikeBodyBytes({
        toBlockchainName: target.blockchainName,
        toBlockNumber: target.blockNumber,
        toBlockHashHex: target.blockHash,
      });

      return this.addBlockSigned({
        login: cleanLogin,
        storagePwd,
        msgType: MSG_TYPE_REACTION,
        msgSubType: MSG_SUBTYPE_REACTION_LIKE,
        msgVersion: 1,
        bodyBytes,
      });
    });
  }

  async addBlockUnlike({ login, message, storagePwd }) {
    const cleanLogin = String(login || '').trim();
    const target = normalizeMessageRefTarget(message, 'unlike');
    const key = `unlike:${cleanLogin}:${target.blockchainName}:${target.blockNumber}:${target.blockHash}`;

    return this.runWriteLocked(key, async () => {
      const bodyBytes = makeReactionLikeBodyBytes({
        toBlockchainName: target.blockchainName,
        toBlockNumber: target.blockNumber,
        toBlockHashHex: target.blockHash,
      });

      return this.addBlockSigned({
        login: cleanLogin,
        storagePwd,
        msgType: MSG_TYPE_REACTION,
        msgSubType: MSG_SUBTYPE_REACTION_UNLIKE,
        msgVersion: 1,
        bodyBytes,
      });
    });
  }

  async addBlockReply({ login, message, text, storagePwd }) {
    const cleanLogin = String(login || '').trim();
    const cleanText = String(text || '').trim();
    const target = normalizeMessageRefTarget(message, 'reply');
    const key = `reply:${cleanLogin}:${target.blockchainName}:${target.blockNumber}:${target.blockHash}:${cleanText}`;

    return this.runWriteLocked(key, async () => {
      const bodyBytes = makeTextReplyBodyBytes({
        toBlockchainName: target.blockchainName,
        toBlockNumber: target.blockNumber,
        toBlockHashHex: target.blockHash,
        text: cleanText,
      });

      return this.addBlockSigned({
        login: cleanLogin,
        storagePwd,
        msgType: MSG_TYPE_TEXT,
        msgSubType: MSG_SUBTYPE_TEXT_REPLY,
        msgVersion: 1,
        bodyBytes,
      });
    });
  }

  async addBlockFollowUser({ login, targetLogin, storagePwd, unfollow = false }) {
    const cleanTargetLogin = String(targetLogin || '').trim().replace(/^@+/, '');
    if (!cleanTargetLogin) throw new Error('Target login is required');
    const cleanLogin = String(login || '').trim();
    const key = `${unfollow ? 'unfollow-user' : 'follow-user'}:${cleanLogin}:${cleanTargetLogin.toLowerCase()}`;

    return this.runWriteLocked(key, async () => {
      const targetUser = await this.getUser(cleanTargetLogin);
      if (!targetUser?.exists) throw new Error('Target user not found');
      const targetHeaderHash = await this.resolveHeaderHashForBlockchain(targetUser.blockchainName);

      return this.addBlockFollowChannel({
        login: cleanLogin,
        storagePwd,
        targetBlockchainName: targetUser.blockchainName,
        targetBlockNumber: 0,
        targetBlockHashHex: targetHeaderHash,
        unfollow,
      });
    });
  }

  async addBlockFollowChannel({
    login,
    storagePwd,
    targetBlockchainName,
    targetBlockNumber,
    targetBlockHashHex,
    unfollow = false,
  }) {
    const cleanLogin = String(login || '').trim();
    const cleanTargetBch = String(targetBlockchainName || '').trim();
    const cleanTargetBlockNumber = Number(targetBlockNumber);
    if (!cleanTargetBch) throw new Error('Target blockchain is required');
    if (!Number.isFinite(cleanTargetBlockNumber) || cleanTargetBlockNumber < 0) {
      throw new Error('Invalid target block number');
    }
    const seedHash = normalizeHex32(targetBlockHashHex, ZERO64);
    const key = `${unfollow ? 'unfollow-channel' : 'follow-channel'}:${cleanLogin}:${cleanTargetBch}:${cleanTargetBlockNumber}:${seedHash}`;

    return this.runWriteLocked(key, async () => {
      let targetHashHex = seedHash;
      if (targetHashHex === ZERO64) {
        targetHashHex = cleanTargetBlockNumber === 0
          ? await this.resolveHeaderHashForBlockchain(cleanTargetBch)
          : await this.getBlockHashByNumber(cleanTargetBch, cleanTargetBlockNumber);
      }

      const bodyBytes = makeConnectionBodyBytes({
        lineCode: 0,
        prevLineNumber: -1,
        prevLineHashHex: ZERO64,
        thisLineNumber: -1,
        toBlockchainName: cleanTargetBch,
        toBlockNumber: cleanTargetBlockNumber,
        toBlockHashHex: targetHashHex,
      });

      return this.addBlockSigned({
        login: cleanLogin,
        storagePwd,
        msgType: MSG_TYPE_CONNECTION,
        msgSubType: unfollow ? MSG_SUBTYPE_CONNECTION_UNFOLLOW : MSG_SUBTYPE_CONNECTION_FOLLOW,
        msgVersion: 1,
        bodyBytes,
      });
    });
  }

  async getBlockHashByNumber(blockchainName, blockNumber) {
    const cleanBlockNumber = Number(blockNumber);
    try {
      const payload = await this.getMessageThread(
        {
          blockchainName: String(blockchainName || '').trim(),
          blockNumber: cleanBlockNumber,
          blockHash: ZERO64,
        },
        0,
        0,
        1
      );
      const hash = payload?.focus?.messageRef?.blockHash;
      return normalizeHex32(hash, ZERO64);
    } catch (error) {
      if (cleanBlockNumber === 0 && Number(error?.status) === 404) {
        return ZERO64;
      }
      throw error;
    }
  }

  async resolveHeaderHashForBlockchain(blockchainName) {
    const cleanBch = String(blockchainName || '').trim();
    if (!cleanBch) throw new Error('Missing blockchainName');

    if (this.headerHashCache.has(cleanBch)) {
      const cached = normalizeHex32(this.headerHashCache.get(cleanBch), ZERO64);
      if (cached !== ZERO64) return cached;
      this.headerHashCache.delete(cleanBch);
    }

    const headerHash = await this.getBlockHashByNumber(cleanBch, 0);
    if (headerHash !== ZERO64) {
      this.headerHashCache.set(cleanBch, headerHash);
    } else {
      this.headerHashCache.delete(cleanBch);
    }
    return headerHash;
  }

  async listOwnChannelsForBlockchain(login, blockchainName) {
    const feed = await this.listSubscriptionsFeed(login, 500);
    const own = feed?.ownedChannels || [];
    return own
      .filter((item) => String(item?.channel?.ownerBlockchainName || '') === blockchainName)
      .map((item) => ({
        rootBlockNumber: Number(item?.channel?.channelRoot?.blockNumber),
        rootBlockHash: normalizeHex32(item?.channel?.channelRoot?.blockHash, ZERO64),
        channelName: String(item?.channel?.channelName || ''),
      }))
      .filter((item) => Number.isFinite(item.rootBlockNumber) && item.rootBlockNumber >= 0);
  }

  async addBlockCreateChannel({ login, channelName, channelDescription = '', storagePwd }) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Missing login');

    const check = validateChannelDisplayName(channelName);
    if (!check.ok) throw new Error(channelNameErrorText(check.code));
    const cleanChannelName = normalizeChannelDisplayName(check.normalized);
    const cleanChannelDescription = normalizeChannelDescription(channelDescription);
    const channelSlug = toCanonicalChannelSlug(cleanChannelName);

    const key = `create-channel:${cleanLogin}:${channelSlug || cleanChannelName.toLowerCase()}`;
    return this.runWriteLocked(key, async () => {
      const user = await this.ensureChainInitializedForLineOps(cleanLogin, storagePwd);
      const blockchainName = String(user?.blockchainName || `${cleanLogin}-${BCH_SUFFIX}`).trim();
      const userLastGlobalNumber = Number(user?.serverLastGlobalNumber);
      const userLastGlobalHash = normalizeHex32(user?.serverLastGlobalHash, ZERO64);

      const ownChannels = await this.listOwnChannelsForBlockchain(cleanLogin, blockchainName);
      const createdChannels = ownChannels
        .filter((item) => item.rootBlockNumber > 0)
        .sort((a, b) => a.rootBlockNumber - b.rootBlockNumber);

      let prevLineNumber = 0;
      let prevLineHashHex = (
        Number.isFinite(userLastGlobalNumber) &&
        userLastGlobalNumber === 0 &&
        userLastGlobalHash !== ZERO64
      )
        ? userLastGlobalHash
        : await this.resolveHeaderHashForBlockchain(blockchainName);
      let thisLineNumber = 1;

      if (createdChannels.length > 0) {
        const last = createdChannels[createdChannels.length - 1];
        prevLineNumber = last.rootBlockNumber;
        prevLineHashHex = normalizeHex32(last.rootBlockHash, ZERO64);
        thisLineNumber = createdChannels.length + 1;
      }

      const submitCreate = async (useV2) => {
        const bodyBytes = useV2
          ? makeCreateChannelBodyV2Bytes({
            lineCode: 0,
            prevLineNumber,
            prevLineHashHex,
            thisLineNumber,
            channelName: cleanChannelName,
            channelDescription: cleanChannelDescription,
          })
          : makeCreateChannelBodyBytes({
            lineCode: 0,
            prevLineNumber,
            prevLineHashHex,
            thisLineNumber,
            channelName: cleanChannelName,
          });

        return this.addBlockSigned({
          login: cleanLogin,
          storagePwd,
          msgType: MSG_TYPE_TECH,
          msgSubType: MSG_SUBTYPE_TECH_CREATE_CHANNEL,
          msgVersion: useV2 ? CREATE_CHANNEL_BODY_VERSION : 1,
          bodyBytes,
        });
      };

      let payload;
      let usedLegacyDescriptionFallback = false;
      let savedDescriptionViaUserParam = false;
      try {
        payload = await submitCreate(true);
      } catch (error) {
        if (!isLegacyCreateChannelFormatError(error)) throw error;
        payload = await submitCreate(false);
        usedLegacyDescriptionFallback = true;
      }

      const selector = {
        ownerBlockchainName: blockchainName,
        channelRootBlockNumber: Number(payload?.serverLastGlobalNumber),
        channelRootBlockHash: normalizeHex32(payload?.serverLastGlobalHash, ZERO64),
      };

      if (usedLegacyDescriptionFallback && cleanChannelDescription) {
        const param = channelDescriptionParamKeyFromSelector(selector);
        if (!param) {
          throw new Error('Не удалось сохранить описание канала: некорректный идентификатор канала.');
        }
        await this.addBlockUserParam({
          login: cleanLogin,
          storagePwd,
          param,
          value: JSON.stringify({ v: cleanChannelDescription }),
        });
        savedDescriptionViaUserParam = true;
      }

      return {
        ...payload,
        usedLegacyDescriptionFallback,
        savedDescriptionViaUserParam,
        channel: {
          ...selector,
        },
      };
    });
  }

  async addBlockTextPost({ login, channel, text, storagePwd }) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Missing login');
    const cleanText = String(text || '').trim();
    const selector = channel || {};
    const owner = String(selector?.ownerBlockchainName || '').trim();
    const root = Number(selector?.channelRootBlockNumber);
    const key = `text-post:${cleanLogin}:${owner}:${root}:${cleanText}`;

    return this.runWriteLocked(key, async () => {
      const user = await this.ensureChainInitializedForLineOps(cleanLogin, storagePwd);
      const blockchainName = String(user?.blockchainName || `${cleanLogin}-${BCH_SUFFIX}`).trim();
      const userLastGlobalNumber = Number(user?.serverLastGlobalNumber);
      const userLastGlobalHash = normalizeHex32(user?.serverLastGlobalHash, ZERO64);

      const ownerBlockchainName = owner;
      const lineCode = root;
      if (!ownerBlockchainName || !Number.isFinite(lineCode) || lineCode < 0) {
        throw new Error('Invalid channel selector');
      }
      if (ownerBlockchainName !== blockchainName) {
        throw new Error('Posting is allowed only to your own channels');
      }

      let rootHashHex = normalizeHex32(selector?.channelRootBlockHash, ZERO64);
      if (lineCode === 0) {
        rootHashHex = (
          Number.isFinite(userLastGlobalNumber) &&
          userLastGlobalNumber === 0 &&
          userLastGlobalHash !== ZERO64
        )
          ? userLastGlobalHash
          : await this.resolveHeaderHashForBlockchain(blockchainName);
      } else if (rootHashHex === ZERO64) {
        const ownChannels = await this.listOwnChannelsForBlockchain(cleanLogin, blockchainName);
        const rootChannel = ownChannels.find((item) => item.rootBlockNumber === lineCode);
        if (!rootChannel) throw new Error('Channel root not found');
        rootHashHex = normalizeHex32(rootChannel.rootBlockHash, ZERO64);
      }

      const bodyBytes = makeTextPostBodyBytes({
        lineCode,
        prevLineNumber: lineCode,
        prevLineHashHex: rootHashHex,
        thisLineNumber: 0,
        text: cleanText,
      });

      const payload = await this.addBlockSigned({
        login: cleanLogin,
        storagePwd,
        msgType: MSG_TYPE_TEXT,
        msgSubType: MSG_SUBTYPE_TEXT_POST,
        msgVersion: 1,
        bodyBytes,
      });

      return {
        ...payload,
        channel: {
          ownerBlockchainName,
          channelRootBlockNumber: lineCode,
          channelRootBlockHash: rootHashHex,
        },
      };
    });
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
      serverLastGlobalHash: freshHash.length === 64 ? freshHash : '0'.repeat(64),
    };

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const blockchainPrivatePkcs8 = savedKeys?.blockchainKey;
    if (!blockchainPrivatePkcs8) {
      throw new Error('На устройстве нет сохраненного приватного blockchainKey');
    }

    const privateKey = await importPkcs8Ed25519(blockchainPrivatePkcs8);

    const tryAdd = async (cursor) => {
      const blockNumber = Number(cursor?.serverLastGlobalNumber ?? -1) + 1;
      const prevBlockHash = String(cursor?.serverLastGlobalHash || '0'.repeat(64));

      // Для USER_PARAM отправляем старт новой line-цепочки:
      // prevLineNumber=-1, prevLineHash=0x00..00, thisLineNumber=-1.
      // Этот формат соответствует BodyHasLine правилам на сервере.
      const bodyBytes = makeUserParamBodyBytes({
        lineCode: 0,
        prevLineNumber: -1,
        prevLineHashHex: '0'.repeat(64),
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
