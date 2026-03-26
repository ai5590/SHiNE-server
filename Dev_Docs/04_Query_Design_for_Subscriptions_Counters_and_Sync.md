# Проектирование запросов: подписки, счетчики, синхронизация, сообщения

## 1) Цель

Сделать один запрос, который отдаёт:
- все каналы пользователя (подписки + опционально self-каналы);
- сколько сообщений всего в каждом канале;
- сколько новых (с учётом last_read).

И отдельно:
- запрос сообщений канала;
- запрос треда конкретного сообщения (предки + все потомки + stats).

## 2) Предлагаемые API

## A. `ListMyChannelsWithCounters`

### Request
```json
{
  "op": "ListMyChannelsWithCounters",
  "requestId": "...",
  "includeSelf": true,
  "includePrivate": true
}
```

### Response
```json
{
  "op": "ListMyChannelsWithCounters",
  "requestId": "...",
  "status": 200,
  "channels": [
    {
      "channelId": "targetBch:targetRootBlock",
      "channelType": "personal|public|system_notifications|dm",
      "ownerLogin": "Alice",
      "bchName": "Alice-001",
      "lineCode": 0,
      "title": "Alice main",
      "totalMessages": 1234,
      "lastReadSeq": 1200,
      "newMessages": 34,
      "lastMessage": {
        "blockNumber": 3500,
        "timeMs": 1760000000000,
        "preview": "..."
      }
    }
  ]
}
```

## B. `GetChannelMessages`

- Возвращает сообщения канала по `channelId` или `(bchName,lineCode)`.
- Нужны `limit`, `before/after`, сортировка и флаг `includeStats`.

## C. `GetMessageThreadGraph`

Один JSON для:
- целевого сообщения;
- всей цепочки родителей до корня;
- всех ответов (даже 100+);
- stats по каждому узлу (`likes/replies/edits`).

## 3) Как считать total/new

## Источник truth по подпискам

Использовать `connections_state` где `rel_type=FOLLOW`.

## Источник сообщений канала

`blocks` где `msg_type=TEXT` и блок принадлежит линии канала (`line_code`).

## Источник last_read

`users_params` ключ вида `read.<channelId>` = число (last seen seq или blockNumber).

`newMessages = max(0, totalMessages - lastReadSeq)` (или по blockNumber/seq в выбранной модели).

## 4) Offline/online синхронизация

Рекомендуемый поток:

1. Клиент локально хранит last_read/last_sync.
2. При подключении делает:
   - `ListMyChannelsWithCounters`
   - затем по каналам с `newMessages > 0` делает `GetChannelMessages`.
3. После отображения сообщений обновляет сервер через `UpsertUserParam` (или новый bulk-метод).
4. Сервер всегда остаётся source of truth для chain/подписок; клиент — кэш.

## 5) Подписи и device key

- Операции изменения данных пользователя (`UpsertUserParam`) уже подписываются device key.
- Для нового bulk-обновления read-состояния лучше сохранить тот же принцип: подписанный payload от device key.

## 6) Разделение каналов: личные / обычные / системные

Предложение:

- `personal_main`: root = HEADER (`lineCode=0`) пользователя.
- `public_channel`: root = CREATE_CHANNEL (lineCode = blockNumber root).
- `dm_channel`: отдельный тип канала (пока не реализован; либо как специальный connection+line policy).
- `system_notifications`: виртуальный/материализованный канал событий (ответы, лайки, follow/unfollow/friend/contact).

## 7) Канал уведомлений (ответили/лайкнули/подписались)

Сейчас это не готово как отдельная сущность API.

Два варианта:

1. **Виртуальный канал (рекомендуется сначала)**
   - не писать новые блоки;
   - собирать события SQL-запросом из `blocks` + `message_stats` + `connections_state`.

2. **Материализованный канал**
   - триггеры/воркер пишут отдельные event-записи в таблицу уведомлений;
   - быстрее чтение, но сложнее консистентность.

## 8) Что уже частично готово

- База уже считает `likes/replies/edits` через `message_stats` триггеры.
- База уже держит текущее состояние связей через `connections_state`.

## 9) Что нужно дописать в сервере

1. Новые handlers в `JsonHandlerRegistry`:
   - `ListMyChannelsWithCounters`
   - `GetChannelMessages`
   - `GetMessageThreadGraph`
   - `ListNotificationFeed`.
2. Новые DAO + SQL-представления/CTE.
3. Унификация subType-констант (иначе статистика и выборки будут «плыть»).
4. Пагинация и лимиты ответа (для больших тредов).
