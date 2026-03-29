import { chatMessages, wallet } from './mock-data.js?v=20260327192619';
import { AuthService } from './services/auth-service.js?v=20260327192619';

const clone = (value) => JSON.parse(JSON.stringify(value));
const SESSION_STORAGE_KEY = 'shine-ui-current-session-v1';

function loadStoredSession() {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
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

const storedSession = loadStoredSession();

export const state = {
  chats: clone(chatMessages),
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
    shineServer: 'wss://shineup.me/ws',
    arweaveServer: 'https://arweave.net',
    statuses: {
      solanaServer: 'idle',
      shineServer: 'idle',
      arweaveServer: 'idle',
    },
  },
  registrationDraft: {
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
    saveRoot: true,
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
};

export const authService = new AuthService(state.entrySettings.shineServer);

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
  state.entrySettings = {
    ...state.entrySettings,
    ...nextSettings,
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

export async function refreshSessions() {
  state.sessions = await authService.listSessions();
  return state.sessions;
}

export function terminateCurrentSession() {
  state.session.isAuthorized = false;
  state.session.login = '';
  state.session.sessionId = '';
  state.session.storagePwdInMemory = '';
  state.sessions = [];
  clearStoredSession();
  state.startHint = '';
}

export function refreshRegistrationBalance() {
  const next = (0.005 + Math.random() * 0.03).toFixed(4);
  state.registrationPayment.balanceSOL = next;
  return next;
}
