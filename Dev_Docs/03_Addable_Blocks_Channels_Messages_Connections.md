# Какие блоки можно добавлять и что уже есть по API

## 1) Что реально доступно по сети сейчас

В `JsonHandlerRegistry` сейчас есть:
- `AddBlock`
- `GetFriendsLists`
- `UpsertUserParam`, `GetUserParam`, `ListUserParams`
- auth/system и тестовые операции.

Отдельных read-API для каналов/сообщений пока нет (только запись блока + частично списки друзей + user params).

## 2) Как добавляются блоки

Через `AddBlock` клиент отправляет:
- blockchainName
- blockNumber
- prevBlockHash
- blockBytesB64

Сервер:
- парсит block;
- проверяет подпись блокчейна;
- извлекает `line`/`target` из body;
- сохраняет в `blocks`;
- обновляет `blockchain_state`;
- триггеры автоматически обновляют производные таблицы (`connections_state`, `message_stats`).

## 3) Конкретные сценарии

### 3.1 Создать канал

Добавить TECH-блок `CreateChannelBody` (type=0/subType=TECH_CREATE_CHANNEL).

### 3.2 Опубликовать сообщение в своём канале

Добавить `TEXT_POST` в нужную линию канала (`lineCode` указывает на root канала).

### 3.3 Ответ на сообщение

Добавить `TEXT_REPLY` с target (`toBlockchainName`, `toBlockGlobalNumber`, `toBlockHash32`).

### 3.4 Лайк

Добавить `REACTION_LIKE` с target сообщения.

### 3.5 Подписаться / отписаться от канала

Добавить `CONNECTION_FOLLOW` / `CONNECTION_UNFOLLOW` с target root канала.

### 3.6 Добавить в контакты/друзья

- Контакты: `CONNECTION_CONTACT` / `CONNECTION_UNCONTACT`.
- Друзья: `CONNECTION_FRIEND` / `CONNECTION_UNFRIEND`.

## 4) Есть ли метод «подписаться на канал» кроме публикации?

Да: подписка — это **не отдельный RPC**, а блок `CONNECTION_FOLLOW`, отправляемый через `AddBlock`.

## 5) Есть ли методы для сохранения технического состояния (например, сколько прочитано)

Да, есть `UpsertUserParam`.

Практично хранить:
- `channel_last_read:<bchName>:<lineCode> -> <lastSeenMessageSeq или blockNumber>`
- `channel_last_sync_at -> <time_ms>`

Ограничение: `users_params` сейчас хранит строки `param/value`, то есть сложные структуры лучше класть в JSON-строку с версией схемы.

## 6) Чего не хватает прямо сейчас

1. Нет RPC, который вернёт **список подписанных каналов** и счётчики сообщений.
2. Нет RPC, который вернёт **сообщения конкретного канала** (с пагинацией).
3. Нет RPC, который вернёт **подробный тред сообщения** (предки + все ответы) одним JSON.
4. Нет RPC «ленты событий» (кто лайкнул/ответил/подписался) как отдельного серверного канала.
