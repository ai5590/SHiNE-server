import { navigate, getRoute, PRE_AUTH_PAGES } from './router.js?v=20260403081123';
import { renderToolbar } from './components/toolbar.js?v=20260403081123';
import { captureClientError, setClientErrorTransport } from './services/client-error-reporter.js?v=20260403081123';
import { initPwaPush } from './services/pwa-push-service.js?v=20260403081123';
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
} from './state.js?v=20260403081123';

import * as startView from './pages/start-view.js?v=20260403081123';
import * as entrySettingsView from './pages/entry-settings-view.js?v=20260403081123';
import * as registerView from './pages/register-view.js?v=20260403081123';
import * as registrationPaymentView from './pages/registration-payment-view.js?v=20260403081123';
import * as registrationKeysView from './pages/registration-keys-view.js?v=20260403081123';
import * as topupView from './pages/topup-view.js?v=20260403081123';
import * as loginView from './pages/login-view.js?v=20260403081123';
import * as loginCameraView from './pages/login-camera-view.js?v=20260403081123';
import * as loginPasswordView from './pages/login-password-view.js?v=20260403081123';
import * as keyStorageView from './pages/key-storage-view.js?v=20260403081123';

import * as profileView from './pages/profile-view.js?v=20260403081123';
import * as walletView from './pages/wallet-view.js?v=20260403081123';
import * as settingsView from './pages/settings-view.js?v=20260403081123';
import * as serverSettingsView from './pages/server-settings-view.js?v=20260403081123';
import * as deviceView from './pages/device-view.js?v=20260403081123';
import * as connectDeviceView from './pages/connect-device-view.js?v=20260403081123';
import * as deviceQrView from './pages/device-qr-view.js?v=20260403081123';
import * as deviceCameraView from './pages/device-camera-view.js?v=20260403081123';
import * as showKeysView from './pages/show-keys-view.js?v=20260403081123';
import * as deviceSessionView from './pages/device-session-view.js?v=20260403081123';
import * as languageView from './pages/language-view.js?v=20260403081123';
import * as messagesList from './pages/messages-list.js?v=20260403081123';
import * as contactSearchView from './pages/contact-search-view.js?v=20260403081123';
import * as chatView from './pages/chat-view.js?v=20260403081123';
import * as channelsList from './pages/channels-list.js?v=20260403081123';
import * as channelView from './pages/channel-view.js?v=20260403081123';
import * as addChannelView from './pages/add-channel-view.js?v=20260403081123';
import * as networkView from './pages/network-view.js?v=20260403081123';
import * as notificationsView from './pages/notifications-view.js?v=20260403081123';

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

window.addEventListener('error', (event) => {
  captureClientError({
    kind: 'global_error',
    message: event.message || 'Global JS error',
    stack: event.error?.stack || '',
    sourceUrl: event.filename || '',
    lineNumber: event.lineno,
    columnNumber: event.colno,
    context: {
      pageId: getRoute().pageId || '',
    },
  });
});

window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason;
  captureClientError({
    kind: 'unhandled_rejection',
    message: reason?.message || String(reason || 'Unhandled promise rejection'),
    stack: reason?.stack || '',
    context: {
      pageId: getRoute().pageId || '',
      reasonType: reason?.constructor?.name || typeof reason,
    },
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
