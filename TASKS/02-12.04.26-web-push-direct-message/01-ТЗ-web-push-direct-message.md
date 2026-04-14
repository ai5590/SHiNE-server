# Задача 02: Web Push + подписанный API отправки личных сообщений

## Контекст (по текущему состоянию проекта)
- Уже есть JSON WebSocket API для личных сообщений: `SendDirectMessage`, `AckIncomingMessage`, `UpsertPushToken`.
- Сейчас серверный fallback-пуш реализован через FCM (`FcmPushSender`) и ключ `fcm.server.key`.
- Клиент уже регистрирует service worker и токен Firebase, затем аплоадит push token на сервер.

## Цель
Добавить полностью рабочий сценарий доставки личных сообщений с приоритетом:
1) онлайн-доставка в активную WebSocket-сессию;
2) если не подтверждено — Web Push;
3) поддержать отдельный API отправки без авторизации, где доступ проверяется цифровой подписью Ed25519 по `deviceKey` отправителя.

---

## Предварительная спецификация подписанного пакета (v1)
> ВАЖНО: финально фиксируется после уточнений по endian/кодировкам/лимитам.

Пакет (binary):
1. `prefix` — ASCII-константа, например `SHINE_MESSAGE`.
2. `toLoginLen` — 1 байт.
3. `toLogin` — ASCII, длина = `toLoginLen`.
4. `fromLoginLen` — 1 байт.
5. `fromLogin` — ASCII, длина = `fromLoginLen`.
6. `timeMs` — 8 байт (unix ms).
7. `nonce32` — 4 байта случайное число.
8. `messageType` — 4 байта.
9. `targetMode` — 1 байт:
   - `0` = всем сессиям пользователя,
   - `1` = конкретной сессии.
10. Если `targetMode=1`:
   - `sessionIdLen` — 1 байт,
   - `sessionId` — ASCII.
11. `messageLen` — 2 байта.
12. `messageBytes` — бинарные данные длиной `messageLen`.
13. `signature64` — 64 байта, Ed25519 подпись всего блока **без** `signature64`.

Ограничения (первичный draft):
- общий размер пакета ≤ 4000 байт;
- логины/префикс/идентификатор сессии — ASCII;
- повторы отсекаются по `(fromLogin, timeMs, nonce32)` в окне TTL.

---

## Сервер: что доработать

### 1) Новый endpoint без авторизации
Операция (через WS JSON обертку) условно `SendSignedDirectMessage`:
- принимает пакет (base64 binary blob);
- парсит и валидирует формат;
- достает `fromLogin`, поднимает `deviceKey` пользователя;
- проверяет подпись Ed25519;
- проверяет анти-replay (time window + nonce);
- отправляет сообщение по правилам маршрутизации;
- пишет результат (messageId, каналы доставки, причины недоставки).

### 2) Маршрутизация доставки
Для `targetMode=1`:
- если целевая сессия онлайн и ACK пришел вовремя — успех;
- иначе отправка в Web Push этой сессии (если есть subscription).

Для `targetMode=0`:
- обход всех сессий пользователя;
- сначала online delivery + ACK;
- для непринятых/офлайн — Web Push по соответствующим subscription;
- если subscription отсутствует — тихий skip.

### 3) Миграция от FCM к Web Push
- добавить конфиг VAPID (`webpush.public.key`, `webpush.private.key`, `webpush.subject`);
- хранить на сервере не только token, а web-push subscription (endpoint + keys);
- сделать отправщик Web Push и заменить/расширить текущий `FcmPushSender`.

### 4) Безопасность
- строгая ASCII-валидация логинов/sessionId;
- лимиты длины всех полей;
- rate limit на endpoint;
- audit-лог неуспешных проверок подписи/формата;
- защита от replay.

---

## Клиент (shine-UI): что доработать

1. Перейти на стандартный Web Push flow:
   - регистрация service worker;
   - `PushManager.subscribe(...)` с VAPID public key;
   - отправка subscription на сервер (`UpsertPushSubscription` или расширение `UpsertPushToken`).

2. Service worker:
   - `push` handler получает payload целиком;
   - показывает системное уведомление;
   - при клике открывает/фокусирует нужный чат.

3. Online-сообщения:
   - сохранить текущий event-канал `IncomingDirectMessage`;
   - обязателен ACK (`AckIncomingMessage` уже есть).

4. Keep-alive:
   - UI отправляет `Ping` раз в 60 секунд при активной сессии.

---

## Документация
Сделать отдельный документ настройки Web Push:
- как сгенерировать VAPID ключи;
- какие параметры прописать на сервере и в UI;
- как проверить локально e2e (онлайн + офлайн пуш);
- ограничения payload и рекомендации по ретраям.

---

## Этапы реализации (предложение)
1. Зафиксировать бинарный формат + валидации.
2. Реализовать серверный parser/validator/signature verify/replay guard.
3. Реализовать Web Push sender + storage subscription.
4. Подключить новый endpoint и маршрутизацию доставки.
5. Обновить UI (subscription + service worker + ping timer).
6. Добавить интеграционные тесты (online ACK / offline push / bad signature / replay / oversize).
7. Добавить документацию.

---

## Что нужно уточнить до разработки
1. Endian для `timeMs/nonce/messageType/messageLen` (big-endian или little-endian).
2. Что именно подписывается: строго весь префикс..messageBytes (без подписи) — подтвердить.
3. Диапазон допустимых `messageType`.
4. TTL окна для анти-replay (например 5 минут / 15 минут).
5. Лимиты длин для login/session/message.
6. Можно ли временно оставить FCM как fallback, пока не готов Web Push в проде.
7. Формат сообщения в `messageBytes`: opaque bytes или UTF-8 строка.


## Статус реализации (12.04.2026)

### Что уже внедрено в коде
- `SendDirectMessage` переведён на signed-binary payload (`blobB64`) без обязательной авторизации WS-сессии.
- Внедрён бинарный парсер пакета формата `SHiNE_msg + version(1) + ... + signature64`.
- Проверка подписи Ed25519 делается по `deviceKey` отправителя через `shine-server-crypto` (`Ed25519Util`).
- Добавлен anti-replay guard `(from_login, time_ms, nonce)` с TTL 15 минут.
- Добавлено историческое хранилище `signed_direct_messages_history` с сырым пакетом `raw_packet`.
- Логика доставки: сначала WS+ACK, затем fallback на Web Push (по подписке конкретной session).
- Поле типа сообщения переведено на `uint16`, пока поддерживается только `1`.
- Для `targetMode=1` при несуществующей сессии возвращается `success` с `sessionNotFound=true` и `delivered=0`.
- UI переведён с Firebase/FCM на браузерный `PushManager.subscribe` + Service Worker `push`.
- Добавлен keep-alive ping из UI раз в 60 секунд при авторизованной сессии.

### Что настроить в окружении
- В `application.properties` задать:
  - `webpush.vapid.public`
  - `webpush.vapid.private`
  - `webpush.vapid.subject`
- В `shine-UI/index.html` задать публичный VAPID ключ в `window.__SHINE_WEBPUSH_VAPID_PUBLIC_KEY__`.

