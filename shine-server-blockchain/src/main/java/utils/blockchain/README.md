# utils.blockchain

Хранит состояние всех цепочек и их владельцев (логин, ключ, хэш, размер).

---

## Классы

### `BchInfoManager`
Singleton-менеджер всех известных цепочек.  
Хранит карту `blockchainId → BchInfoEntry` в файле `data/blockchain_info.json`.  
Обновляется после каждого добавления блока.

Главные методы:
- `getInstance()` — получить менеджер.
- `addBlockchain(id, login, key32, limit)` — создать новую цепочку (первый блок).
- `updateBlockchainState(id, num, hash, size)` — обновить состояние после блока.
- `getBchInfo(id)` — получить всю запись (`BchInfoEntry`) по цепочке.
- `getAllLoginsSnapshot()` — карта `id → login` для поиска.

---

### `BchInfoEntry`
Сущность одной цепочки:
- `blockchainId`
- `userLogin`
- `publicKeyBase64`
- `blockchainSizeLimit`
- `blockchainSize`
- `lastBlockNumber`
- `lastBlockHash`

Метод:
- `getPublicKey32()` — вернуть ключ в бинарном виде.

---

## Итого
- Менеджер хранит метаданные всех цепочек.
- Сущность описывает одну цепочку.
- Сейчас всё лежит в JSON, позже можно заменить на SQL без изменений кода.
