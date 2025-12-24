формат добавления блоков и из чего реально состоит блок, плюс как именно считаются хэши и подписи.

1) Формат добавления блоков в файл .bch

Файл — это просто конкатенация блоков один за другим, без разделителей:

BLOCK_0 | BLOCK_1 | BLOCK_2 | ...


Чтобы читать файл, ты идёшь по нему последовательно:

Читаешь первые 4 байта = recordSize

Понимаешь, сколько всего байт занимает блок:

fullBlockLen = recordSize + 64 + 32


Считываешь оставшиеся байты блока и парсишь:

RAW часть длиной recordSize

затем signature64

затем hash32

Это удобно тем, что файл можно дописывать (APPEND) хоть по сети, хоть локально: блоки самодостаточные.

2) Из чего состоит блок (формат блока)
   2.1. RAW (участвует в preimage и верификации; подпись/хэш к нему «пришиты»)

BigEndian, фиксированный заголовок + body:

RAW:

[4] recordSize (int) — размер RAW включая эти 4 байта, но без signature+hash

[4] recordNumber (int) — глобальный номер блока (цепочка)

[8] timestamp (long) — unix seconds

[2] line (short) — линия (у вас пока по сути одна)

[4] lineNumber (int) — номер в линии (у вас пока обычно равен global)

[N] bodyBytes — тело, начинается с [2] type + [2] version, дальше payload

RAW_HEADER_SIZE = 4 + 4 + 8 + 2 + 4 = 22 байта

2.2. TAIL (не входит в recordSize)

[64] signature64 — Ed25519 подпись

[32] hash32 — SHA-256 хэш

Итого полный блок:

FULL = RAW(recordSize bytes) + signature64 + hash32

3) Формат body (тела блока)

У тебя правило железное: каждый body начинается одинаково:

[2] type

[2] version

дальше payload по типу/версии

Парсер делает ключ:

key = (type<<16) | version

3.1 HeaderBody (type=0, ver=1)

Полный bodyBytes:

[2] type=0

[2] ver=1

[8] tag "SHiNE001" (ASCII)

[1] loginLength (uint8)

[N] login UTF-8

Это «генезис-смысл» для идентичности: в теле блока явно хранится логин.

3.2 TextBody (type=1, ver=1)

[2] type=1

[2] ver=1

[N] message UTF-8

4) Как считается хэш (hash32)

Важно: hash32 в блоке — это не hash(rawBytes).

Ты считаешь:

4.1 Preimage
preimage =
"SHiNE" +
[1] loginLen + loginBytes(UTF-8) +
prevGlobalHash32 +
prevLineHash32 +
rawBytes


Где:

"SHiNE" — доменная метка (защита от «подпиши этим же ключом что-то другое»)

loginLen + loginBytes — привязка блока к пользователю/цепочке

prevGlobalHash32 — сцепление по глобальной цепи

prevLineHash32 — сцепление по линии

rawBytes — содержимое текущего блока без подписи/хэша

4.2 Сам хэш
hash32 = SHA-256(preimage)


И именно этот hash32 должен лежать в конце блока.

5) Как считается подпись (signature64)

Подписывается не rawBytes, а hash32:

signature64 = Ed25519.sign(hash32, privateKey)


Проверка:

Пересобираем preimage

Считаем hash32 = sha256(preimage)

Сверяем hash32 с тем, что лежит в блоке

Проверяем подпись:

Ed25519.verify(hash32, signature64, publicKey32)


Это хорошая схема: подпись всегда фиксированного размера, а хэш — единая точка правды.

6) Что является «цепочкой» и чем блоки сцеплены

Сцепление идёт через prevGlobalHash32 и prevLineHash32, которые ты передаёшь в verifyAll(...).

То есть логика добавления нового блока (в терминах данных) такая:

берём последний блок

достаём его hash32

это и будет prevGlobalHash32 для нового блока

prevLineHash32 — по той линии, куда добавляем (у вас пока это то же самое, потому что одна линия/нет разветвления)

У тебя даже отдельно отмечено: пока нет отдельных линий, lastLineHash == lastGlobalHash — значит сейчас обе цепочки фактически совпадают.

7) Мини-схема “как добавить новый блок” (пошагово)

Собрать BodyRecord → получить bodyBytes = body.toBytes()

Собрать RAW:

recordNumber = lastRecordNumber + 1

timestamp = nowSeconds

line (скорее всего 0 или 1 — как вы договорились)

lineNumber = lastLineNumber + 1 (сейчас равно global)

recordSize = RAW_HEADER_SIZE + bodyBytes.length

rawBytes = new BchBlockEntry(...).getRawBytes() (или собрать вручную)

preimage = buildPreimage(login, prevGlobalHash32, prevLineHash32, rawBytes)

hash32 = sha256(preimage)

signature64 = Ed25519.sign(hash32, privateKey)

Собрать финальный BchBlockEntry(..., signature64, hash32)

Дописать toBytes() в файл .bch

8) Важные нюансы (чтобы не словить «тихий баг»)

recordSize включает себя (первые 4 байта) — это ок, просто помнить.

bodyLen = recordSize - RAW_HEADER_SIZE — значит RAW_HEADER_SIZE обязан быть всегда синхронен с реальным RAW.

login в preimage ограничен 255 байт (у тебя [1] loginLen) — это правильно и предсказуемо.

В HeaderBody tag фиксирован "SHiNE001" — фактически версия протокола body-header’а.