Протокол создания новой сессии
📌 Общие правила JSON-протокола

Любой запрос от клиента имеет формат:

{
"op": "OperationName",
"requestId": "some-id",
"payload": { ... }
}


Ответ от сервера:

{
"op": "OperationName",
"requestId": "some-id",
"status": 200,
"payload": { "ok": true, ... }
}


Ошибка:

{
"op": "OperationName",
"requestId": "some-id",
"status": 4xx/5xx,
"payload": {
"ok": false,
"code": "ERROR_CODE",
"message": "Описание ошибки"
}
}

1. Добавление пользователя (AddUser)

Назначение: создать локальную запись пользователя с двумя ключами — loginKey и deviceKey.

📤 Запрос клиента
{
"op": "AddUser",
"requestId": "req-1",
"payload": {
"login": "anya4",
"loginId": 100212,
"bchId": 4222,
"loginKey": "BASE64_LOGIN_KEY",
"deviceKey": "BASE64_DEVICE_KEY",
"bchLimit": 1000000
}
}

🖥 Действия сервера

Проверяет корректность данных.

Вставляет запись в таблицу:

CREATE TABLE solana_users (
login TEXT NOT NULL,
loginId INTEGER PRIMARY KEY,
bchId INTEGER NOT NULL,
loginKey TEXT,
deviceKey TEXT,
bchLimit INTEGER
);

📥 Ответ
{
"op": "AddUser",
"requestId": "req-1",
"status": 200,
"payload": { "ok": true }
}

2. Шаг 1 — запрос временного пароля сессии
   AuthChallenge

Назначение: сервер выдаёт клиенту временный sessionPwd.
Он потом будет включён в подписываемую строку.

📤 Запрос клиента
{
"op": "AuthChallenge",
"requestId": "req-2",
"payload": {
"login": "anya4"
}
}

🖥 Действия сервера

Находит пользователя по login.

Генерирует sessionPwd — случайный 32-байтовый base64.

Сохраняет sessionPwd во временный in-memory storage, привязанный к loginId.

📥 Ответ
{
"op": "AuthChallenge",
"requestId": "req-2",
"status": 200,
"payload": {
"ok": true,
"sessionPwd": "BASE64_SESSION_PWD"
}
}

3. Шаг 2 — подтверждение подписью и создание сессии
   CreateAuthSession
   📌 Клиент подписывает строку:
   preimage = "AUTHORIFICATED:" + timeMs + sessionPwd


timeMs — timestamp клиента (UTC).

sessionPwd — строка с шага 1.

signatureB64 — Ed25519‐подпись preimage приватным ключом deviceKey.

📤 Запрос клиента
{
"op": "CreateAuthSession",
"requestId": "req-3",
"payload": {
"storagePwd": "BASE64_32_BYTES",
"timeMs": 1765298068436,
"signatureB64": "BASE64_SIGNATURE"
}
}

🔍 Валидация сервера

Проверяет timeMs:

не старше 30 секунд (now - timeMs <= 30s);

не в сильном будущем (опционально).

Восстанавливает preimage.

Находит deviceKey пользователя.

Проверяет Ed25519-подпись.

Создаёт sessionId — base64 от 32 байт.

Создаёт запись ActiveSession:

CREATE TABLE active_sessions (
sessionId TEXT PRIMARY KEY,
loginId INTEGER NOT NULL,
sessionPwd TEXT NOT NULL,
storagePwd TEXT NOT NULL,
sessionCreatedAtMs INTEGER NOT NULL,
lastAuthirificatedAtMs INTEGER NOT NULL,
pushEndpoint TEXT,
pushP256dhKey TEXT,
pushAuthKey TEXT
);

📥 Ответ сервера (успех)
{
"op": "CreateAuthSession",
"requestId": "req-3",
"status": 200,
"payload": {
"ok": true,
"sessionId": "BASE64_SESSION_ID"
}
}

📘 Итоговая схема создания сессии
AddUser → AuthChallenge → CreateAuthSession → Session Created
