import { renderHeader } from '../components/header.js?v=20260327192619';
import { channelPosts, channels } from '../mock-data.js?v=20260327192619';

export const pageMeta = { id: 'channel-view', title: 'Канал' };

export function render({ navigate, route }) {
  const channelId = route.params.channelId || 'ch1';
  const channel = channels.find((c) => c.id === channelId) || channels[0];
  const posts = channelPosts[channelId] || [];

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: `Канал: ${channel.name}`,
      leftAction: { label: '←', onClick: () => navigate('channels-list') },
    })
  );

  const head = document.createElement('div');
  head.className = 'card';
  head.innerHTML = `
    <strong># ${channel.name}</strong>
    <p class="meta-muted" style="margin-top:4px;">${channel.description}</p>
    <p class="meta-muted" style="margin-top:8px;">Публичный канал, режим только чтение</p>
  `;

  const feed = document.createElement('div');
  feed.className = 'stack';

  posts.forEach((post) => {
    const card = document.createElement('article');
    card.className = 'card stack';
    card.innerHTML = `<strong>${post.title}</strong><p class="meta-muted">${post.body}</p>`;
    feed.append(card);
  });

  screen.append(head, feed);
  return screen;
}
