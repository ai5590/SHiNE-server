# TEXT блоки (`type=1`, `version=1`)

TEXT-тип хранит сообщения и редактирования.

## Подтипы

1. `subType=10` — `TEXT_POST`
   - пост в линии канала;
   - содержит line-поля + текст.

2. `subType=11` — `TEXT_EDIT_POST`
   - редактирование поста;
   - line-поля + target на оригинальный POST + новый текст.

3. `subType=20` — `TEXT_REPLY`
   - ответ на сообщение;
   - target (`toBlockchainName`, `toBlockGlobalNumber`, `toBlockHash32`) + текст.

4. `subType=21` — `TEXT_EDIT_REPLY`
   - редактирование ответа;
   - target на исходный REPLY + новый текст.

## Правило для edit

`EDIT_POST` и `EDIT_REPLY` должны ссылаться на **оригинальный** блок, а не на предыдущий edit.
