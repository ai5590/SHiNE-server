import { navigate, getRoute, PRE_AUTH_PAGES } from './router.js';
import { renderToolbar } from './components/toolbar.js';
import { captureClientError, setClientErrorTransport } from './services/client-error-reporter.js';
import { initPwaPush } from './services/pwa-push-service.js';
import { handleIncomingCallInvite, handleIncomingCallSignal } from './services/call-service.js';
import {
  authService,
  authorizeSession,
  isSessionInvalidError,
  refreshSessions,
  setSessionResetHandler,
  state,
  terminateCurrentSession,
  addIncomingMessage,
  setContacts,
} from './state.js';

import * as startView from './pages/start-view.js';
import * as entrySettingsView from './pages/entry-settings-view.js';
import * as registerView from './pages/register-view.js';
import * as registrationPaymentView from './pages/registration-payment-view.js';
import * as registrationKeysView from './pages/registration-keys-view.js';
import * as topupView from './pages/topup-view.js';
import * as loginView from './pages/login-view.js';
import * as loginCameraView from './pages/login-camera-view.js';
import * as loginPasswordView from './pages/login-password-view.js';
import * as keyStorageView from './pages/key-storage-view.js';

import * as profileView from './pages/profile-view.js';
import * as walletView from './pages/wallet-view.js';
import * as settingsView from './pages/settings-view.js';
import * as serverSettingsView from './pages/server-settings-view.js';
import * as deviceView from './pages/device-view.js';
import * as connectDeviceView from './pages/connect-device-view.js';
import * as deviceQrView from './pages/device-qr-view.js';
import * as deviceCameraView from './pages/device-camera-view.js';
import * as showKeysView from './pages/show-keys-view.js';
import * as deviceSessionView from './pages/device-session-view.js';
import * as languageView from './pages/language-view.js';
import * as messagesList from './pages/messages-list.js';
import * as contactSearchView from './pages/contact-search-view.js';
import * as chatView from './pages/chat-view.js';
import * as userProfileView from './pages/user-profile-view.js';
import * as channelsList from './pages/channels-list.js';
import * as channelView from './pages/channel-view.js';
import * as addChannelView from './pages/add-channel-view.js';
import * as networkView from './pages/network-view.js';
import * as notificationsView from './pages/notifications-view.js';

const routes = {
  'start-view': startView,
  'entry-settings-view': entrySettingsView,
  'register-view': registerView,
  'registration-payment-view': registrationPaymentView,
  'registration-keys-view': registrationKeysView,
  'topup-view': topupView,
  'login-view': loginView,
  'login-camera-view': loginCameraView,
  'login-password-view': loginPasswordView,
  'key-storage-view': keyStorageView,
  'profile-view': profileView,
  'wallet-view': walletView,
  'settings-view': settingsView,
  'server-settings-view': serverSettingsView,
  'device-view': deviceView,
  'connect-device-view': connectDeviceView,
  'device-qr-view': deviceQrView,
  'device-camera-view': deviceCameraView,
  'show-keys-view': showKeysView,
  'device-session-view': deviceSessionView,
  'language-view': languageView,
  'messages-list': messagesList,
  'contact-search-view': contactSearchView,
  'chat-view': chatView,
  'user-profile-view': userProfileView,
  'channels-list': channelsList,
  'channel-view': channelView,
  'add-channel-view': addChannelView,
  'network-view': networkView,
  'notifications-view': notificationsView,
};

const screenEl = document.getElementById('app-screen');
const toolbarEl = document.getElementById('toolbar-slot');

let currentCleanup = null;

setClientErrorTransport((payload) => authService.reportClientError(payload));

function showGlobalErrorAlert(title, details = {}) {
  const lines = [title];
  if (details.message) lines.push(`Сообщение: ${details.message}`);
  if (details.pageId) lines.push(`Экран: ${details.pageId}`);
  if (details.sourceUrl) lines.push(`Источник: ${details.sourceUrl}`);
  if (Number.isFinite(details.lineNumber)) lines.push(`Строка: ${details.lineNumber}`);
  if (Number.isFinite(details.columnNumber)) lines.push(`Колонка: ${details.columnNumber}`);
  if (details.reasonType) lines.push(`Тип: ${details.reasonType}`);
  if (details.stack) lines.push(`Stack:\n${details.stack}`);
  window.alert(lines.join('\n'));
}

