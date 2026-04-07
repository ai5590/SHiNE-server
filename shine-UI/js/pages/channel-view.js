import { renderHeader } from '../components/header.js?v=20260407105357';
import { channelPosts, channels } from '../mock-data.js?v=20260407105357';
import { addLocalChannelPost, authService, getLocalChannelPosts, state } from '../state.js?v=20260407105357';

export const pageMeta = { id: 'channel-view', title: 'Канал' };

function findMockChannel(channelId) {
  const channel = channels.find((c) => c.id === channelId) || channels[0];
  return {
    channel,
    posts: [
      ...(channelPosts[channel.id] || []).map((post) => ({ title: post.title, body: post.body })),
      ...getLocalChannelPosts(channelId),
    ],
    isOwnChannel: channel.ownerLogin === '@shine.alex',
  };
}

function mapApiMessageToPost(message) {
  return {
    title: `${message.authorLogin || 'author'} • #${message.messageRef?.blockNumber ?? '?'}`,
    body: message.text || '(пусто)',
  };
}

function renderPostCard(post) {
  const card = document.createElement('article');
  card.className = 'card stack';
  card.innerHTML = `<strong>${post.title}</strong><p class="meta-muted">${post.body}</p>`;
  return card;
}

function openAddMessageModal({ channelId, channelName, onSubmit }) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channel-message-modal">
      <div class="modal-card stack">
        <h3 style="font-size:18px;">Новое сообщение в канал</h3>
        <p class="meta-muted"># ${channelName}</p>
        <textarea id="channel-message-text" class="input" rows="6" maxlength="2000" placeholder="Введите текст сообщения"></textarea>
        <div class="meta-muted" id="channel-message-error" style="min-height:18px;"></div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
          <button class="secondary-btn" id="channel-message-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="channel-message-submit" type="button">Отправить</button>
        </div>
      </div>
    </div>
  `;

  const textEl = root.querySelector('#channel-message-text');
  const errorEl = root.querySelector('#channel-message-error');
  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#channel-message-cancel').addEventListener('click', close);
  root.querySelector('#channel-message-submit').addEventListener('click', () => {
    const body = textEl.value.trim();
    if (!body) {
      errorEl.textContent = 'Введите текст сообщения.';
      return;
    }

    onSubmit({
      title: `${state.session.login || 'Вы'} • сейчас`,
      body,
    });
    close();
  });

  if (textEl) textEl.focus();
}

function renderBody(screen, navigate, channelId, channelData) {
  const head = document.createElement('div');
  head.className = 'card';
  head.innerHTML = `
    <strong># ${channelData.channel.name}</strong>
    <p class="meta-muted" style="margin-top:4px;">${channelData.channel.description}</p>
    <p class="meta-muted" style="margin-top:8px;">Владелец: ${channelData.channel.ownerName}</p>
  `;

  const actionButton = document.createElement('button');
  actionButton.className = channelData.isOwnChannel ? 'primary-btn' : 'secondary-btn';
  actionButton.textContent = channelData.isOwnChannel ? 'Добавить сообщение в канал' : 'Отписаться от канала';

  const feed = document.createElement('div');
  feed.className = 'stack';

  channelData.posts.forEach((post) => {
    feed.append(renderPostCard(post));
  });

  if (channelData.isOwnChannel) {
    actionButton.addEventListener('click', () => {
      openAddMessageModal({
        channelId,
        channelName: channelData.channel.name,
        onSubmit: (post) => {
          addLocalChannelPost(channelId, post);
          channelData.posts.push(post);
          feed.append(renderPostCard(post));
        },
      });
    });
  }

  const backButton = document.createElement('button');
  backButton.className = 'secondary-btn';
  backButton.textContent = 'Назад к списку';
  backButton.addEventListener('click', () => navigate('channels-list'));

  screen.append(head, actionButton, feed, backButton);
}

async function loadFromApi(channelId) {
  const summary = state.channelsIndex[channelId];
  if (!summary) return null;

  const selector = {
    ownerBlockchainName: summary.channel?.ownerBlockchainName,
    channelRootBlockNumber: summary.channel?.channelRoot?.blockNumber,
    channelRootBlockHash: summary.channel?.channelRoot?.blockHash,
  };

  if (!selector.ownerBlockchainName || selector.channelRootBlockNumber == null) return null;

  const payload = await authService.getChannelMessages(selector, 200, 'asc');
  const posts = [
    ...(payload.messages || []).map(mapApiMessageToPost),
    ...getLocalChannelPosts(channelId),
  ];

  return {
    channel: {
      name: payload.channel?.channelName || summary.channel?.channelName || 'unknown',
      description: `bch=${payload.channel?.ownerBlockchainName || selector.ownerBlockchainName}`,
      ownerName: payload.channel?.ownerLogin || summary.channel?.ownerLogin || 'unknown',
    },
    posts,
    isOwnChannel: (payload.channel?.ownerLogin || '').toLowerCase() === (state.session.login || '').toLowerCase(),
  };
}

export function render({ navigate, route }) {
  const channelId = route.params.channelId || 'ch1';

  const screen = document.createElement('section');
  screen.className = 'stack';

  const headerTitle = state.channelsIndex[channelId]?.channel?.channelName
    ? `Канал: ${state.channelsIndex[channelId].channel.channelName}`
    : `Канал: ${(channels.find((c) => c.id === channelId) || channels[0]).name}`;

  screen.append(
    renderHeader({
      title: headerTitle,
      leftAction: { label: '←', onClick: () => navigate('channels-list') },
    })
  );

  const loading = document.createElement('div');
  loading.className = 'card meta-muted';
  loading.textContent = 'Загрузка канала...';
  screen.append(loading);

  (async () => {
    try {
      const apiData = await loadFromApi(channelId);
      loading.remove();
      if (apiData) {
        renderBody(screen, navigate, channelId, apiData);
        return;
      }
    } catch {
      // fallback to mock below
    }

    loading.remove();
    renderBody(screen, navigate, channelId, findMockChannel(channelId));
  })();

  return screen;
}
