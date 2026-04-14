import { chatMessages, wallet } from './mock-data.js';
import { AuthService } from './services/auth-service.js';
import { clearClientAuthData } from './services/key-vault.js';

const clone = (value) => JSON.parse(JSON.stringify(value));
const SESSION_STORAGE_KEY = 'shine-ui-current-session-v1';
const REACTIONS_STORAGE_KEY = 'shine-ui-message-reactions-v2';
const MAX_APP_LOG_ENTRIES = 500;
const INVALID_SESSION_CODES = new Set([
  'NOT_AUTHENTICATED',
  'SESSION_NOT_FOUND',
  'SESSION_KEY_NOT_ACTUAL',
  'SESSION_OF_ANOTHER_USER',
]);

function readLocalWsOverrideUrl() {
  try {
    const params = new URLSearchParams(window.location.search);

    const explicitWsUrl = String(params.get('wsUrl') || '').trim();
    if (explicitWsUrl) {
      if (explicitWsUrl.startsWith('ws://') || explicitWsUrl.startsWith('wss://')) {
        try {
          const parsed = new URL(explicitWsUrl);
          if (!parsed.pathname || parsed.pathname === '/') parsed.pathname = '/ws';
          return parsed.toString();
        } catch {
          return explicitWsUrl;
        }
      }
      if (explicitWsUrl.startsWith('http://') || explicitWsUrl.startsWith('https://')) {
        try {
          const parsed = new URL(explicitWsUrl);
          parsed.protocol = parsed.protocol === 'https:' ? 'wss:' : 'ws:';
          if (!parsed.pathname || parsed.pathname === '/') parsed.pathname = '/ws';
          return parsed.toString();
        } catch {
          return `${explicitWsUrl.replace(/^http/, 'ws').replace(/\/$/, '')}/ws`;
        }
      }
      return '';
    }

    const value = params.get('localWsPort');
    const asNum = Number(value);
    if (!Number.isFinite(asNum)) return '';
    const port = Math.trunc(asNum);
    if (port <= 0 || port > 65535) return '';
    const isHttpsPage = window.location.protocol === 'https:';
    const forceInsecureLocal = params.get('allowInsecureLocalWs') === '1';
    const scheme = (isHttpsPage && !forceInsecureLocal) ? 'wss' : 'ws';
    return `${scheme}://localhost:${port}/ws`;
  } catch {
    return '';
  }
}

