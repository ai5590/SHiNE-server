/* global importScripts, firebase */
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

// Заполните теми же значениями, что и в shine-UI/index.html
const FIREBASE_CONFIG = {
  apiKey: '',
  authDomain: '',
  projectId: '',
  messagingSenderId: '',
  appId: '',
};

if (FIREBASE_CONFIG.apiKey && firebase && firebase.messaging) {
  if (!firebase.apps.length) {
    firebase.initializeApp(FIREBASE_CONFIG);
  }
  const messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    const title = payload?.notification?.title || 'Новое сообщение';
    const options = {
      body: payload?.notification?.body || '',
      data: payload?.data || {},
    };
    self.registration.showNotification(title, options);
  });
}
