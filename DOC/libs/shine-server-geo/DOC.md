shine-server-geo

Назначение: утилиты для получения “кто подключился” (IP/UA/язык) и геолокации по IP (с опциональным кэшем в БД).

Классы:

ClientInfoService — собирает строку UA/ch-ua/platform/mobile/remoteIP, вытаскивает реальный IP (приоритет: X-Forwarded-For → X-Real-IP → remoteAddress), парсит первый Accept-Language.
GeoLookupService — геолокация по IP через внешний API (ip-api.com), умеет вариант без кэша и с кэшем в таблице ip_geo_cache через IpGeoCacheDAO (пишет даже unknown), плюс метод получения внешнего IP через api.ipify.org.
GeoLookupTestMain — консольный тест: берёт IP из аргумента или определяет внешний, вызывает геолокацию и печатает результат + время.


Внешние (публично используемые) методы:

ClientInfoService.buildClientInfoString(Session) — формирует строку с User-Agent, client-hints и реальным IP клиента
ClientInfoService.extractClientIp(Session) — извлекает реальный IP (X-Forwarded-For / X-Real-IP / remoteAddress)
ClientInfoService.extractPreferredLanguageTag(Session) — возвращает основной язык клиента из Accept-Language
GeoLookupService.resolveCountryCityOrIp(String ip) — геолокация по IP без кэша (Country, City или unknown)
GeoLookupService.resolveCountryCityOrIpWithCache(String ip) — геолокация по IP с кэшированием в БД
GeoLookupService.fetchPublicIpOrDefault(String fallbackIp) — получение внешнего IP текущей машины