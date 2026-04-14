function extractCode(message = '') {
  const match = String(message || '').match(/\(([A-Z0-9_]+)\)\s*$/i);
  return match ? String(match[1]).toUpperCase() : '';
}

function normalizeText(error) {
  return String(error?.message || '').trim().toLowerCase();
}

export function toUserMessage(error, fallback = 'Действие не выполнено. Попробуйте еще раз.') {
  const raw = String(error?.message || '').trim();
  const text = normalizeText(error);
  const code = String(error?.code || extractCode(raw) || '').toUpperCase();

  if (
    text.includes('webcrypto') ||
    text.includes('crypto.subtle') ||
    text.includes("reading 'digest'") ||
    text.includes('not supported on insecure origins')
  ) {
    return 'Криптография браузера недоступна. Откройте приложение через HTTPS или localhost.';
  }

  if (
    text.includes('mixed content') ||
    text.includes('insecure websocket connection') ||
    (text.includes('https') && text.includes('ws://'))
  ) {
    return 'Подключение заблокировано: страница открыта по HTTPS, а сервер указан как ws://. Используйте wss://.';
  }

  if (
    text.includes('не удалось подключиться') ||
    text.includes('failed to connect websocket') ||
    text.includes('websocket закрыто') ||
    text.includes('соединение websocket закрыто')
  ) {
    return 'Сервер недоступен. Проверьте, что backend запущен, и повторите попытку.';
  }

  if (text.includes('таймаут') || text.includes('timeout waiting')) {
    return 'Сервер отвечает слишком долго. Повторите попытку через несколько секунд.';
  }

  if (
    code === 'USER_NOT_FOUND' ||
    text.includes('user not found') ||
    text.includes('пользователь не найден')
  ) {
    return 'Пользователь не найден. Проверьте логин.';
  }

  if (
    code === 'BAD_CHANNEL_NAME' ||
    text.includes('channel name must match') ||
    text.includes('channelname contains unsupported') ||
    text.includes('channelname length must be 3..32') ||
    text.includes('bad_channel_name')
  ) {
    return 'Некорректное название канала. Разрешены кириллица, латиница, цифры, пробел, _ и - (3..32 символа).';
  }

  if (text.includes('channel name is required') || text.includes('введите имя канала')) {
    return 'Введите имя канала.';
  }

  if (code === 'CHANNEL_NAME_ALREADY_EXISTS' || text.includes('channel_name_already_exists')) {
    return 'Такое название уже занято. Попробуйте немного изменить его.';
  }

  if (
    code === 'PREV_LINE_BLOCK_NOT_FOUND' ||
    code === 'LINE_ERR_NO_PREV' ||
    text.includes('prev_line_block_not_found') ||
    text.includes('line_err_no_prev')
  ) {
    return 'Базовый блок линии не найден. Обновите страницу и повторите действие.';
  }

  if (
    code === 'BAD_PREV_LINE_HASH' ||
    code === 'LINE_ERR_PREV_HASH_MISMATCH' ||
    text.includes('bad_prev_line_hash') ||
    text.includes('line_err_prev_hash_mismatch') ||
    text.includes('prevlinehash')
  ) {
    return 'Конфликт состояния канала. Обновите страницу и повторите действие.';
  }

  if (code === 'LINE_ERR_PARTIAL_FIELDS' || text.includes('line_err_partial_fields')) {
    return 'Некорректные данные канала. Обновите страницу и повторите действие.';
  }

  if (code === 'NOT_AUTHENTICATED' || text.includes('session is not ready for signing')) {
    return 'Сессия недействительна. Выполните вход заново.';
  }

  if (
    code === 'SESSION_NOT_FOUND' ||
    code === 'SESSION_KEY_NOT_ACTUAL' ||
    code === 'SESSION_OF_ANOTHER_USER'
  ) {
    return 'Сессия устарела. Войдите заново и повторите действие.';
  }

  if (code === 'UNSUPPORTED_KEY_ALGORITHM' || text.includes('unsupported key algorithm')) {
    return 'Ключ устройства не поддерживается сервером. Очистите локальные ключи и войдите заново.';
  }

  if (!raw) return fallback;
  return raw;
}
