const LS_KEY = 'shine-ui-webpush-subscription-v1';

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export async function initPwaPush({ authService }) {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;

  const vapidPublicKey = window.__SHINE_WEBPUSH_VAPID_PUBLIC_KEY__ || '';
  if (!vapidPublicKey) return;

  try {
    const registration = await navigator.serviceWorker.register('./firebase-messaging-sw.js');
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    let sub = await registration.pushManager.getSubscription();
    if (!sub) {
      sub = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      });
    }

    const serialized = JSON.stringify(sub);
    if (localStorage.getItem(LS_KEY) === serialized) return;
    localStorage.setItem(LS_KEY, serialized);

    const json = sub.toJSON();
    const endpoint = json.endpoint || '';
    const p256dhKey = json.keys?.p256dh || '';
    const authKey = json.keys?.auth || '';
    if (!endpoint || !p256dhKey || !authKey) return;

    await authService.upsertPushToken({
      endpoint,
      p256dhKey,
      authKey,
      platform: 'web',
      userAgent: navigator.userAgent || '',
    });
  } catch {
    // silent for MVP
  }
}
