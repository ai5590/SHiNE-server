# Настройка PWA + FCM для веб-клиента (Chrome/Edge/Firefox/Safari iOS)

## 1) Что нужно создать в Firebase
1. Создать проект Firebase.
2. Включить Cloud Messaging.
3. Создать Web App и получить конфиг:
   - apiKey
   - authDomain
   - projectId
   - messagingSenderId
   - appId
4. В Cloud Messaging -> Web Push certificates сгенерировать VAPID key.
5. Для серверной отправки взять **Server key** (legacy) или настроить HTTP v1 (service account).

## 2) Куда вставить токены в клиенте
Файл: `shine-UI/index.html` и `shine-UI/firebase-messaging-sw.js`.

Заполнить:
- `window.__SHINE_FIREBASE_CONFIG__`
- `window.__SHINE_FIREBASE_VAPID_KEY__`
- `FIREBASE_CONFIG` (в service worker)

## 3) Куда вставить серверный ключ FCM
Файл: `src/main/resources/application.properties`

Добавить:
```
fcm.server.key=YOUR_FCM_SERVER_KEY
```

## 4) PWA требования
1. Открывать сайт только по HTTPS (или localhost).
2. Разрешить уведомления в браузере.
3. Убедиться, что `manifest.webmanifest` доступен.
4. Убедиться, что `firebase-messaging-sw.js` зарегистрирован.

## 5) Safari / iPhone (iOS)
- Нужен iOS 16.4+.
- Пользователь должен добавить сайт на Home Screen.
- После запуска PWA с Home Screen дать разрешение на уведомления.
- Без Home Screen web push в Safari iOS не работает.

## 6) Проверка
1. Логин в приложении.
2. Клиент вызывает `UpsertPushToken` и отправляет FCM токен на сервер.
3. Вызов `SendDirectMessage` пользователю без активной WS доставки.
4. Сервер шлет push через FCM.

## 7) Поддержка разных браузеров
- Chrome/Edge/Opera/Android Browser: FCM web push поддерживается нативно.
- Firefox: поддержка web push есть, но тестировать отдельно (поведение токенов отличается).
- Safari macOS/iOS: web push есть, но требуется PWA режим и Apple-ограничения.
