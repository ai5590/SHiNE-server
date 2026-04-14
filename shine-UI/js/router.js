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

  const segments = raw.split('/').filter(Boolean);
  const pageId = segments[0] || '';
  const dynamicId = segments[1] || '';

  const decodePart = (value) => {
    try {
      return decodeURIComponent(value || '');
    } catch {
      return value || '';
    }
  };

  if (pageId === 'chat-view') {
    return { pageId, params: { chatId: dynamicId ? decodeURIComponent(dynamicId) : '' } };
  }

  if (pageId === 'channel-view') {
    if (segments.length >= 4) {
      return {
        pageId,
        params: {
          ownerBlockchainName: decodePart(segments[1]),
          channelRootBlockNumber: segments[2] || '',
          channelRootBlockHash: segments[3] || '',
          channelId: '',
        },
      };
    }
    return { pageId, params: { channelId: dynamicId || '' } };
  }

  if (pageId === 'channel-thread-view') {
    return {
      pageId,
      params: {
        messageBlockchainName: decodePart(segments[1]),
        messageBlockNumber: segments[2] || '',
        messageBlockHash: segments[3] || '',
        channelOwnerBlockchainName: decodePart(segments[4]),
        channelRootBlockNumber: segments[5] || '',
        channelRootBlockHash: segments[6] || '',
      },
    };
  }

  if (pageId === 'device-session-view') {
    return { pageId, params: { sessionId: dynamicId ? decodeURIComponent(dynamicId) : '' } };
  }

  if (pageId === 'user-profile-view') {
    return {
      pageId,
      params: {
        login: dynamicId ? decodeURIComponent(dynamicId) : '',
        fromPage: segments[2] ? decodeURIComponent(segments[2]) : 'messages-list',
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
    pageId === 'language-view' ||
    pageId === 'app-log-view'
  ) {
    return 'profile-view';
  }
  if (pageId === 'chat-view' || pageId === 'contact-search-view') return 'messages-list';
  if (pageId === 'channel-view' || pageId === 'channel-thread-view' || pageId === 'add-channel-view') return 'channels-list';
  if (pageId === 'user-profile-view') return 'messages-list';
  return 'profile-view';
}
