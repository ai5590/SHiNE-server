Протокол обновления существующей сессии
1. Назначение

RefreshSession используется для:

проверки, что клиент всё ещё владеет правильным sessionPwd;

продления “живучести” сессии;

возврата клиенту его storagePwd.

2. Формат запроса RefreshSession
   {
   "op": "RefreshSession",
   "requestId": "abc-1",
   "payload": {
   "sessionId": "BASE64_SESSION_ID",
   "sessionPwd": "BASE64_SESSION_PWD"
   }
   }

3. Действия сервера при RefreshSession
1) Поиск записи в active_sessions
   SELECT * FROM active_sessions WHERE sessionId = ?


Если нет — ошибка:

{
"op": "RefreshSession",
"status": 404,
"payload": {
"ok": false,
"code": "SESSION_NOT_FOUND",
"message": "Сессия не найдена"
}
}

2) Проверка поля sessionPwd

Сравнивается payload.sessionPwd с полем active_sessions.sessionPwd.

Если неверно:

{
"op": "RefreshSession",
"status": 401,
"payload": {
"ok": false,
"code": "SESSION_PWD_MISMATCH",
"message": "Неверный пароль сессии"
}
}

3) Обновление времени последней авторизации
   UPDATE active_sessions
   SET lastAuthirificatedAtMs = ?
   WHERE sessionId = ?

4) Возвращение клиенту актуального storagePwd
   {
   "op": "RefreshSession",
   "requestId": "abc-1",
   "status": 200,
   "payload": {
   "ok": true,
   "storagePwd": "BASE64_STORAGE_PWD"
   }
   }

4. Итоговая схема RefreshSession
   Client → RefreshSession(sessionId, sessionPwd)
   → Server validates → updates lastAuth
   → returns storagePwd
