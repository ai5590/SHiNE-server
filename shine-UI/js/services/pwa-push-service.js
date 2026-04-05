const LS_KEY = 'shine-ui-fcm-token-v1';

export async function initPwaPush({ authService }) {
  if (!('serviceWorker' in navigator)) return;
  try {
    await navigator.serviceWorker.register('./firebase-messaging-sw.js');
  } catch {
    return;
  }

  if (!window.firebase || !window.firebase.messaging) return;

  try {
    const config = window.__SHINE_FIREBASE_CONFIG__ || null;
    if (!config) return;
    if (!window.firebase.apps.length) {
      window.firebase.initializeApp(config);
    }
    const messaging = window.firebase.messaging();

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    const vapidKey = window.__SHINE_FIREBASE_VAPID_KEY__ || '';
    const token = await messaging.getToken({ vapidKey });
    if (!token) return;

    const prev = localStorage.getItem(LS_KEY);
    if (prev === token) return;

    localStorage.setItem(LS_KEY, token);
    const tokenId = `tok-${new Date().toISOString().replace(/[-:.TZ]/g, '')}-${Math.random().toString(36).slice(2, 12)}`;
    await authService.upsertPushToken({
      tokenId,
      token,
      provider: 'fcm',
      platform: 'web',
      userAgent: navigator.userAgent || '',
    });

    messaging.onMessage((payload) => {
      const title = payload?.notification?.title || 'Новое сообщение';
      const body = payload?.notification?.body || '';
      try {
        new Notification(title, { body });
      } catch {}
    });
  } catch {
    // silent for MVP
  }
}
