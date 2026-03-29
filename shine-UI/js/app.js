import { navigate, getRoute, PRE_AUTH_PAGES } from './router.js?v=20260327192619';
import { renderToolbar } from './components/toolbar.js?v=20260327192619';
import { renderPageLabel } from './components/page-label.js?v=20260327192619';
import { authService, authorizeSession, refreshSessions, state, togglePageLabel } from './state.js?v=20260327192619';

import * as startView from './pages/start-view.js?v=20260327192619';
import * as entrySettingsView from './pages/entry-settings-view.js?v=20260327192619';
import * as registerView from './pages/register-view.js?v=20260327192619';
import * as registrationPaymentView from './pages/registration-payment-view.js?v=20260327192619';
import * as registrationKeysView from './pages/registration-keys-view.js?v=20260327192619';
import * as topupView from './pages/topup-view.js?v=20260327192619';
import * as loginView from './pages/login-view.js?v=20260327192619';
import * as loginCameraView from './pages/login-camera-view.js?v=20260327192619';
import * as loginPasswordView from './pages/login-password-view.js?v=20260327192619';
import * as keyStorageView from './pages/key-storage-view.js?v=20260327192619';

import * as profileView from './pages/profile-view.js?v=20260327192619';
import * as walletView from './pages/wallet-view.js?v=20260327192619';
import * as settingsView from './pages/settings-view.js?v=20260327192619';
import * as serverSettingsView from './pages/server-settings-view.js?v=20260327192619';
import * as deviceView from './pages/device-view.js?v=20260327192619';
import * as connectDeviceView from './pages/connect-device-view.js?v=20260327192619';
import * as deviceQrView from './pages/device-qr-view.js?v=20260327192619';
import * as deviceCameraView from './pages/device-camera-view.js?v=20260327192619';
import * as showKeysView from './pages/show-keys-view.js?v=20260327192619';
import * as deviceSessionView from './pages/device-session-view.js?v=20260327192619';
import * as languageView from './pages/language-view.js?v=20260327192619';
import * as messagesList from './pages/messages-list.js?v=20260327192619';
import * as contactSearchView from './pages/contact-search-view.js?v=20260327192619';
import * as chatView from './pages/chat-view.js?v=20260327192619';
import * as channelsList from './pages/channels-list.js?v=20260327192619';
import * as channelView from './pages/channel-view.js?v=20260327192619';
import * as networkView from './pages/network-view.js?v=20260327192619';
import * as notificationsView from './pages/notifications-view.js?v=20260327192619';

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
  'network-view': networkView,
  'notifications-view': notificationsView,
};

const screenEl = document.getElementById('app-screen');
const labelEl = document.getElementById('page-label-slot');
const toolbarEl = document.getElementById('toolbar-slot');

let currentCleanup = null;

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

  labelEl.innerHTML = '';
  toolbarEl.innerHTML = '';

  if (showAppChrome) {
    labelEl.append(
      renderPageLabel(page.pageMeta.title, page.pageMeta.id, state.pageLabelCollapsed, () => {
        togglePageLabel();
        renderApp();
      }),
    );
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
  } catch {
    // silent fallback to auth screens
  }
}

async function init() {
  await tryAutoLogin();

  if (!window.location.hash) {
    navigate(state.session.isAuthorized ? 'profile-view' : 'start-view');
  } else {
    renderApp();
  }

  window.addEventListener('hashchange', renderApp);
}

init();
