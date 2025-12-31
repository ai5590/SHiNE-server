shine-server-protocol
Библиотека JSON-протокол поверх WebSocket для взаимодействия с клиентами.



Всё общение — JSON поверх WebSocket
Формат всегда один:

request:  op + requestId + payload
response: op + requestId + status + payload


Net_Event / Net_Request / Net_Response
Базовые классы протокола.
requestId связывает запрос и ответ, status = результат.

Хэндлер = логика операции
Каждый op обрабатывается своим JsonMessageHandler.

Entities (Request / Response)
DTO-классы для Jackson:

Net_Xxx_Request — что приходит от клиента

Net_Xxx_Response — что уходит клиенту

JsonHandlerRegistry
Связывает:

op → RequestClass
op → Handler


JsonInboundProcessor
Единая точка входа:
парсит JSON → маппит payload → вызывает handler → собирает ответ JSON.

Папки по темам

auth/ — авторизация и сессии
(AuthChallenge → CreateAuthSession → Refresh / List / Close)

blockchain/ — AddBlock

tempToTest/ — AddUser (временный, потом уйдёт в блокчейн-логику)

ConnectionContext
Состояние одного WebSocket-подключения (login, session, authStatus).

ActiveConnectionsRegistry
Глобальный реестр активных авторизованных соединений
(нужно для закрытия других сессий).