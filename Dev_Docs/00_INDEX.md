# Dev_Docs — оглавление

Этот набор документов сделан по текущему состоянию кода сервера (`/workspace/SHiNE-server`) и разбит по темам.

## Список документов

0. **API/00_Common_API_Format.md**
   Общий формат JSON-запросов и JSON-ответов по всему API: `op`, `requestId`, `status`, `ok`, `payload`, единые правила успеха и ошибок.

0. **API/01_User_Registration_API.md**
   Временная глава API по регистрации пользователя: `AddUser` и временный `GetUser`, с пометкой о будущем переходе проверки identity напрямую через Solana.

0. **API/02_Authentication_API.md**
   Глава API по авторизации: `AuthChallenge`, `CreateAuthSession`, `SessionChallenge`, `SessionLogin`, подписи, `deviceKey`, `sessionKey`.

0. **API/03_Session_Management_API.md**
   Глава API по управлению сессиями: `ListSessions` и `CloseActiveSession`.


0. **API/04_Add_Block_to_Blockchain_API.md**
   Глава API по записи блоков: формат `AddBlock`, коды ошибок, поддержанные типы/подтипы блоков и рекомендации по ресинхронизации.

1. **01_Connection_and_Sessions.md**
   Процесс подключения к WebSocket, авторизация (двухшаговая), создание сессии, вход в существующую сессию, просмотр и закрытие сессий.

2. **02_Blockchain_Structure_and_Block_Types.md**
   Архитектура блокчейна, форматы и типы блоков, что уже можно делать каждым типом блока.

3. **03_Addable_Blocks_Channels_Messages_Connections.md**
   Какие блоки добавляются через `AddBlock`, как делать каналы/подписки/контакты/друзей/лайки/ответы, что уже есть и чего не хватает в API.

4. **04_Query_Design_for_Subscriptions_Counters_and_Sync.md**
   Проектирование новых API-запросов: список подписок с общим/новым числом сообщений, список сообщений канала, граф ответов для сообщения, поток синхронизации online/offline.

5. **05_Open_Questions_and_TODO.md**
   Список открытых вопросов, рисков и приоритетов для доработки сервера.

## Почему так разбито

- **Сначала протокол и сессии** — это входная точка клиента.
- **Потом блокчейн-слой** — какие данные вообще можно выразить блоками.
- **Потом прикладные функции (каналы/сообщения/связи)** — что реально можно сделать уже сейчас.
- **Потом проектирование отсутствующих запросов** — чтобы закрыть разрыв между текущим сервером и нужной функциональностью клиента.
- **В конце вопросы** — чтобы быстро согласовать спорные места.


6. **Blockchain/00_Blockchain_Formats_and_Block_Types.md**
   Индекс раздела по форматам блокчейна и блоков.

7. **Blockchain/01_Common_Block_Format.md**
   Общий бинарный формат блока (Frame v0), подпись и обязательные проверки на `AddBlock`.

8. **Blockchain/02_Blockchain_Kinds_and_Lines.md**
   Виды блокчейнов (`login-NNN`) и логические линии внутри цепочки.

9. **Blockchain/10_TECH_Blocks.md**
   Системные блоки: `HEADER_COMPAT`, `TECH_CREATE_CHANNEL`.

10. **Blockchain/11_TEXT_Blocks.md**
    Текстовые блоки: `POST`, `EDIT_POST`, `REPLY`, `EDIT_REPLY`.

11. **Blockchain/12_REACTION_Blocks.md**
    Реакции: `REACTION_LIKE`.

12. **Blockchain/13_CONNECTION_Blocks.md**
    Связи и подписки: `FRIEND/CONTACT/FOLLOW` и обратные операции.

13. **Blockchain/14_USER_PARAM_Blocks.md**
    Пользовательские параметры `key/value`.
