self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let body = 'Новое сообщение SHiNE';
  try {
    if (event.data) {
      const text = event.data.text();
      body = text || body;
    }
  } catch {
    // ignore
  }

  event.waitUntil(self.registration.showNotification('SHiNE: входящее сообщение', {
    body,
    tag: 'shine-direct-message',
    renotify: true,
  }));
});
