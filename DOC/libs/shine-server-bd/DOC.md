shine-server-bd — это библиотека реалезующая всю работу с БД:

хранит пользователей/сессии/параметры/кэш IP→гео и данные блокчейна (состояние + блоки), предоставляя единый SqliteDbController для соединений, набор DAO под каждую таблицу (Singleton, методы с Connection для транзакций и без Connection — сами открывают/закрывают), и простые entity-модели как контейнеры данных для маппинга ResultSet↔Java.

Логика структуры классов (в двух словах):

shine.db.SqliteDbController — один вход в БД: читает db.path, при отсутствии файла создаёт БД, выдаёт новые Connection и настраивает PRAGMA.
shine.db.DatabaseInitializer — разовая сборка схемы (таблицы + индексы).


shine.db.entities.* — POJO-модели строк таблиц (без логики, только поля/геттеры/сеттеры + иногда удобные методы вроде getDeviceKeyByte()).
shine.db.dao.* — DAO по таблицам: ActiveSessionsDAO, SolanaUsersDAO, UserParamsDAO, IpGeoCacheDAO, BlockchainStateDAO, BlocksDAO; плюс “сервисные” DAO:

UserCreateDAO — атомарная регистрация пользователя в транзакции (BEGIN IMMEDIATE + rollback/commit).
// Временное решение позволяющее регистрировать новых пользователей
// атомарно и добавляет запись и в solana_users и в BlockchainState

