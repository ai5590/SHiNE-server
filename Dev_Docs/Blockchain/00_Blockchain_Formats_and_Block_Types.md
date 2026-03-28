# Форматы блокчейнов и блоков (индекс раздела)

Этот раздел разбит на несколько файлов, чтобы формат добавляемых блоков было проще читать и сопровождать.

## Структура раздела

- `01_Common_Block_Format.md` — общий бинарный формат блока (Frame v0), правила подписи и проверки.
- `02_Blockchain_Kinds_and_Lines.md` — виды блокчейнов и логические линии внутри цепочки.
- `10_TECH_Blocks.md` — системные блоки (`type=0`).
- `11_TEXT_Blocks.md` — текстовые блоки (`type=1`).
- `12_REACTION_Blocks.md` — реакции (`type=2`).
- `13_CONNECTION_Blocks.md` — связи/подписки (`type=3`).
- `14_USER_PARAM_Blocks.md` — пользовательские параметры (`type=4`).

## Быстрая карта типов

- `type=0` — TECH: HEADER, CREATE_CHANNEL.
- `type=1` — TEXT: POST/EDIT_POST/REPLY/EDIT_REPLY.
- `type=2` — REACTION: LIKE.
- `type=3` — CONNECTION: FRIEND/CONTACT/FOLLOW и обратные операции.
- `type=4` — USER_PARAM: key/value-параметры пользователя.

## Примечание

Если нужно добавить новый тип или подтип блока, сначала обновляйте профильный файл этого раздела, затем API-документацию в `Dev_Docs/API`.
