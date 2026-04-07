import { resolveToolbarActive } from '../router.js?v=20260407105357';

const ITEMS = [
  { pageId: 'messages-list', label: 'Личные сообщения', icon: '💬' },
  { pageId: 'channels-list', label: 'Каналы', icon: '📢' },
  { pageId: 'network-view', label: 'Связи', icon: '🕸' },
  { pageId: 'notifications-view', label: 'Уведомления', icon: '🔔' },
  { pageId: 'profile-view', label: 'Профиль', icon: '👤' },
];

export function renderToolbar(currentPageId, navigate) {
  const root = document.createElement('nav');
  root.className = 'toolbar';
  const active = resolveToolbarActive(currentPageId);

  ITEMS.forEach((item) => {
    const btn = document.createElement('button');
    btn.className = `toolbar-btn${item.pageId === active ? ' active' : ''}`;
    btn.innerHTML = `<span>${item.icon}</span><span>${item.label}</span>`;
    btn.addEventListener('click', () => navigate(item.pageId));
    root.append(btn);
  });

  return root;
}
