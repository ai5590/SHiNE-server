# utils.files

Хранение блокчейнов в виде файлов в папке `data/`.

---

## FileStoreUtil
Singleton для чтения и записи `.bch` файлов.

- `newBlockchain(id, data)` — создать новый файл `data/<id>.bch`
- `addDataToBlockchain(id, data)` — добавить байты в конец файла
- `readAllDataFromBlockchain(id)` — прочитать весь файл как массив байт

Каждый файл содержит последовательность полных блоков `[RAW][signature64][hash32]...`

---

## FileStoreUtilSelfTest
Тест: создаёт, дописывает и читает файл, чтобы проверить корректность.

---

Пока используется как временное файловое хранилище.
В будущем всё это уйдёт в SQL.