function inferTunnelWsUrl() {
  try {
    const host = String(window.location.host || '').toLowerCase();
    const isTunnelHost = (
      host.endsWith('.ngrok-free.dev') ||
      host.endsWith('.ngrok.io') ||
      host.endsWith('.trycloudflare.com')
    );
    if (!isTunnelHost) return '';
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ws`;
  } catch {
    return '';
  }
}

const LOCAL_WS_OVERRIDE_URL = readLocalWsOverrideUrl() || inferTunnelWsUrl();
const DEFAULT_SHINE_SERVER = 'wss://shineup.me/ws';

function loadStoredSession() {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function loadStoredReactions() {
  try {
    const raw = localStorage.getItem(REACTIONS_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return parsed;
  } catch {
    return {};
  }
}

function persistStoredReactions(reactions) {
  try {
    localStorage.setItem(REACTIONS_STORAGE_KEY, JSON.stringify(reactions || {}));
  } catch {
    // ignore storage errors
  }
}

function persistSession(session) {
  try {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  } catch {
    // ignore quota/storage errors for prototype
  }
}

function clearStoredSession() {
  try {
    localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // ignore
  }
}

function createInitialState({ withStoredSession = true } = {}) {
  const storedSession = withStoredSession ? loadStoredSession() : null;
  const storedReactions = loadStoredReactions();
  const initialShineServer = LOCAL_WS_OVERRIDE_URL || DEFAULT_SHINE_SERVER;

  return {
    chats: clone(chatMessages),
    contacts: [],
    appLog: [],
    incomingDedup: {},
    notificationsTab: 'replies',
    pageLabelCollapsed: false,
    session: {
      isAuthorized: false,
      login: storedSession?.login || '',
      sessionId: storedSession?.sessionId || '',
      storagePwdInMemory: '',
    },
    startHint: '',
    entrySettings: {
      language: 'ru',
      solanaServer: 'https://api.mainnet-beta.solana.com',
      shineServer: initialShineServer,
      arweaveServer: 'https://arweave.net',
      statuses: {
        solanaServer: 'idle',
        shineServer: 'idle',
        arweaveServer: 'idle',
      },
    },
    registrationDraft: {
      flowType: '',
      login: '',
      password: '',
      sessionId: '',
      storagePwd: '',
      pendingKeyBundle: null,
      pendingSessionMaterial: null,
    },
    loginDraft: {
      login: storedSession?.login || '',
      password: '',
    },
    registrationPayment: {
      walletAddress: wallet.publicAddress,
      balanceSOL: '0.0068',
    },
    keyStorage: {
      rootKey: 'Ключ root хранится в зашифрованном виде',
      blockchainKey: 'Ключ blockchain хранится в зашифрованном виде',
      deviceKey: 'Ключ device хранится в зашифрованном виде',
      saveRoot: false,
      saveBlockchain: true,
      saveDevice: true,
    },
    deviceConnect: {
      root: true,
      blockchain: true,
      device: true,
    },
    authUi: {
      busy: false,
      error: '',
      info: '',
    },
    sessions: [],
    channelsFeed: null,
    channelsIndex: {},
    localChannelPosts: {},
    messageReactions: storedReactions,
  };
}

export const state = createInitialState();

export const authService = new AuthService(state.entrySettings.shineServer);
let onSessionReset = null;

export function getChatMessages(chatId) {
  if (!state.chats[chatId]) {
    state.chats[chatId] = [];
  }
  return state.chats[chatId];
}

export function addChatMessage(chatId, text) {
  const message = text.trim();
  if (!message) return;
  getChatMessages(chatId).push({ from: 'out', text: message });
}


export function addIncomingMessage(chatId, text, messageId = '') {
  const msg = text?.trim();
  if (!msg) return false;
  if (messageId && state.incomingDedup[messageId]) return false;
  if (messageId) state.incomingDedup[messageId] = true;
  getChatMessages(chatId).push({ from: 'in', text: msg, messageId });
  return true;
}

export function setContacts(list) {
  state.contacts = Array.isArray(list) ? [...list] : [];
}

function toText(value) {
  if (typeof value === 'string') return value;
  if (value == null) return '';
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function addAppLogEntry({
  level = 'info',
  source = 'ui',
  message = '',
  details = '',
} = {}) {
  const cleanMessage = String(message || '').trim();
  if (!cleanMessage) return;
  const cleanLevel = String(level || 'info').trim().toLowerCase();
  const normalizedLevel = (cleanLevel === 'error' || cleanLevel === 'warn') ? cleanLevel : 'info';

  state.appLog.push({
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`,
    ts: Date.now(),
    level: normalizedLevel,
    source: String(source || 'ui').trim() || 'ui',
    message: cleanMessage,
    details: toText(details),
  });

  if (state.appLog.length > MAX_APP_LOG_ENTRIES) {
    state.appLog.splice(0, state.appLog.length - MAX_APP_LOG_ENTRIES);
  }
}

export function getAppLogEntries() {
  return [...state.appLog];
}

export function clearAppLogEntries() {
  state.appLog = [];
}

export function togglePageLabel() {
  state.pageLabelCollapsed = !state.pageLabelCollapsed;
}

export function ensureChat(chatId) {
  return getChatMessages(chatId);
}

export function checkServerAvailability(address) {
  const normalized = address.trim().toLowerCase();
  if (!normalized) return 'unavailable';

  const looksLikeUrl = /^(https?:\/\/|wss?:\/\/)[a-z0-9.-]+/i.test(normalized);
  const blockedWord = /(offline|down|fail|bad|broken|invalid)/i.test(normalized);
  return looksLikeUrl && !blockedWord ? 'available' : 'unavailable';
}

export async function saveEntrySettings(nextSettings) {
  const forcedShineServer = LOCAL_WS_OVERRIDE_URL || nextSettings.shineServer;
  state.entrySettings = {
    ...state.entrySettings,
    ...nextSettings,
    shineServer: forcedShineServer,
    statuses: {
      ...state.entrySettings.statuses,
      ...(nextSettings.statuses || {}),
    },
  };
  await authService.reconnect(state.entrySettings.shineServer);
  state.startHint = 'Настройки входа сохранены, адреса серверов обновлены.';
}

