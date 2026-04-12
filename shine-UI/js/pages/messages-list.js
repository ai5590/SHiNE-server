import { renderHeader } from '../components/header.js';
import { directMessages } from '../mock-data.js';
import { getChatMessages } from '../state.js';
import { loadCurrentRelations } from '../services/user-connections.js';

export const pageMeta = { id: 'messages-list', title: 'Личные сообщения' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Личные сообщения',
      rightActions: [{ label: '+', onClick: () => navigate('contact-search-view') }],
    }),
  );

  const list = document.createElement('div');
  list.className = 'stack';
  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка списка сообщений...';

  function renderRow(item) {
    const row = document.createElement('article');
    row.className = 'list-item';
    row.innerHTML = `
      <div class="avatar">${item.initials}</div>
      <div>
        <div class="row" style="justify-content:flex-start; gap:8px;">
          <strong>${item.name}</strong>
        </div>
        <p class="meta-muted" style="margin-top:4px;">${item.lastMessage}</p>
      </div>
      <div style="display:grid; justify-items:end; gap:6px;">
        <span class="meta-muted">${item.time}</span>
        ${item.unread ? `<span class="unread">${item.unread}</span>` : '<span></span>'}
      </div>
    `;
    row.addEventListener('click', () => navigate(`chat-view/${encodeURIComponent(item.id)}`));
    return row;
  }

  async function loadList() {
    try {
      const relations = await loadCurrentRelations();
      const follows = relations.outFollows || [];
      list.innerHTML = '';

      if (!follows.length) {
        const empty = document.createElement('div');
        empty.className = 'card meta-muted';
        empty.textContent = 'Ваш список контактов пока пуст';
        list.append(empty);
        status.className = 'status-line is-available';
        status.textContent = 'Нет подписок на пользователей.';
        return;
      }

      const rows = follows.map((login) => {
        const preview = directMessages.find((item) => item.id.toLowerCase() === login.toLowerCase());
        const chat = getChatMessages(login);
        const lastChat = chat[chat.length - 1];
        return {
          id: login,
          initials: (login[0] || '?').toUpperCase(),
          name: preview?.name || login,
          lastMessage: lastChat?.text || preview?.lastMessage || 'Диалог пока пуст.',
          time: preview?.time || '—',
          unread: Number(preview?.unread || 0),
        };
      });

      rows.forEach((item) => list.append(renderRow(item)));
      status.className = 'status-line is-available';
      status.textContent = `Загружено диалогов: ${rows.length}`;
    } catch (error) {
      list.innerHTML = '';
      const fail = document.createElement('div');
      fail.className = 'card meta-muted';
      fail.textContent = `Не удалось загрузить сообщения: ${error.message || 'unknown'}`;
      list.append(fail);
      status.className = 'status-line is-unavailable';
      status.textContent = 'Список недоступен.';
    }
  }

  screen.append(status, list);
  loadList();
  return screen;
}
