const TOAST_HOST_ID = 'shine-toast-host';

const rtf = (() => {
  try {
    return new Intl.RelativeTimeFormat('ru', { numeric: 'auto' });
  } catch {
    return null;
  }
})();

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function pickUnit(seconds) {
  const abs = Math.abs(seconds);
  if (abs < 60) return ['second', Math.round(seconds)];
  const minutes = seconds / 60;
  if (Math.abs(minutes) < 60) return ['minute', Math.round(minutes)];
  const hours = minutes / 60;
  if (Math.abs(hours) < 24) return ['hour', Math.round(hours)];
  const days = hours / 24;
  if (Math.abs(days) < 30) return ['day', Math.round(days)];
  const months = days / 30;
  if (Math.abs(months) < 12) return ['month', Math.round(months)];
  const years = months / 12;
  return ['year', Math.round(years)];
}

export function formatRelativeTime(timestampMs) {
  const ts = toNumber(timestampMs);
  if (!ts) return '—';

  const now = Date.now();
  const diffSeconds = (ts - now) / 1000;
  const ageSeconds = now >= ts ? (now - ts) / 1000 : 0;
  const ageHours = ageSeconds / 3600;

  if (ageHours <= 10) {
    const [unit, value] = pickUnit(diffSeconds);
    if (rtf) return rtf.format(value, unit);

    const absValue = Math.abs(value);
    const suffix = value <= 0 ? 'назад' : 'через';
    const labels = {
      second: 'сек',
      minute: 'мин',
      hour: 'ч',
      day: 'д',
      month: 'мес',
      year: 'г',
    };
    return `${suffix} ${absValue} ${labels[unit] || ''}`.trim();
  }

  try {
    const dt = new Date(ts);
    const nowDt = new Date(now);
    const formatter = new Intl.DateTimeFormat('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      ...(dt.getFullYear() !== nowDt.getFullYear() ? { year: 'numeric' } : {}),
      hour: '2-digit',
      minute: '2-digit',
    });
    return formatter.format(dt);
  } catch {
    return new Date(ts).toLocaleString();
  }
}

function ensureToastHost() {
  let host = document.getElementById(TOAST_HOST_ID);
  if (host) return host;

  host = document.createElement('div');
  host.id = TOAST_HOST_ID;
  host.className = 'toast-host';
  document.body.append(host);
  return host;
}

export function showToast(message, { kind = 'success', timeoutMs = 2500 } = {}) {
  const text = String(message || '').trim();
  if (!text) return;

  const host = ensureToastHost();
  const toast = document.createElement('div');
  toast.className = `toast toast--${kind}`;
  toast.textContent = text;
  host.append(toast);

  requestAnimationFrame(() => {
    toast.classList.add('is-visible');
  });

  const hide = () => {
    toast.classList.remove('is-visible');
    toast.classList.add('is-hiding');
    setTimeout(() => toast.remove(), 220);
  };

  setTimeout(hide, Math.max(1200, Number(timeoutMs) || 2500));
}

export function softHaptic(duration = 15) {
  try {
    if (navigator?.vibrate) navigator.vibrate(Math.max(5, Math.min(30, Number(duration) || 15)));
  } catch {
    // ignore
  }
}

export function animatePress(el) {
  if (!el) return;
  el.classList.remove('is-springing');
  // force reflow
  // eslint-disable-next-line no-unused-expressions
  el.offsetWidth;
  el.classList.add('is-springing');
}

const CHANNEL_NOTIF_KEY = 'shine-channels-notify-v1';

export function readChannelNotificationsState() {
  try {
    const raw = localStorage.getItem(CHANNEL_NOTIF_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed;
  } catch {
    // ignore
  }
  return {};
}

export function writeChannelNotificationsState(nextState) {
  try {
    localStorage.setItem(CHANNEL_NOTIF_KEY, JSON.stringify(nextState || {}));
  } catch {
    // ignore
  }
}

export function makeAuthorLabel(login, localNumber) {
  const cleanLogin = String(login || 'автор');
  const n = Number(localNumber);
  if (!Number.isFinite(n) || n < 1) return cleanLogin;
  return `${cleanLogin} · #${n}`;
}

export function createSkeletonCard(className = '') {
  const card = document.createElement('div');
  card.className = `card skeleton-card ${className}`.trim();
  card.innerHTML = `
    <div class="skeleton-line w-40"></div>
    <div class="skeleton-line w-90"></div>
    <div class="skeleton-line w-70"></div>
  `;
  return card;
}

export function normalizeChannelDescription(value) {
  const text = String(value == null ? '' : value).trim().replace(/\s+/g, ' ');
  if (!text) return '';
  const chars = Array.from(text);
  if (chars.length <= 200) return text;
  return chars.slice(0, 200).join('');
}