export function clearStartHint() {
  state.startHint = '';
}

export function setAuthBusy(flag) {
  state.authUi.busy = flag;
}

export function setAuthError(message) {
  state.authUi.error = message || '';
}

export function setAuthInfo(message) {
  state.authUi.info = message || '';
}

export function clearAuthMessages() {
  state.authUi.error = '';
  state.authUi.info = '';
}

export function authorizeSession({ login, sessionId, storagePwd }) {
  state.session.isAuthorized = true;
  state.session.login = login;
  state.session.sessionId = sessionId;
  state.session.storagePwdInMemory = storagePwd;
  persistSession({
    isAuthorized: true,
    login,
    sessionId,
  });
  state.startHint = '';
}

export function setSessionResetHandler(handler) {
  onSessionReset = typeof handler === 'function' ? handler : null;
}

export function isSessionInvalidError(error) {
  return INVALID_SESSION_CODES.has(error?.code);
}

export async function refreshSessions() {
  state.sessions = await authService.listSessions();
  return state.sessions;
}

function resetStateForSignedOut() {
  const next = createInitialState({ withStoredSession: false });
  state.chats = next.chats;
  state.notificationsTab = next.notificationsTab;
  state.session = next.session;
  state.startHint = next.startHint;
  state.registrationDraft = next.registrationDraft;
  state.loginDraft = next.loginDraft;
  state.registrationPayment = next.registrationPayment;
  state.keyStorage = next.keyStorage;
  state.deviceConnect = next.deviceConnect;
  state.authUi = next.authUi;
  state.sessions = next.sessions;
  state.channelsFeed = next.channelsFeed;
  state.channelsIndex = next.channelsIndex;
  state.localChannelPosts = next.localChannelPosts;
  state.messageReactions = next.messageReactions;
}

export async function terminateCurrentSession({ infoMessage = '' } = {}) {
  clearStoredSession();
  resetStateForSignedOut();
  authService.close();
  try {
    await clearClientAuthData();
  } catch {
    // ignore cleanup errors in prototype mode
  }
  if (infoMessage) {
    state.startHint = infoMessage;
  }
  if (onSessionReset) {
    onSessionReset();
  }
}

export function refreshRegistrationBalance() {
  const next = (0.005 + Math.random() * 0.03).toFixed(4);
  state.registrationPayment.balanceSOL = next;
  return next;
}

export function setChannelsFeed(feed, index) {
  state.channelsFeed = feed || null;
  state.channelsIndex = index || {};
}

export function getLocalChannelPosts(channelId) {
  if (!channelId) return [];
  if (!state.localChannelPosts[channelId]) {
    state.localChannelPosts[channelId] = [];
  }
  return state.localChannelPosts[channelId];
}

export function addLocalChannelPost(channelId, post) {
  if (!channelId) return;
  const text = post?.body?.trim();
  if (!text) return;

  getLocalChannelPosts(channelId).push({
    title: post.title || `${state.session.login || 'Вы'} • сейчас`,
    body: text,
  });
}

function makeMessageReactionKey(messageRef, login = state.session.login) {
  const bch = String(messageRef?.blockchainName || '').trim();
  const blockNumber = Number(messageRef?.blockNumber);
  const blockHash = String(messageRef?.blockHash || '').trim().toLowerCase();
  const cleanLogin = String(login || '').trim().toLowerCase();
  if (!cleanLogin || !bch || !Number.isFinite(blockNumber) || blockNumber < 0 || !blockHash) return '';
  return `${cleanLogin}|${bch}|${blockNumber}|${blockHash}`;
}

export function getMessageReactionState(messageRef) {
  const key = makeMessageReactionKey(messageRef);
  if (!key) return '';
  return state.messageReactions[key] || '';
}

export function setMessageReactionState(messageRef, nextState) {
  const key = makeMessageReactionKey(messageRef);
  if (!key) return;
  const normalized = String(nextState || '').trim().toLowerCase();
  if (normalized === 'liked' || normalized === 'unliked') {
    state.messageReactions[key] = normalized;
    persistStoredReactions(state.messageReactions);
    return;
  }
  delete state.messageReactions[key];
  persistStoredReactions(state.messageReactions);
}
