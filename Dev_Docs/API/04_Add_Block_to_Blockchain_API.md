# API для разработчиков: 04 — Добавление блока в блокчейн (AddBlock)

Документ описывает **текущий рабочий формат** сетевого вызова `AddBlock`, который используется для записи **любого** блока в блокчейн пользователя.

> Важный принцип: на уровне JSON API сейчас есть **один универсальный метод** записи — `AddBlock`.
> Конкретный смысл записи задаётся типом самого бинарного блока (`type/subType/version` в заголовке блока).

## 1. Что делает `AddBlock`

`AddBlock`:
- принимает имя блокчейна и base64 бинарного блока;
- проверяет непрерывность цепочки (`blockNumber`, `prevHash`);
- проверяет формат и подпись Ed25519;
- валидирует `body` по правилам типа блока;
- сохраняет блок и обновляет состояние цепочки.

## 2. JSON формат запроса

`op = "AddBlock"`.

```json
{
  "op": "AddBlock",
  "requestId": "req-1001",
  "payload": {
    "blockchainName": "alice-001",
    "blockNumber": 12,
    "prevBlockHash": "ab12...ff", 
    "blockBytesB64": "AAAB..."
  }
}
```

Поля `payload`:
- `blockchainName` — обязательно, формат `login-NNN`.
- `blockNumber` — обязательно (временное legacy-поле для совместимости; должно совпасть с номером внутри бинарного блока).
- `prevBlockHash` — legacy-поле, сейчас сервер использует `prevHash` из бинарного блока и состояние цепочки.
- `blockBytesB64` — обязательно: **полный бинарный блок** (`preimage + sigMarker + signature`) в Base64.

## 3. Успешный ответ

```json
{
  "op": "AddBlock",
  "requestId": "req-1001",
  "status": 200,
  "ok": true,
  "payload": {
    "reasonCode": null,
    "serverLastGlobalNumber": 12,
    "serverLastGlobalHash": "9f0e...a1"
  }
}
```

## 4. Ошибка (единый формат)

При ошибках сервер отдаёт `Net_Exception_Response` со стандартными полями и дополнительно с состоянием сервера для ресинка:

```json
{
  "op": "AddBlock",
  "requestId": "req-1001",
  "status": 400,
  "ok": false,
  "error": "bad_prev_hash",
  "message": "Некорректный prevHash (цепочка не совпадает)",
  "payload": {
    "serverLastGlobalNumber": 11,
    "serverLastGlobalHash": "c3d4...98"
  }
}
```

### Основные `reasonCode`

- `empty_blockchain_name`, `bad_blockchain_name`
- `blockchain_state_not_found`
- `bad_block_base64`, `bad_block_format`, `bad_block_body`
- `bad_block_number`, `req_global_mismatch`, `bad_prev_hash`
- `bad_signature`, `signature_verify_failed`
- `prev_line_block_not_found`, `bad_prev_line_hash`
- `limit_exceeded`
- `internal_error`

## 5. Какие блоки реально можно добавлять через `AddBlock`

Через `AddBlock` можно писать все поддержанные форматы:

1. **TECH (type=0)**
   - `HEADER_COMPAT (subType=0)`
   - `TECH_CREATE_CHANNEL (subType=1)`

2. **TEXT (type=1)**
   - `TEXT_POST (10)`
   - `TEXT_EDIT_POST (11)`
   - `TEXT_REPLY (20)`
   - `TEXT_EDIT_REPLY (21)`

3. **REACTION (type=2)**
   - `REACTION_LIKE (1)`

4. **CONNECTION (type=3)**
   - `CONNECTION_FRIEND (10)`
   - `CONNECTION_UNFRIEND (11)`
   - `CONNECTION_CONTACT (20)`
   - `CONNECTION_UNCONTACT (21)`
   - `CONNECTION_FOLLOW (30)`
   - `CONNECTION_UNFOLLOW (31)`

5. **USER_PARAM (type=4)**
   - `USER_PARAM_TEXT_TEXT (1)`

## 6. Хватает ли функций сейчас

Коротко: **для записи событий в блокчейн — хватает**, для полноценного клиентского чтения — **пока не хватает**.

Что есть:
- единый надёжный write-путь `AddBlock`;
- есть `GetFriendsLists` и API по `UserParam`;
- есть унифицированные коды ошибок и поля для ресинхронизации.

Что пока ограничивает продукт:
- нет полноценного read API для каналов/постов/тредов;
- нет API списка подписок с серверными счётчиками непрочитанного;
- нет ленты событий (новые ответы/лайки/подписки) как отдельного RPC.

## 7. Рекомендации по клиенту при записи блоков

1. Перед отправкой держать локальный `lastNumber/lastHash`.
2. При `bad_prev_hash` или `bad_block_number`:
   - взять `serverLastGlobalNumber/serverLastGlobalHash` из ошибки,
   - пересобрать следующий блок на актуальной вершине.
3. Для edit-блоков всегда ссылаться на **оригинальный** блок, а не на предыдущий edit.
4. Для связей/подписок использовать target на **root** (HEADER или CREATE_CHANNEL), а не на произвольный пост.


## 8. USER_PARAM для «личных данных»

Да, на текущем API это можно добавить **без изменения серверного кода**:

- в `UserParam` поле `param` сейчас не ограничено фиксированным справочником;
- сервер хранит пары `param -> value` как строки (при наличии корректной подписи и `time_ms`);
- чтение уже есть через `GetUserParam` и `ListUserParams`.

Рекомендуемый стартовый набор ключей для профиля (MVP):

- `name`
- `last_name`
- `address_physical`
- `address_web`
- `phone`

Практическая рекомендация: заранее зафиксировать единый словарь ключей в клиенте/документации, чтобы избежать дублей вида `lastname` vs `last_name`, `site` vs `address_web` и т.д.

Ограничения, которые важно учесть:

- сейчас нет серверной ACL-политики чтения параметров (в MVP их может читать любой клиент, который знает `login`);
- нет валидации формата значений для конкретных ключей (телефон, URL и т.д. проверяются только на стороне клиента);
- нет отдельного индекса/поиска по этим полям — только точечное чтение и listing по `login`.
