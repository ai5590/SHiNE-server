import { renderHeader } from '../components/header.js';
import { clearAppLogEntries, getAppLogEntries } from '../state.js';

export const pageMeta = { id: 'app-log-view', title: 'Лог приложения' };

function formatTime(ts) {
  try {
    return new Date(ts).toLocaleTimeString('ru-RU');
  } catch {
    return String(ts || '');
  }
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Лог приложения',
      leftAction: { label: '←', onClick: () => navigate('settings-view') },
    }),
  );

  const controls = document.createElement('div');
  controls.className = 'card row';
  controls.style.justifyContent = 'flex-start';
  controls.style.gap = '8px';
  controls.style.flexWrap = 'wrap';
  controls.innerHTML = `
    <button class="ghost-btn" type="button" data-action="refresh">Обновить</button>
    <button class="ghost-btn" type="button" data-action="copy-all">Скопировать всё</button>
    <button class="ghost-btn" type="button" data-action="clear">Очистить</button>
  `;

  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка лога...';

  const list = document.createElement('div');
  list.className = 'stack';

  function entriesToClipboardText(entries) {
    return entries
      .slice()
      .reverse()
      .map((entry) => {
        const level = String(entry.level || 'info').toUpperCase();
        const source = String(entry.source || 'ui');
        const header = `[${formatTime(entry.ts)}] ${level} ${source}`;
        const message = String(entry.message || '');
        const details = String(entry.details || '').trim();
        return details ? `${header}\n${message}\n${details}` : `${header}\n${message}`;
      })
      .join('\n\n');
  }

  async function copyTextToClipboard(text) {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    ta.style.pointerEvents = 'none';
    document.body.append(ta);
    ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return !!ok;
  }

  function renderEntries() {
    const entries = getAppLogEntries();
    list.innerHTML = '';

    if (!entries.length) {
      const empty = document.createElement('div');
      empty.className = 'card meta-muted';
      empty.textContent = 'Лог пока пуст.';
      list.append(empty);
      status.className = 'status-line is-available';
      status.textContent = 'Записей: 0';
      return;
    }

    entries.slice().reverse().forEach((entry) => {
      const row = document.createElement('div');
      row.className = 'card stack';
      const level = String(entry.level || 'info').toUpperCase();
      const source = String(entry.source || 'ui');
      const details = String(entry.details || '').trim();

      const head = document.createElement('div');
      head.className = 'meta-muted';
      head.textContent = `[${formatTime(entry.ts)}] ${level} ${source}`;

      const message = document.createElement('div');
      message.textContent = entry.message || '';

      row.append(head, message);

      if (details) {
        const detailsNode = document.createElement('pre');
        detailsNode.className = 'meta-muted';
        detailsNode.style.whiteSpace = 'pre-wrap';
        detailsNode.style.margin = '0';
        detailsNode.textContent = details;
        row.append(detailsNode);
      }

      list.append(row);
    });

    status.className = 'status-line is-available';
    status.textContent = `Записей: ${entries.length}`;
  }

  controls.querySelector('[data-action="refresh"]').addEventListener('click', renderEntries);
  controls.querySelector('[data-action="copy-all"]').addEventListener('click', async () => {
    const entries = getAppLogEntries();
    if (!entries.length) {
      status.className = 'status-line';
      status.textContent = 'Лог пуст, копировать нечего.';
      return;
    }
    try {
      const text = entriesToClipboardText(entries);
      await copyTextToClipboard(text);
      status.className = 'status-line is-available';
      status.textContent = `Записей: ${entries.length} · скопировано`;
    } catch (e) {
      status.className = 'status-line';
      status.textContent = 'Ошибка копирования лога.';
    }
  });
  controls.querySelector('[data-action="clear"]').addEventListener('click', () => {
    clearAppLogEntries();
    renderEntries();
  });

  screen.append(controls, status, list);
  renderEntries();
  return screen;
}
