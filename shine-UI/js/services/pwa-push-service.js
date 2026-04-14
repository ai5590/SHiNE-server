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

export async function initPwaPush({ authService, onLog = null }) {
  const log = (entry) => {
    if (typeof onLog === 'function') onLog(entry);
  };

  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    log({
      level: 'warn',
      source: 'web-push',
      message: 'Web Push недоступен: нет serviceWorker или PushManager',
    });
    return;
  }

  const vapidPublicKey = window.__SHINE_WEBPUSH_VAPID_PUBLIC_KEY__ || '';
  if (!vapidPublicKey) {
    log({
      level: 'warn',
      source: 'web-push',
      message: 'Web Push отключен: не задан публичный VAPID ключ',
    });
    return;
  }

  try {
    const registration = await navigator.serviceWorker.register('./firebase-messaging-sw.js');
    log({
      level: 'info',
      source: 'web-push',
      message: 'Service Worker зарегистрирован',
      details: { scope: registration.scope },
    });

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      log({
        level: 'warn',
        source: 'web-push',
        message: `Разрешение на уведомления: ${permission}`,
      });
      return;
    }
    log({
      level: 'info',
      source: 'web-push',
      message: 'Разрешение на уведомления получено',
    });

    let sub = await registration.pushManager.getSubscription();
    let isNewSubscription = false;
    if (!sub) {
      sub = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      });
      isNewSubscription = true;
    }
    log({
      level: 'info',
      source: 'web-push',
      message: isNewSubscription ? 'Создана новая push-подписка' : 'Найдена существующая push-подписка',
    });

    const serialized = JSON.stringify(sub);
    const prevSerialized = localStorage.getItem(LS_KEY);
    if (prevSerialized === serialized) {
      log({
        level: 'info',
        source: 'web-push',
        message: 'Push-подписка не изменилась, отправка на сервер не требуется',
      });
      return;
    }
    localStorage.setItem(LS_KEY, serialized);

    const json = sub.toJSON();
    const endpoint = json.endpoint || '';
    const p256dhKey = json.keys?.p256dh || '';
    const authKey = json.keys?.auth || '';
    if (!endpoint || !p256dhKey || !authKey) {
      log({
        level: 'warn',
        source: 'web-push',
        message: 'Подписка неполная: endpoint/p256dh/auth отсутствуют',
      });
      return;
    }

    log({
      level: 'info',
      source: 'web-push',
      message: 'Push-токен получен, отправка на сервер',
      details: { endpoint },
    });

    await authService.upsertPushToken({
      endpoint,
      p256dhKey,
      authKey,
      platform: 'web',
      userAgent: navigator.userAgent || '',
    });
    log({
      level: 'info',
      source: 'web-push',
      message: 'Push-подписка успешно отправлена на сервер',
    });
  } catch (error) {
    log({
      level: 'error',
      source: 'web-push',
      message: 'Ошибка инициализации Web Push',
      details: error?.message || 'unknown',
    });
  }
}
