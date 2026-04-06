import { WsJsonClient } from './ws-client.js?v=20260405171816';
import {
  deriveEd25519FromPassword,
  exportEd25519PublicKeyB64,
  exportPkcs8B64,
  generateEd25519Pair,
  importPkcs8Ed25519,
  randomBase64,
  signBase64,
} from './crypto-utils.js?v=20260405171816';
import {
  loadEncryptedUserSecrets,
  loadSessionMaterial,
  saveEncryptedUserSecrets,
  saveSessionMaterial,
} from './key-vault.js?v=20260405171816';

const BCH_SUFFIX = '001';
const USER_PARAMETER_PREFIX = 'SHiNe/UserParameter:';

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

  async addUserParamBlock({ login, param, value, timeMs = Date.now(), storagePwd }) {
    const cleanLogin = (login || '').trim();
    const cleanParam = (param || '').trim();
    if (!cleanLogin || !cleanParam) throw new Error('Не переданы login/param');
    if (!storagePwd) throw new Error('Не передан storagePwd для подписи блокчейна');

    const user = await this.getUser(cleanLogin);
    const blockchainKey = String(user?.blockchainKey || '').trim();
    if (!blockchainKey) throw new Error('GetUser не вернул blockchainKey');

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const blockchainPrivatePkcs8 = savedKeys?.blockchainKey;
    if (!blockchainPrivatePkcs8) throw new Error('На устройстве нет сохраненного приватного blockchainKey');

    const privateKey = await importPkcs8Ed25519(blockchainPrivatePkcs8);
    const cleanValue = String(value ?? '');
    const signText = `${USER_PARAMETER_PREFIX}${cleanLogin}${cleanParam}${timeMs}${cleanValue}`;
    const signature = await signBase64(privateKey, signText);

    const response = await this.ws.request('AddUserParamBlock', {
      login: cleanLogin,
      param: cleanParam,
      time_ms: Number(timeMs),
      value: cleanValue,
      blockchain_key: blockchainKey,
      signature,
    });

    if (response.status !== 200) throw opError('AddUserParamBlock', response);
    return response.payload || {};
  }
  async listUserParams(login) {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin) throw new Error('Не передан login');
    const response = await this.ws.request('ListUserParams', { login: cleanLogin });
    if (response.status !== 200) throw opError('ListUserParams', response);
    return response.payload || {};
  }

  async upsertUserParam({ login, param, value, timeMs = Date.now(), storagePwd }) {
    const cleanLogin = (login || '').trim();
    const cleanParam = (param || '').trim();
    if (!cleanLogin || !cleanParam) throw new Error('Не переданы login/param');
    if (!storagePwd) throw new Error('Не передан storagePwd для подписи UserParam');

    const user = await this.getUser(cleanLogin);
    const deviceKey = String(user?.deviceKey || '').trim();
    if (!deviceKey) {
      throw new Error('GetUser не вернул deviceKey для подписи UserParam');
    }

    const savedKeys = await loadEncryptedUserSecrets(cleanLogin, storagePwd);
    const devicePrivatePkcs8 = savedKeys?.deviceKey;
    if (!devicePrivatePkcs8) {
      throw new Error('На устройстве нет сохраненного приватного deviceKey');
    }

    const privateKey = await importPkcs8Ed25519(devicePrivatePkcs8);
    const cleanValue = String(value ?? '');
    const signText = `${USER_PARAMETER_PREFIX}${cleanLogin}${cleanParam}${timeMs}${cleanValue}`;
    const signature = await signBase64(privateKey, signText);

    const response = await this.ws.request('UpsertUserParam', {
      login: cleanLogin,
      param: cleanParam,
      time_ms: Number(timeMs),
      value: cleanValue,
      device_key: deviceKey,
      signature,
    });

    if (response.status !== 200) throw opError('UpsertUserParam', response);
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
