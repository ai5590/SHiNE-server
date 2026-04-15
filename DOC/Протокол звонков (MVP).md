# Протокол звонков (MVP)

Версия: browser-to-browser, runtime-only signaling.

## Цели
- Технические сообщения звонка не сохраняются в БД direct_messages.
- Первый INVITE рассылается всем активным сессиям получателя и дублируется web push.
- Последующие сигналы идут только в конкретную sessionId и не дублируются в push.

## Операции API

### 1) CallInviteBroadcast
Отправляет общий вызов пользователю.

Запрос payload:
- `toLogin: string`
- `callId: string`
- `type: 100` (INVITE)

Поведение сервера:
- Рассылает `IncomingCallInvite` во все активные WS-сессии `toLogin`.
- В payload события передаёт:
  - `fromLogin`
  - `fromSessionId` (session инициатора)
  - `toLogin`
  - `callId`
  - `type=100`
  - `timeMs`
- Отправляет web push уведомление о входящем вызове.

Ответ payload:
- `callId`
- `deliveredWsSessions`
- `deliveredFcmSessions`

### 2) CallSignalToSession
Отправляет технический сигнал в конкретную сессию.

Запрос payload:
- `toLogin: string`
- `targetSessionId: string`
- `callId: string`
- `type: int`
- `data: string` (для SDP/ICE/служебных строк)

Поведение сервера:
- Ищет только `targetSessionId`.
- Проверяет, что сессия принадлежит `toLogin`.
- Отправляет `IncomingCallSignal` только в эту сессию.
- В БД ничего не сохраняет.
- Push не отправляет.

Ответ payload:
- `delivered: boolean`

## Коды type
- `100` INVITE
- `110` RINGING
- `120` ACCEPT
- `130` DECLINE_BUSY
- `140` TIMEOUT
- `150` HANGUP
- `200` OFFER
- `210` ANSWER
- `220` ICE

## Правила UI/логики
- Если уже есть активный звонок и пришел новый INVITE -> автоответ `DECLINE_BUSY` без UI.
- После ACCEPT `callId` остаётся во всех OFFER/ANSWER/ICE сообщениях до конца звонка.
- При параллельных звонках A<->B допускается детерминированное правило, кто создаёт OFFER.

## Тайминги MVP
- Ожидание подтверждения/реакции после INVITE: до 5с (у инициатора).
- Ожидание принятия у входящего звонка: 20с.
- Общий лимит ожидания до соединения: 22с.
