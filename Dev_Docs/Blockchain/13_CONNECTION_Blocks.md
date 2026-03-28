# CONNECTION блоки (`type=3`, `version=1`)

CONNECTION-тип описывает социальные связи и подписки.

## Подтипы

1. `subType=10` — `CONNECTION_FRIEND`
2. `subType=11` — `CONNECTION_UNFRIEND`
3. `subType=20` — `CONNECTION_CONTACT`
4. `subType=21` — `CONNECTION_UNCONTACT`
5. `subType=30` — `CONNECTION_FOLLOW`
6. `subType=31` — `CONNECTION_UNFOLLOW`

## Общий формат payload

- line-поля (`lineCode`, `prevLineNumber`, `prevLineHash32`, `thisLineNumber`)
- target (`toBlockchainName`, `toBlockGlobalNumber`, `toBlockHash32`)

## Правила target

- FRIEND/CONTACT обычно указывают на `HEADER` цели (`block 0`).
- FOLLOW указывает на root канала:
  - `HEADER` для канала `0`;
  - `CREATE_CHANNEL` для пользовательского канала.
