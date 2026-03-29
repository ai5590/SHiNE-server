import { WsJsonClient } from './ws-client.js?v=20260327192619';
import {
  deriveEd25519FromPassword,
  exportEd25519PublicKeyB64,
  exportPkcs8B64,
  generateEd25519Pair,
  randomBase64,
  signBase64,
} from './crypto-utils.js?v=20260327192619';
import { saveEncryptedUserSecrets, saveSessionMaterial } from './key-vault.js?v=20260327192619';

const BCH_SUFFIX = '001';

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
  return new Error(`${op}: ${message} (${code})`);
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
    return this.createAuthSession(cleanLogin, keyBundle);
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

  async listSessions() {
    const response = await this.ws.request('ListSessions', {});
    if (response.status !== 200) throw opError('ListSessions', response);
    return response?.payload?.sessions || [];
  }

  async closeSession(sessionId) {
    const response = await this.ws.request('CloseActiveSession', { sessionId });
    if (response.status !== 200) throw opError('CloseActiveSession', response);
  }

  close() {
    this.ws.close();
  }
}
