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
  controls.style.justifyContent = 'space-between';
  controls.style.gap = '8px';
  controls.innerHTML = `
    <button class="ghost-btn" type="button" data-action="refresh">Обновить</button>
    <button class="ghost-btn" type="button" data-action="clear">Очистить</button>
  `;

  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка лога...';

  const list = document.createElement('div');
  list.className = 'stack';

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
  controls.querySelector('[data-action="clear"]').addEventListener('click', () => {
    clearAppLogEntries();
    renderEntries();
  });

  screen.append(controls, status, list);
  renderEntries();
  return screen;
}
