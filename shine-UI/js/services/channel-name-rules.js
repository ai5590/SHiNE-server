const MIN_LEN = 3;
const MAX_LEN = 32;
const ALLOWED_CHARS_RE = /^[\p{Script=Latin}\p{Script=Cyrillic}0-9 _-]+$/u;

export function normalizeChannelDisplayName(value) {
  if (value == null) return '';
  return String(value).trim().replace(/\s+/g, ' ');
}

export function normalizeChannelDescription(value) {
  if (value == null) return '';
  return String(value).trim().replace(/\s+/g, ' ');
}

export function toCanonicalChannelSlug(value) {
  const normalized = normalizeChannelDisplayName(value);
  if (!normalized) return '';

  const lowered = normalized.toLowerCase().replace(/\u0451/g, '\u0435');
  let out = '';
  let pendingSeparator = false;

  for (const ch of lowered) {
    if (ch === ' ' || ch === '_' || ch === '-') {
      pendingSeparator = out.length > 0;
      continue;
    }
    if (!/[\p{Script=Latin}\p{Script=Cyrillic}0-9]/u.test(ch)) {
      return '';
    }
    if (pendingSeparator && out.length > 0) out += '-';
    out += ch;
    pendingSeparator = false;
  }

  return out.replace(/-+$/g, '');
}

export function validateChannelDisplayName(value) {
  const normalized = normalizeChannelDisplayName(value);
  if (!normalized) {
    return { ok: false, code: 'blank', normalized: '', slug: '' };
  }

  const length = Array.from(normalized).length;
  if (length < MIN_LEN) {
    return { ok: false, code: 'too_short', normalized, slug: '' };
  }
  if (length > MAX_LEN) {
    return { ok: false, code: 'too_long', normalized, slug: '' };
  }
  if (!ALLOWED_CHARS_RE.test(normalized)) {
    return { ok: false, code: 'bad_chars', normalized, slug: '' };
  }
  if (normalized === '0') {
    return { ok: false, code: 'reserved', normalized, slug: '' };
  }

  const slug = toCanonicalChannelSlug(normalized);
  if (!slug) {
    return { ok: false, code: 'bad_chars', normalized, slug: '' };
  }

  return { ok: true, code: '', normalized, slug };
}

export function channelNameErrorText(code) {
  switch (String(code || '').trim()) {
    case 'blank':
      return 'Введите название канала.';
    case 'too_short':
      return 'Название слишком короткое: минимум 3 символа.';
    case 'too_long':
      return 'Название слишком длинное: максимум 32 символа.';
    case 'bad_chars':
      return 'Разрешены кириллица, латиница, цифры, пробел, _ и -.';
    case 'reserved':
      return 'Название "0" зарезервировано.';
    default:
      return 'Некорректное название канала.';
  }
}

export function channelDescriptionErrorText(value) {
  const normalized = normalizeChannelDescription(value);
  if (new TextEncoder().encode(normalized).length > 200) {
    return 'Описание слишком длинное: максимум 200 байт UTF-8.';
  }
  return '';
}
