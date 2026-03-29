import { renderHeader } from '../components/header.js?v=20260327192619';
import { notifications } from '../mock-data.js?v=20260327192619';
import { state } from '../state.js?v=20260327192619';

export const pageMeta = { id: 'notifications-view', title: 'Уведомления' };

function renderList(container) {
  const active = state.notificationsTab;
  const items = notifications[active] || [];
  container.innerHTML = '';

  items.forEach((item) => {
    const card = document.createElement('article');
    card.className = 'card stack';
    card.innerHTML = `<strong>${item.title}</strong><p class="meta-muted">${item.text}</p><p class="meta-muted">${item.time}</p>`;
    container.append(card);
  });
}

export function render() {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(renderHeader({ title: 'Уведомления' }));

  const tabs = document.createElement('div');
  tabs.className = 'tabs';
  tabs.innerHTML = `
    <button class="tab-btn ${state.notificationsTab === 'replies' ? 'active' : ''}" data-tab="replies">Ответы</button>
    <button class="tab-btn ${state.notificationsTab === 'events' ? 'active' : ''}" data-tab="events">События</button>
  `;

  const list = document.createElement('div');
  list.className = 'stack';
  renderList(list);

  tabs.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.notificationsTab = btn.dataset.tab;
      tabs.querySelectorAll('.tab-btn').forEach((node) => node.classList.remove('active'));
      btn.classList.add('active');
      renderList(list);
    });
  });

  screen.append(tabs, list);
  return screen;
}
