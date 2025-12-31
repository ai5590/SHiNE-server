краткая «памятка себе» по базовым классам и как они связаны.

server.logic.InboundMessageProcessor (устаревший путь)

Роль: маршрутизатор бинарного протокола: берёт входящие байты, читает первые 4 байта как op, находит MessageHandler и отдаёт ему сообщение.
Что возвращает: байтовый ответ хэндлера; при ошибках — 4 байта со статусом (BAD_REQUEST/INTERNAL_ERROR).
Важно: сейчас фактически не используется (карта HANDLERS пустая/закомментирована) — это «след» старого бинарного протокола, который вы заменили на JSON-WS.

server.ws.BlockchainTmpRecoveryOnStartup

Роль: «автослесарь» при старте: чинит последствия падения во время записи блокчейн-файла.
Логика: ищет *.tmp_bch в data/, сравнивает размеры tmp, main .bch и state.fileSizeBytes из БД.
Решения:
если stateSize == mainSize → tmp мусор, удаляем;
если stateSize == tmpSize → tmp актуален, атомарно заменяем main;
если не сходится / подозрительно (нет state, но есть main+tmp и т.п.) → CRITICAL + стоп сервера.
Итог: гарантирует, что на запуске не будет «тихо битого» блокчейна.

server.ws.BlockchainWsEndpoint

Роль: WS-эндпоинт Jetty, который принимает и бинарные, и текстовые сообщения.
Connect: сохраняет Session, кладёт её в ConnectionContext.
Binary: асинхронно вызывает InboundMessageProcessor.process(msg) и отправляет байтовый ответ. (Это тот самый устаревший путь.)
Text (JSON): асинхронно вызывает JsonInboundProcessor.processJson(message, connectionContext) и отправляет строку JSON.
Close: удаляет соединение из ActiveConnectionsRegistry, чистит ConnectionContext.
Смысл: один входной узел WS, где JSON — основной протокол, binary — “наследие”.

server.ws.WsServer

Роль: точка входа сервера.

Порядок запуска:
BlockchainTmpRecoveryOnStartup.runRecoveryOrThrow() — если не смог починить/сопоставить → сервер не стартует;
читает порт из AppConfig (server.port), иначе 7070;
поднимает Jetty, конфигурирует WS-контейнер, маппит /ws → BlockchainWsEndpoint, ставит idleTimeout.
Итог: «бутстрап»: сначала безопасность файлов, потом сеть.