window.addEventListener('error', (event) => {
  const pageId = getRoute().pageId || '';
  captureClientError({
    kind: 'global_error',
    message: event.message || 'Global JS error',
    stack: event.error?.stack || '',
    sourceUrl: event.filename || '',
    lineNumber: event.lineno,
    columnNumber: event.colno,
    context: {
      pageId,
    },
  });

  showGlobalErrorAlert('Поймана глобальная ошибка UI', {
    message: event.message || 'Global JS error',
    stack: event.error?.stack || '',
    sourceUrl: event.filename || '',
    lineNumber: event.lineno,
    columnNumber: event.colno,
    pageId,
  });
});

window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason;
  const pageId = getRoute().pageId || '';
  captureClientError({
    kind: 'unhandled_rejection',
    message: reason?.message || String(reason || 'Unhandled promise rejection'),
    stack: reason?.stack || '',
    context: {
      pageId,
      reasonType: reason?.constructor?.name || typeof reason,
    },
  });

  showGlobalErrorAlert('Пойман необработанный Promise reject', {
    message: reason?.message || String(reason || 'Unhandled promise rejection'),
    stack: reason?.stack || '',
    pageId,
    reasonType: reason?.constructor?.name || typeof reason,
  });
});

function renderApp() {
  const route = getRoute();
  const pageId = route.pageId || (state.session.isAuthorized ? 'profile-view' : 'start-view');

  if (!state.session.isAuthorized && !PRE_AUTH_PAGES.includes(pageId)) {
    navigate('start-view');
    return;
  }

  if (state.session.isAuthorized && PRE_AUTH_PAGES.includes(pageId)) {
    navigate('profile-view');
    return;
  }

  const page = routes[pageId] || routes['start-view'];

  if (typeof currentCleanup === 'function') {
    currentCleanup();
    currentCleanup = null;
  }

  screenEl.innerHTML = '';
  const screen = page.render({ route, navigate });
  screenEl.append(screen);
  currentCleanup = typeof screen.cleanup === 'function' ? screen.cleanup : null;

  const showAppChrome = page.pageMeta?.showAppChrome !== false;
  screenEl.classList.toggle('no-app-chrome', !showAppChrome);

  toolbarEl.innerHTML = '';

  if (showAppChrome) {
    toolbarEl.append(renderToolbar(page.pageMeta.id, navigate));
  }
}

async function tryAutoLogin() {
  if (!state.session.login || !state.session.sessionId) return;
  try {
    await authService.reconnect(state.entrySettings.shineServer);
    const resumed = await authService.resumeSession(state.session.login, state.session.sessionId);
    authorizeSession(resumed);
    await refreshSessions();
    try {
      const contacts = await authService.listContacts();
      setContacts(contacts.contacts || []);
    } catch {}
  } catch (error) {
    if (isSessionInvalidError(error)) {
      await terminateCurrentSession({
        infoMessage: 'Сессия на этом устройстве уже завершена. Выполните вход заново.',
      });
    }
  }
}

async function init() {
  setSessionResetHandler(() => {
    navigate('start-view');
  });

  authService.onEvent('SessionRevoked', async () => {
    await terminateCurrentSession({ infoMessage: 'Сессия закрыта с другого устройства.' });
  });

  authService.onEvent('IncomingDirectMessage', async (evt) => {
    const payload = evt?.payload || {};
    const fromLogin = payload.fromLogin || 'unknown';
    const messageId = payload.messageId || '';
    const eventId = payload.eventId || evt?.requestId || '';
    const added = addIncomingMessage(fromLogin, payload.text || '', messageId);
    if (added && Notification.permission === 'granted') {
      try {
        new Notification(`Сообщение от ${fromLogin}`, { body: payload.text || '' });
      } catch {}
    }
    if (eventId) {
      try { await authService.ackIncomingMessage(eventId, messageId); } catch {}
    }
  });

  authService.onEvent('IncomingCallInvite', async (evt) => {
    try { await handleIncomingCallInvite(evt); } catch {}
  });

  authService.onEvent('IncomingCallSignal', async (evt) => {
    try { await handleIncomingCallSignal(evt); } catch {}
  });

  await tryAutoLogin();
  if (state.session.isAuthorized) {
    await initPwaPush({ authService });
  }

  if (!window.location.hash) {
    navigate(state.session.isAuthorized ? 'profile-view' : 'start-view');
  } else {
    renderApp();
  }

  window.addEventListener('hashchange', renderApp);
}

init();
