Документ: shine-server-bd — таблицы и структура
Таблицы (что хранят)

solana_users — локальная таблица пользователей (логин + ключ устройства + опциональный solanaKey).

active_sessions — активные сессии пользователей (секреты сессии, время, push-подписки, IP/клиент-инфо).

users_params — сохранённые параметры пользователя (например, состояние просмотренности лент и прочие настройки), с подписью.

ip_geo_cache — кэш геолокации по IP (чтобы не дергать внешние сервисы каждый раз).

blockchain_state — агрегатное “текущее состояние” блокчейна по blockchainName (лимит, размер, последние хэши/номера).

blocks — сохранённые блоки/сообщения блокчейна (байты блока + связи “кому/куда” для адресации).

solana_users — пользователи и их ключи

login TEXT — логин пользователя (PRIMARY KEY)
deviceKey TEXT — публичный ключ устройства (обязателен)
solanaKey TEXT — дополнительный ключ Solana (может быть NULL)

active_sessions — активные сессии пользователя

sessionId TEXT — идентификатор сессии (PRIMARY KEY)
login TEXT — логин владельца сессии (FK → solana_users.login)
sessionPwd TEXT — пароль/секрет сессии (используется сервером)
storagePwd TEXT — пароль/секрет для шифрования хранилища/данных
sessionCreatedAtMs INTEGER — время создания сессии (ms)
lastAuthirificatedAtMs INTEGER — время последней успешной аутентификации/refresh (ms)
pushEndpoint TEXT — endpoint для web-push (nullable)
pushP256dhKey TEXT — ключ p256dh для web-push (nullable)
pushAuthKey TEXT — auth key для web-push (nullable)
clientIp TEXT — IP клиента при auth/refresh (nullable)
clientInfoFromClient TEXT — строка client-info от клиента (nullable)
clientInfoFromRequest TEXT — строка client-info, собранная сервером (nullable)
userLanguage TEXT — предпочитаемый язык пользователя (nullable)

users_params — параметры/состояния пользователя

login TEXT — логин владельца параметра (FK → solana_users.login)
param TEXT — имя параметра (в паре с login образует UNIQUE)
bch_channel_id INTEGER — id канала/ленты/контекста блокчейна (по умолчанию 0)
value TEXT — значение параметра (nullable)
time_ms INTEGER — время обновления параметра (ms)
pubkey_num INTEGER — номер/индекс публичного ключа, которым подписано значение
signature TEXT — подпись значения параметра (строка)

ip_geo_cache — кэш геоданных по IP

ip TEXT — IP-адрес (PRIMARY KEY)
geo TEXT — строка гео-описания (“Country, City” и т.п.) (nullable)
updated_at_ms INTEGER — время последнего обновления (ms)

blockchain_state — текущее состояние блокчейна по blockchainName

blockchainName TEXT — имя/идентификатор блокчейна (PRIMARY KEY)
login TEXT — владелец блокчейна (FK → solana_users.login)
blockchainKey TEXT — публичный ключ блокчейна (используется для подписи блоков)
size_limit INTEGER — лимит размера блокчейн-файла (байты)
file_size_bytes INTEGER — текущий размер файла блокчейна (байты)
last_global_number INTEGER — номер последнего глобального блока (genesis обычно -1)
last_global_hash TEXT — хэш последнего глобального блока (пусто для “нулевого” состояния)
updated_at_ms INTEGER — время обновления состояния (ms)
line0_last_number INTEGER — последний номер в линии 0
line0_last_hash TEXT — последний хэш в линии 0
line1_last_number INTEGER — последний номер в линии 1
line1_last_hash TEXT — последний хэш в линии 1
line2_last_number INTEGER — последний номер в линии 2
line2_last_hash TEXT — последний хэш в линии 2
line3_last_number INTEGER — последний номер в линии 3
line3_last_hash TEXT — последний хэш в линии 3
line4_last_number INTEGER — последний номер в линии 4
line4_last_hash TEXT — последний хэш в линии 4
line5_last_number INTEGER — последний номер в линии 5
line5_last_hash TEXT — последний хэш в линии 5
line6_last_number INTEGER — последний номер в линии 6
line6_last_hash TEXT — последний хэш в линии 6
line7_last_number INTEGER — последний номер в линии 7
line7_last_hash TEXT — последний хэш в линии 7

blocks — блоки/сообщения блокчейна (байтовое тело + адресация)

login TEXT — владелец (FK → solana_users.login)
bchName TEXT — имя блокчейна (FK → blockchain_state.blockchainName)
blockGlobalNumber INTEGER — глобальный номер блока
blockGlobalPreHashe TEXT — предыдущий глобальный хэш (строка)
blockLineIndex INTEGER — индекс линии (0..7)
blockLineNumber INTEGER — номер блока внутри линии
blockLinePreHashe TEXT — предыдущий хэш внутри линии
msgType INTEGER — тип сообщения/записи (код)
blockByte BLOB — сырые байты блока (nullable)
to_login TEXT — адресат логин (nullable)
toBchName TEXT — адресат блокчейн (nullable)
toBlockGlobalNumber INTEGER — целевой глобальный номер (nullable)
toBlockHashe TEXT — целевой хэш (nullable)