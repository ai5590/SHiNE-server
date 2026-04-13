import { renderHeader } from '../components/header.js';
import { channelPosts, channels } from '../mock-data.js';
import {
  addLocalChannelPost,
  authService,
  getLocalChannelPosts,
  getMessageReactionState,
  setMessageReactionState,
  state,
} from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';

export const pageMeta = { id: 'channel-view', title: 'Канал' };

const ZERO64 = '0'.repeat(64);
const pendingReactionActions = new Set();

function isChannelsDemoMode() {
  try {
    const qs = new URLSearchParams(window.location.search);
    if (qs.get('channelsDemo') === '1') return true;
    return localStorage.getItem('shine-channels-demo') === '1';
  } catch {
    return false;
  }
}

function encodeRoutePart(value = '') {
  return encodeURIComponent(String(value));
}

function normalizeRouteHash(hash) {
  const normalized = String(hash || '').trim().toLowerCase();
  return normalized || '0';
}

function normalizeMessageHash(hash) {
  const normalized = String(hash || '').trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(normalized)) return '';
  if (/^0+$/.test(normalized)) return '';
  return normalized;
}

function toSafeInt(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function makeReactionActionKey(messageRef) {
  const login = String(state.session.login || '').trim().toLowerCase();
  const blockchainName = String(messageRef?.blockchainName || '').trim();
  const blockNumber = Number(messageRef?.blockNumber);
  const blockHash = normalizeMessageHash(messageRef?.blockHash);
  if (!login || !blockchainName || !Number.isFinite(blockNumber) || blockNumber < 0 || !blockHash) return '';
  return `${login}|${blockchainName}|${blockNumber}|${blockHash}`;
}

function buildSelectorFromRoute(route, channelId) {
  const params = route?.params || {};

  if (params.ownerBlockchainName) {
    const rootBlockNumber = toSafeInt(params.channelRootBlockNumber);
    if (rootBlockNumber != null) {
      return {
        ownerBlockchainName: String(params.ownerBlockchainName),
        channelRootBlockNumber: rootBlockNumber,
        channelRootBlockHash: normalizeRouteHash(params.channelRootBlockHash),
      };
    }
  }

  const summary = channelId ? state.channelsIndex[channelId] : null;
  if (!summary) return null;
  return {
    ownerBlockchainName: summary.channel?.ownerBlockchainName,
    channelRootBlockNumber: summary.channel?.channelRoot?.blockNumber,
    channelRootBlockHash: normalizeRouteHash(summary.channel?.channelRoot?.blockHash),
  };
}

function localPostsKey(selector, channelId) {
  if (selector?.ownerBlockchainName && selector?.channelRootBlockNumber != null) {
    return `${selector.ownerBlockchainName}:${selector.channelRootBlockNumber}`;
  }
  return channelId || '';
}

function buildThreadRoute(messageRef, selector) {
  if (!messageRef || !selector) return '';
  return [
    'channel-thread-view',
    encodeRoutePart(messageRef.blockchainName),
    messageRef.blockNumber,
    normalizeRouteHash(messageRef.blockHash),
    encodeRoutePart(selector.ownerBlockchainName),
    selector.channelRootBlockNumber,
    normalizeRouteHash(selector.channelRootBlockHash),
  ].join('/');
}

function firstNonEmptyText(...candidates) {
  for (const candidate of candidates) {
    if (typeof candidate !== 'string') continue;
    const trimmed = candidate.trim();
    if (trimmed.length > 0) return candidate;
  }
  return '';
}

function latestVersionText(versions) {
  if (!Array.isArray(versions)) return '';
  for (let i = versions.length - 1; i >= 0; i -= 1) {
    const version = versions[i];
    const value = firstNonEmptyText(version?.text, version?.message, version?.body);
    if (value) return value;
  }
  return '';
}

function resolveMessageText(message) {
  return firstNonEmptyText(
    message?.text,
    message?.message,
    message?.body,
    latestVersionText(message?.versions),
  );
}

function mapApiMessageToPost(message, selector) {
  const blockNumber = toSafeInt(message?.messageRef?.blockNumber);
  const blockHash = normalizeMessageHash(message?.messageRef?.blockHash);
  const messageBch = String(message?.authorBlockchainName || selector?.ownerBlockchainName || '').trim();
  const hasRef = !!(messageBch && blockNumber != null && blockHash);
  const resolvedText = resolveMessageText(message);
  const messageRef = hasRef
    ? {
        blockchainName: messageBch,
        blockNumber,
        blockHash,
      }
    : null;

  if (messageRef) {
    setMessageReactionState(messageRef, message?.likedByMe === true ? 'liked' : 'unliked');
  }

  return {
    title: `${message?.authorLogin || 'автор'} - #${blockNumber ?? '?'}`,
    body: resolvedText || '(пусто)',
    likesCount: Number(message?.likesCount || 0),
    repliesCount: Number(message?.repliesCount || 0),
    messageRef,
    reactionState: messageRef ? getMessageReactionState(messageRef) : '',
  };
}

function findMockChannel(channelId) {
  const fallback = channels[0] || {
    id: 'ch0',
    name: 'Неизвестный канал',
    description: 'Описание отсутствует',
    ownerName: 'неизвестно',
    ownerLogin: '',
    displayName: 'неизвестно/Неизвестный канал',
  };
  const channel = channels.find((c) => c.id === channelId) || fallback;
  return {
    channel,
    posts: [
      ...(channelPosts[channel.id] || []).map((post) => ({ title: post.title, body: post.body })),
      ...getLocalChannelPosts(channelId),
    ],
    isOwnChannel: channel.ownerLogin === '@shine.alex',
    selector: null,
    localKey: channelId,
  };
}

function openReplyModal({ onSubmit }) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="reply-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">Ответ</h3>
        <textarea id="reply-text" class="input" rows="5" maxlength="2000" placeholder="Текст ответа"></textarea>
        <div class="meta-muted inline-error" id="reply-error"></div>
        <div class="form-actions-grid">
          <button class="secondary-btn" id="reply-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="reply-submit" type="button">Отправить</button>
        </div>
      </div>
    </div>
  `;

  const textEl = root.querySelector('#reply-text');
  const errorEl = root.querySelector('#reply-error');
  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#reply-cancel').addEventListener('click', close);
  root.querySelector('#reply-submit').addEventListener('click', async () => {
    const text = String(textEl?.value || '').trim();
    if (!text) {
      errorEl.textContent = 'Введите текст ответа.';
      return;
    }

    try {
      await onSubmit(text);
      close();
    } catch (error) {
      errorEl.textContent = toUserMessage(error, 'Не удалось отправить ответ.');
    }
  });

  if (textEl) textEl.focus();
}

function openAddMessageModal({ channelName, onSubmit }) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channel-message-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">Новое сообщение в канале</h3>
        <p class="meta-muted"># ${channelName}</p>
        <textarea id="channel-message-text" class="input" rows="6" maxlength="2000" placeholder="Текст сообщения"></textarea>
        <div class="meta-muted inline-error" id="channel-message-error"></div>
        <div class="form-actions-grid">
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
  root.querySelector('#channel-message-submit').addEventListener('click', async () => {
    const body = String(textEl?.value || '').trim();
    if (!body) {
      errorEl.textContent = 'Введите текст сообщения.';
      return;
    }

    try {
      await onSubmit({
        title: `${state.session.login || 'вы'} - сейчас`,
        body,
      });
      close();
    } catch (error) {
      errorEl.textContent = toUserMessage(error, 'Не удалось отправить сообщение.');
    }
  });

  if (textEl) textEl.focus();
}

function renderPostCard(post, { navigate, selector, onToggleLike, onReply }) {
  const card = document.createElement('article');
  card.className = 'card stack channel-message-card';

  const stats = document.createElement('p');
  stats.className = 'channel-message-stats';
  stats.textContent = `Лайки: ${post.likesCount || 0}, ответы: ${post.repliesCount || 0}`;

  card.innerHTML = `<strong class="channel-message-title">${post.title}</strong><p class="channel-message-body">${post.body}</p>`;
  card.append(stats);

  if (!post.messageRef || !selector) return card;

  const actionKey = makeReactionActionKey(post.messageRef);
  const isPending = actionKey ? pendingReactionActions.has(actionKey) : false;

  const actions = document.createElement('div');
  actions.className = 'channel-message-actions';

  const likeButton = document.createElement('button');
  likeButton.type = 'button';
  likeButton.className = 'secondary-btn channel-action-like';
  const isLiked = post.reactionState === 'liked';
  if (isLiked) likeButton.classList.add('is-liked');
  likeButton.textContent = isPending ? 'Выполняется...' : (isLiked ? 'Убрать лайк' : 'Лайк');
  likeButton.disabled = isPending;
  likeButton.addEventListener('click', async () => {
    if (isPending) return;
    await onToggleLike(post.messageRef, isLiked ? 'unlike' : 'like');
  });

  const replyButton = document.createElement('button');
  replyButton.type = 'button';
  replyButton.className = 'secondary-btn channel-action-reply';
  replyButton.textContent = 'Ответить';
  replyButton.addEventListener('click', () => {
    openReplyModal({
      onSubmit: async (text) => onReply(post.messageRef, text),
    });
  });

  const openThreadButton = document.createElement('button');
  openThreadButton.type = 'button';
  openThreadButton.className = 'secondary-btn channel-action-thread';
  openThreadButton.textContent = 'Открыть тред';
  openThreadButton.addEventListener('click', () => {
    const route = buildThreadRoute(post.messageRef, selector);
    if (route) navigate(route);
  });

  actions.append(likeButton, replyButton, openThreadButton);
  card.append(actions);
  return card;
}

function renderBody(screen, navigate, channelData, handlers) {
  const head = document.createElement('div');
  head.className = 'card channel-head-card';
  head.innerHTML = `
    <strong class="channel-head-title">${channelData.channel.displayName || channelData.channel.name}</strong>
    <p class="channel-head-meta">${channelData.channel.description}</p>
    <p class="channel-head-meta">Владелец: ${channelData.channel.ownerName}</p>
    <p class="channel-note">Состояние лайка обновляется после подтверждённого reread с сервера.</p>
  `;

  const actionButton = document.createElement('button');
  actionButton.className = channelData.isOwnChannel
    ? 'primary-btn channel-main-action'
    : 'destructive-btn channel-main-action';
  actionButton.textContent = channelData.isOwnChannel ? 'Добавить сообщение' : 'Отписаться от канала';
  let followLimit = null;

  const feed = document.createElement('div');
  feed.className = 'stack channel-feed';

  channelData.posts.forEach((post) => {
    feed.append(renderPostCard(post, {
      navigate,
      selector: channelData.selector,
      onToggleLike: handlers.onToggleLike,
      onReply: handlers.onReply,
    }));
  });

  if (channelData.isOwnChannel) {
    actionButton.addEventListener('click', () => {
      openAddMessageModal({
        channelName: channelData.channel.name,
        onSubmit: async (post) => handlers.onAddPost(post),
      });
    });
  } else {
    followLimit = document.createElement('p');
    followLimit.className = 'channel-note';
    followLimit.textContent = 'Отписка удаляет только эту подписку на канал.';
    actionButton.addEventListener('click', handlers.onUnfollowChannel);
  }

  const backButton = document.createElement('button');
  backButton.className = 'secondary-btn channel-back-btn';
  backButton.textContent = 'Назад к каналам';
  backButton.addEventListener('click', () => navigate('channels-list'));

  if (followLimit) {
    screen.append(head, followLimit, actionButton, feed, backButton);
    return;
  }
  screen.append(head, actionButton, feed, backButton);
}

async function loadFromApi(route, channelId) {
  const selector = buildSelectorFromRoute(route, channelId);
  if (!selector?.ownerBlockchainName || selector.channelRootBlockNumber == null) {
    throw new Error('Не удалось определить канал из адреса страницы.');
  }

  const payload = await authService.getChannelMessages(selector, 200, 'asc', state.session.login);
  const localKey = localPostsKey(selector, channelId);
  const posts = [
    ...(payload.messages || []).map((message) => mapApiMessageToPost(message, selector)),
    ...getLocalChannelPosts(localKey),
  ];

  return {
    channel: {
      name: payload.channel?.channelName || 'неизвестный канал',
      displayName: `${payload.channel?.ownerLogin || 'неизвестно'}/${payload.channel?.channelName || 'неизвестный канал'}`,
      description: `bch=${payload.channel?.ownerBlockchainName || selector.ownerBlockchainName}`,
      ownerName: payload.channel?.ownerLogin || 'неизвестно',
    },
    posts,
    isOwnChannel: (payload.channel?.ownerLogin || '').toLowerCase() === (state.session.login || '').toLowerCase(),
    selector,
    localKey,
  };
}

function renderLoadError(screen, navigate, message, onRetry) {
  const card = document.createElement('div');
  card.className = 'card stack channels-status';
  card.innerHTML = `
    <strong>Не удалось загрузить канал</strong>
    <p class="meta-muted">${message || 'Проверьте подключение к серверу и повторите попытку.'}</p>
  `;

  const retry = document.createElement('button');
  retry.type = 'button';
  retry.className = 'primary-btn';
  retry.textContent = 'Повторить';
  retry.addEventListener('click', onRetry);

  const back = document.createElement('button');
  back.type = 'button';
  back.className = 'secondary-btn';
  back.textContent = 'Назад к каналам';
  back.addEventListener('click', () => navigate('channels-list'));

  card.append(retry, back);
  screen.append(card);
}

function renderDemoFallback(screen, navigate, channelId, error) {
  const info = document.createElement('div');
  info.className = 'card stack';
  info.innerHTML = `
    <strong>Включен демо-режим</strong>
    <p class="meta-muted">Данные канала с сервера недоступны. Показан мок-канал, потому что включен channelsDemo.</p>
    <p class="meta-muted">${toUserMessage(error, 'Ошибка API/WS')}</p>
  `;
  screen.append(info);

  renderBody(screen, navigate, findMockChannel(channelId || 'ch1'), {
    onToggleLike: async () => {},
    onReply: async () => {},
    onAddPost: async (post) => {
      addLocalChannelPost(channelId || 'ch1', post);
    },
    onUnfollowChannel: () => {},
  });
}

export function render({ navigate, route }) {
  const channelId = route.params.channelId || '';
  const routeSelector = buildSelectorFromRoute(route, channelId);

  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--channel';

  const fallbackName = channels.find((c) => c.id === channelId)?.name || 'Канал';
  const titleFromIndex = state.channelsIndex[channelId]?.channel?.channelName;
  const ownerFromIndex = state.channelsIndex[channelId]?.channel?.ownerLogin;
  const titleFromIndexDisplay = (ownerFromIndex && titleFromIndex) ? `${ownerFromIndex}/${titleFromIndex}` : titleFromIndex;
  const titleFromRoute = route.params.ownerBlockchainName ? String(route.params.ownerBlockchainName) : '';
  const headerTitle = `Канал: ${titleFromIndexDisplay || titleFromRoute || fallbackName}`;

  const userIndicator = document.createElement('div');
  userIndicator.className = 'card channels-user-chip';
  userIndicator.textContent = `Вы вошли как @${state.session.login || 'неизвестно'}`;

  const statusBox = document.createElement('div');
  statusBox.className = 'card status-line is-unavailable channels-status';
  statusBox.style.display = 'none';

  const rerender = () => {
    const current = document.querySelector('section.channels-screen--channel');
    if (!current) return;
    const next = render({ navigate, route });
    current.replaceWith(next);
  };

  const showStatus = (message) => {
    if (!message) {
      statusBox.style.display = 'none';
      statusBox.textContent = '';
      return;
    }
    statusBox.textContent = message;
    statusBox.style.display = '';
  };

  const requireSigningSession = () => {
    const login = state.session.login;
    const storagePwd = state.session.storagePwdInMemory;
    if (!login || !storagePwd) {
      throw new Error('Сессия недействительна. Выполните вход заново.');
    }
    return { login, storagePwd };
  };

  const rereadChannel = async () => {
    await loadFromApi(route, channelId);
  };

  const onToggleLike = async (messageRef, action) => {
    const actionKey = makeReactionActionKey(messageRef);
    if (!actionKey) {
      throw new Error('Некорректная ссылка на сообщение для реакции.');
    }
    if (pendingReactionActions.has(actionKey)) return;

    pendingReactionActions.add(actionKey);
    rerender();
    try {
      const { login, storagePwd } = requireSigningSession();
      if (action === 'unlike') {
        await authService.addBlockUnlike({ login, storagePwd, message: messageRef });
      } else {
        await authService.addBlockLike({ login, storagePwd, message: messageRef });
      }
      await rereadChannel();
      showStatus('');
    } finally {
      pendingReactionActions.delete(actionKey);
      rerender();
    }
  };

  const onReply = async (messageRef, text) => {
    const { login, storagePwd } = requireSigningSession();
    await authService.addBlockReply({ login, storagePwd, message: messageRef, text });
    await rereadChannel();
    rerender();
  };

  const onAddPost = async (post) => {
    const { login, storagePwd } = requireSigningSession();
    if (!routeSelector?.ownerBlockchainName || routeSelector.channelRootBlockNumber == null) {
      throw new Error('Идентификатор канала не готов.');
    }

    await authService.addBlockTextPost({
      login,
      storagePwd,
      channel: routeSelector,
      text: post?.body || '',
    });
    await rereadChannel();
    rerender();
  };

  screen.append(
    renderHeader({
      title: headerTitle,
      leftAction: { label: '<', onClick: () => navigate('channels-list') },
    })
  );
  screen.append(userIndicator, statusBox);

  const loading = document.createElement('div');
  loading.className = 'card meta-muted';
  loading.textContent = 'Загрузка канала...';
  screen.append(loading);

  (async () => {
    try {
      const apiData = await loadFromApi(route, channelId);
      loading.remove();
      renderBody(screen, navigate, apiData, {
        onToggleLike: async (messageRef, action) => {
          try {
            await onToggleLike(messageRef, action);
          } catch (error) {
            showStatus(toUserMessage(error, action === 'unlike' ? 'Не удалось убрать лайк.' : 'Не удалось поставить лайк.'));
          }
        },
        onReply: async (messageRef, text) => {
          try {
            await onReply(messageRef, text);
            showStatus('');
          } catch (error) {
            throw new Error(toUserMessage(error, 'Не удалось отправить ответ.'));
          }
        },
        onAddPost: async (post) => {
          try {
            await onAddPost(post);
            showStatus('');
          } catch (error) {
            throw new Error(toUserMessage(error, 'Не удалось добавить сообщение.'));
          }
        },
        onUnfollowChannel: async () => {
          try {
            const { login, storagePwd } = requireSigningSession();
            if (!apiData.selector) throw new Error('Не удалось определить канал для отписки.');

            await authService.addBlockFollowChannel({
              login,
              storagePwd,
              targetBlockchainName: apiData.selector.ownerBlockchainName,
              targetBlockNumber: apiData.selector.channelRootBlockNumber,
              targetBlockHashHex: apiData.selector.channelRootBlockHash,
              unfollow: true,
            });

            navigate('channels-list');
          } catch (error) {
            showStatus(toUserMessage(error, 'Не удалось отписаться от канала.'));
          }
        },
      });
    } catch (error) {
      loading.remove();
      if (isChannelsDemoMode()) {
        renderDemoFallback(screen, navigate, channelId, error);
        return;
      }
      renderLoadError(screen, navigate, toUserMessage(error, 'Не удалось загрузить канал.'), rerender);
    }
  })();

  return screen;
}
