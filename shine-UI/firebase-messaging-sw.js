self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

async function broadcastToClients(payload) {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  clients.forEach((client) => {
    client.postMessage({
      type: 'SHINE_WEB_PUSH_EVENT',
      payload,
    });
  });
}

self.addEventListener('push', (event) => {
  let body = 'Новое сообщение SHiNE';
  let rawText = '';
  try {
    if (event.data) {
      const text = event.data.text();
      rawText = text || '';
      body = rawText || body;
    }
  } catch {
    // ignore
  }

  event.waitUntil(Promise.all([
    self.registration.showNotification('SHiNE: входящее сообщение', {
      body,
      tag: 'shine-direct-message',
      renotify: true,
    }),
    broadcastToClients({
      body,
      rawText,
      receivedAt: Date.now(),
    }),
  ]));
});
