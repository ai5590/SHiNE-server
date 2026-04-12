const ROOT_PAGES = ['messages-list', 'channels-list', 'network-view', 'notifications-view', 'profile-view'];

export const PRE_AUTH_PAGES = [
  'start-view',
  'entry-settings-view',
  'register-view',
  'registration-payment-view',
  'registration-keys-view',
  'topup-view',
  'login-view',
  'login-camera-view',
  'login-password-view',
  'key-storage-view',
];

export function getRoute() {
  const raw = window.location.hash.replace(/^#\/?/, '');
  if (!raw) {
    return { pageId: '', params: {} };
  }

  const [pageId, dynamicId, extraId] = raw.split('/');

  if (pageId === 'chat-view') {
    return { pageId, params: { chatId: dynamicId ? decodeURIComponent(dynamicId) : '' } };
  }

  if (pageId === 'channel-view') {
    return { pageId, params: { channelId: dynamicId ? decodeURIComponent(dynamicId) : '' } };
  }

  if (pageId === 'device-session-view') {
    return { pageId, params: { sessionId: dynamicId ? decodeURIComponent(dynamicId) : '' } };
  }

  if (pageId === 'user-profile-view') {
    return {
      pageId,
      params: {
        login: dynamicId ? decodeURIComponent(dynamicId) : '',
        fromPage: extraId ? decodeURIComponent(extraId) : 'messages-list',
      },
    };
  }

  return { pageId, params: {} };
}

export function navigate(path) {
  window.location.hash = `#/${path}`;
}

export function resolveToolbarActive(pageId) {
  if (ROOT_PAGES.includes(pageId)) return pageId;
  if (
    pageId === 'wallet-view' ||
    pageId === 'settings-view' ||
    pageId === 'server-settings-view' ||
    pageId === 'device-view' ||
    pageId === 'connect-device-view' ||
    pageId === 'device-qr-view' ||
    pageId === 'device-camera-view' ||
    pageId === 'show-keys-view' ||
    pageId === 'device-session-view' ||
    pageId === 'language-view'
  ) {
    return 'profile-view';
  }
  if (pageId === 'chat-view' || pageId === 'contact-search-view') return 'messages-list';
  if (pageId === 'user-profile-view') return 'messages-list';
  if (pageId === 'channel-view' || pageId === 'add-channel-view') return 'channels-list';
  return 'profile-view';
}
