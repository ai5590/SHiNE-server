import { renderHeader } from '../components/header.js?v=20260327192619';
import { channels } from '../mock-data.js?v=20260327192619';

export const pageMeta = { id: 'channels-list', title: 'Каналы' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(renderHeader({ title: 'Каналы' }));

  const search = document.createElement('div');
  search.className = 'card';
  search.textContent = 'Найти канал';
  search.style.color = 'var(--text-muted)';

  const list = document.createElement('div');
  list.className = 'stack';

  channels.forEach((channel) => {
    const row = document.createElement('article');
    row.className = 'list-item';
    row.innerHTML = `
      <div class="avatar">${channel.initials}</div>
      <div>
        <strong># ${channel.name}</strong>
        <p class="meta-muted" style="margin-top:4px;">${channel.description}</p>
        <p class="meta-muted" style="margin-top:6px; color:#d8e3ff;">${channel.lastMessage}</p>
      </div>
      <div style="display:grid; justify-items:end; gap:6px;">
        <span class="badge alt" style="padding:4px 8px; font-size:10px;">Канал</span>
        <span class="meta-muted">${channel.time}</span>
        ${channel.unread ? `<span class="unread">${channel.unread}</span>` : '<span></span>'}
      </div>
    `;
    row.addEventListener('click', () => navigate(`channel-view/${channel.id}`));
    list.append(row);
  });

  screen.append(search, list);
  return screen;
}
