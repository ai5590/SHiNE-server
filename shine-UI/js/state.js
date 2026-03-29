import { chatMessages, wallet } from './mock-data.js?v=20260327192619';

const clone = (value) => JSON.parse(JSON.stringify(value));

export const state = {
  chats: clone(chatMessages),
  notificationsTab: 'replies',
  pageLabelCollapsed: false,
  session: {
    isAuthorized: false,
  },
  startHint: '',
  entrySettings: {
    language: 'ru',
    solanaServer: 'https://api.mainnet-beta.solana.com',
    shineServer: 'https://demo.shine.local',
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
  },
  loginDraft: {
    login: '',
    password: '',
  },
  registrationPayment: {
    walletAddress: wallet.publicAddress,
    balanceSOL: '0.0068',
  },
  keyStorage: {
    rootKey: 'RK-4Q8N-1SZP-71LM-AUTH-ROOT',
    blockchainKey: 'BK-SOL-19F2-CHAIN-ACCESS',
    deviceKey: 'DK-LOCAL-82XA-DEVICE-SIGN',
    saveRoot: false,
    saveBlockchain: true,
    saveDevice: true,
  },
  deviceConnect: {
    root: true,
    blockchain: true,
    device: true,
  },
};

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

  const looksLikeUrl = /^https?:\/\/[a-z0-9.-]+/i.test(normalized);
  const blockedWord = /(offline|down|fail|bad|broken|invalid)/i.test(normalized);
  return looksLikeUrl && !blockedWord ? 'available' : 'unavailable';
}

export function saveEntrySettings(nextSettings) {
  state.entrySettings = {
    ...state.entrySettings,
    ...nextSettings,
    statuses: {
      ...state.entrySettings.statuses,
      ...(nextSettings.statuses || {}),
    },
  };
  state.startHint = 'Настройки входа сохранены, адреса серверов обновлены.';
}

export function clearStartHint() {
  state.startHint = '';
}

export function authorizeSession() {
  state.session.isAuthorized = true;
  state.startHint = '';
}

export function terminateCurrentSession() {
  state.session.isAuthorized = false;
  state.startHint = '';
}

export function refreshRegistrationBalance() {
  const next = (0.005 + Math.random() * 0.03).toFixed(4);
  state.registrationPayment.balanceSOL = next;
  return next;
}
