import { renderHeader } from '../components/header.js?v=20260406221807';
import { directMessages } from '../mock-data.js?v=20260406221807';

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

  directMessages.forEach((item) => {
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
    row.addEventListener('click', () => navigate(`chat-view/${item.id}`));
    list.append(row);
  });

  screen.append(list);
  return screen;
}
