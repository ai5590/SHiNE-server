import { renderHeader } from '../components/header.js';
import {
  authService,
  getMessageReactionState,
  setMessageReactionState,
  state,
} from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';
import {
  animatePress,
  createSkeletonCard,
  showToast,
  softHaptic,
} from '../services/channels-ux.js';

export const pageMeta = { id: 'channel-view', title: 'Канал' };

const pendingReactionActions = new Set();
const pendingScrollByRoute = new Map();

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

function messageRefKey(messageRef) {
  const blockchainName = String(messageRef?.blockchainName || '').trim();
  const blockNumber = Number(messageRef?.blockNumber);
  const blockHash = normalizeMessageHash(messageRef?.blockHash);
  if (!blockchainName || !Number.isFinite(blockNumber) || blockNumber < 0 || !blockHash) return '';
  return `${blockchainName}:${blockNumber}:${blockHash}`;
}

function channelDescriptionParamKey(selector) {
  const owner = String(selector?.ownerBlockchainName || '').trim();
  const rootNo = Number(selector?.channelRootBlockNumber);
  const rootHash = normalizeRouteHash(selector?.channelRootBlockHash);
  if (!owner || !Number.isFinite(rootNo)) return '';
  return `channel_desc:${owner}:${rootNo}:${rootHash}`;
}

function parseDescriptionOverride(payload) {
  if (!payload || typeof payload !== 'object') {
    return { hasOverride: false, description: '' };
  }

  const rawValue = String(payload?.value ?? payload?.param_value ?? '').trim();
  if (!rawValue && !Number(payload?.time_ms || payload?.timeMs || 0)) {
    return { hasOverride: false, description: '' };
  }

  if (!rawValue) {
    return { hasOverride: true, description: '' };
  }

  try {
    const parsed = JSON.parse(rawValue);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const value = typeof parsed.v === 'string' ? parsed.v : '';
      return { hasOverride: true, description: value.trim() };
    }
  } catch {
    // legacy raw string value
  }

  return { hasOverride: true, description: rawValue };
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

function openAboutChannelModal(channel) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="about-channel-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">О канале</h3>
        <p><strong>${channel.displayName || channel.name}</strong></p>
        <p class="meta-muted">${channel.description || 'Описание не задано.'}</p>
        <button class="secondary-btn" id="about-channel-close" type="button">Закрыть</button>
      </div>
    </div>
  `;

  root.querySelector('#about-channel-close')?.addEventListener('click', () => {
    root.innerHTML = '';
  });
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
  const submitEl = root.querySelector('#reply-submit');
  let inFlight = false;

  const setBusy = (busy) => {
    inFlight = !!busy;
    submitEl.disabled = inFlight;
    if (textEl) textEl.disabled = inFlight;
    submitEl.textContent = inFlight ? 'Отправляем...' : 'Отправить';
  };

  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#reply-cancel')?.addEventListener('click', close);
  submitEl?.addEventListener('click', async () => {
    if (inFlight) return;

    const text = String(textEl?.value || '').trim();
    if (!text) {
      errorEl.textContent = 'Введите текст ответа.';
      return;
    }

    setBusy(true);
    errorEl.textContent = '';

    try {
      await onSubmit(text);
      close();
    } catch (error) {
      setBusy(false);
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
        <p class="meta-muted">${channelName}</p>
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
  const submitEl = root.querySelector('#channel-message-submit');
  let inFlight = false;

  const setBusy = (busy) => {
    inFlight = !!busy;
    submitEl.disabled = inFlight;
    if (textEl) textEl.disabled = inFlight;
    submitEl.textContent = inFlight ? 'Отправляем...' : 'Отправить';
  };

  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#channel-message-cancel')?.addEventListener('click', close);
  submitEl?.addEventListener('click', async () => {
    if (inFlight) return;

    const body = String(textEl?.value || '').trim();
    if (!body) {
      errorEl.textContent = 'Введите текст сообщения.';
      return;
    }

    setBusy(true);
    errorEl.textContent = '';

    try {
      await onSubmit(body);
      close();
    } catch (error) {
      setBusy(false);
      errorEl.textContent = toUserMessage(error, 'Не удалось отправить сообщение.');
    }
  });

  if (textEl) textEl.focus();
}

function openEditDescriptionModal({ initialValue = '', onSubmit }) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channel-edit-description-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">Описание канала</h3>
        <textarea id="channel-description-text" class="input" rows="5" maxlength="400" placeholder="Коротко о канале, до 200 байт UTF-8"></textarea>
        <div class="meta-muted" id="channel-description-counter">0 / 200 байт</div>
        <div class="meta-muted inline-error" id="channel-description-error"></div>
        <div class="form-actions-grid">
          <button class="secondary-btn" id="channel-description-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="channel-description-submit" type="button">Сохранить</button>
        </div>
      </div>
    </div>
  `;

  const textEl = root.querySelector('#channel-description-text');
  const counterEl = root.querySelector('#channel-description-counter');
  const errorEl = root.querySelector('#channel-description-error');
  const submitEl = root.querySelector('#channel-description-submit');
  const cancelEl = root.querySelector('#channel-description-cancel');

  let inFlight = false;

  const compute = () => {
    const value = String(textEl?.value || '').replace(/\s+/g, ' ').trim();
    const bytes = new TextEncoder().encode(value).length;
    const ok = bytes <= 200;
    return {
      value,
      bytes,
      ok,
      error: ok ? '' : 'Описание слишком длинное: максимум 200 байт UTF-8.',
    };
  };

  const setBusy = (busy) => {
    inFlight = !!busy;
    submitEl.disabled = inFlight;
    cancelEl.disabled = inFlight;
    if (textEl) textEl.disabled = inFlight;
    submitEl.textContent = inFlight ? 'Сохраняем...' : 'Сохранить';
  };

  const close = () => {
    root.innerHTML = '';
  };

  const updateValidation = () => {
    const check = compute();
    counterEl.textContent = `${check.bytes} / 200 байт`;
    errorEl.textContent = check.error;
    submitEl.disabled = inFlight || !check.ok;
    return check;
  };

  cancelEl?.addEventListener('click', close);
  textEl?.addEventListener('input', updateValidation);
  submitEl?.addEventListener('click', async () => {
    if (inFlight) return;
    const check = updateValidation();
    if (!check.ok) return;

    setBusy(true);
    errorEl.textContent = '';
    try {
      await onSubmit(check.value);
      close();
    } catch (error) {
      setBusy(false);
      errorEl.textContent = toUserMessage(error, 'Не удалось сохранить описание.');
    }
  });

  if (textEl) {
    textEl.value = String(initialValue || '');
    textEl.focus();
  }
  updateValidation();
}

function mapApiMessageToPost(message, selector, localNumber) {
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
    localNumber,
    authorLogin: message?.authorLogin || 'автор',
    body: resolvedText || '(пусто)',
    likesCount: Number(message?.likesCount || 0),
    repliesCount: Number(message?.repliesCount || 0),
    messageRef,
    reactionState: messageRef ? getMessageReactionState(messageRef) : '',
  };
}

async function loadFromApi(route, channelId) {
  const selector = buildSelectorFromRoute(route, channelId);
  if (!selector?.ownerBlockchainName || selector.channelRootBlockNumber == null) {
    throw new Error('Не удалось определить канал из адреса страницы.');
  }

  const payload = await authService.getChannelMessages(selector, 200, 'asc', state.session.login);
  const messages = Array.isArray(payload.messages) ? payload.messages : [];
  const posts = messages.map((message, index) => mapApiMessageToPost(message, selector, index + 1));
  const ownerLogin = String(payload.channel?.ownerLogin || '').trim();

  const readDescription = async () => {
    const sourceDescription = String(payload.channel?.channelDescription || '').trim();
    const paramKey = channelDescriptionParamKey(selector);
    if (!ownerLogin || !paramKey) return sourceDescription;

    try {
      const paramPayload = await authService.getUserParam(ownerLogin, paramKey);
      const override = parseDescriptionOverride(paramPayload);
      return override.hasOverride ? override.description : sourceDescription;
    } catch {
      return sourceDescription;
    }
  };

  const resolvedDescription = await readDescription();

  return {
    channel: {
      name: payload.channel?.channelName || 'неизвестный канал',
      displayName: `${ownerLogin || 'неизвестно'}/${payload.channel?.channelName || 'неизвестный канал'}`,
      description: resolvedDescription,
      ownerName: ownerLogin || 'неизвестно',
    },
    posts,
    isOwnChannel: ownerLogin.toLowerCase() === (state.session.login || '').toLowerCase(),
    selector,
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

function renderDemoFallback(screen, navigate, error) {
  const info = document.createElement('div');
  info.className = 'card stack';
  info.innerHTML = `
    <strong>Включен демо-режим</strong>
    <p class="meta-muted">Данные канала с сервера недоступны. Показан демо-контент.</p>
    <p class="meta-muted">${toUserMessage(error, 'Ошибка API/WS')}</p>
  `;
  screen.append(info);

  const back = document.createElement('button');
  back.className = 'secondary-btn';
  back.textContent = 'Назад к каналам';
  back.addEventListener('click', () => navigate('channels-list'));
  screen.append(back);
}

function applyPendingScroll(screen, routeKey) {
  const target = pendingScrollByRoute.get(routeKey);
  if (!target) return;

  const doScroll = () => {
    if (target === '__LAST__') {
      const cards = screen.querySelectorAll('[data-message-key]');
      const last = cards[cards.length - 1];
      if (last) {
        last.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      pendingScrollByRoute.delete(routeKey);
      return;
    }

    const element = screen.querySelector(`[data-message-key="${target}"]`);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' });
      pendingScrollByRoute.delete(routeKey);
    }
  };

  setTimeout(doScroll, 20);
}

function renderPostCard(post, { navigate, selector, onToggleLike, onReply }) {
  const card = document.createElement('article');
  card.className = 'card stack channel-message-card';

  const title = document.createElement('div');
  title.className = 'channel-message-title author-line';

  const loginEl = document.createElement('span');
  loginEl.className = 'author-line-login';
  loginEl.textContent = post.authorLogin;

  const numberEl = document.createElement('span');
  numberEl.className = 'author-line-num';
  numberEl.textContent = `· #${post.localNumber}`;

  title.append(loginEl, numberEl);

  const body = document.createElement('p');
  body.className = 'channel-message-body';
  body.textContent = post.body;

  const stats = document.createElement('p');
  stats.className = 'channel-message-stats';
  stats.textContent = `Лайки: ${post.likesCount || 0}, ответы: ${post.repliesCount || 0}`;

  card.append(title, body, stats);

  const refKey = messageRefKey(post.messageRef);
  if (refKey) {
    card.dataset.messageKey = refKey;
  }

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
  likeButton.addEventListener('click', async (event) => {
    animatePress(event.currentTarget);
    if (isPending) return;
    likeButton.disabled = true;
    likeButton.textContent = 'Выполняется...';
    await onToggleLike(post.messageRef, isLiked ? 'unlike' : 'like', { likeButton });
  });

  const replyButton = document.createElement('button');
  replyButton.type = 'button';
  replyButton.className = 'secondary-btn channel-action-reply';
  replyButton.textContent = 'Ответить';
  replyButton.addEventListener('click', (event) => {
    animatePress(event.currentTarget);
    openReplyModal({
      onSubmit: async (text) => onReply(post.messageRef, text),
    });
  });

  const openThreadButton = document.createElement('button');
  openThreadButton.type = 'button';
  openThreadButton.className = 'secondary-btn channel-action-thread';
  openThreadButton.textContent = 'Открыть тред';
  openThreadButton.addEventListener('click', (event) => {
    animatePress(event.currentTarget);
    const route = buildThreadRoute(post.messageRef, selector);
    if (route) navigate(route);
  });

  actions.append(likeButton, replyButton, openThreadButton);
  card.append(actions);
  return card;
}

function renderBody(screen, navigate, routeKey, channelData, handlers) {
  const head = document.createElement('div');
  head.className = 'card channel-head-card';

  const title = document.createElement('strong');
  title.className = 'channel-head-title';
  title.textContent = String(channelData.channel.name || '').trim();

  const owner = document.createElement('p');
  owner.className = 'channel-head-meta';
  owner.textContent = `Владелец: ${channelData.channel.ownerName}`;

  const headActions = document.createElement('div');
  headActions.className = 'channel-head-actions';
  const aboutButton = document.createElement('button');
  aboutButton.type = 'button';
  aboutButton.className = 'secondary-btn small-btn';
  aboutButton.textContent = 'О канале';
  aboutButton.addEventListener('click', (event) => {
    animatePress(event.currentTarget);
    openAboutChannelModal(channelData.channel);
  });
  headActions.append(aboutButton);

  if (channelData.isOwnChannel) {
    const editButton = document.createElement('button');
    editButton.type = 'button';
    editButton.className = 'secondary-btn small-btn';
    editButton.textContent = '✎';
    editButton.title = 'Редактировать описание';
    editButton.addEventListener('click', (event) => {
      animatePress(event.currentTarget);
      openEditDescriptionModal({
        initialValue: channelData.channel.description || '',
        onSubmit: async (nextValue) => handlers.onEditDescription(nextValue),
      });
    });
    headActions.append(editButton);
  }

  head.append(title);
  head.append(owner, headActions);

  const actionButton = document.createElement('button');
  actionButton.className = channelData.isOwnChannel
    ? 'primary-btn channel-main-action'
    : 'destructive-btn channel-main-action';
  actionButton.textContent = channelData.isOwnChannel ? 'Добавить сообщение' : 'Отписаться от канала';

  const feed = document.createElement('div');
  feed.className = 'stack channel-feed';

  if (channelData.posts.length) {
    channelData.posts.forEach((post) => {
      feed.append(renderPostCard(post, {
        navigate,
        selector: channelData.selector,
        onToggleLike: handlers.onToggleLike,
        onReply: handlers.onReply,
      }));
    });
  } else {
    const empty = document.createElement('div');
    empty.className = 'card meta-muted';
    empty.textContent = 'Ждем ваших начинаний';
    feed.append(empty);
  }

  if (channelData.isOwnChannel) {
    actionButton.addEventListener('click', (event) => {
      animatePress(event.currentTarget);
      openAddMessageModal({
        channelName: channelData.channel.name,
        onSubmit: async (bodyText) => handlers.onAddPost(bodyText),
      });
    });
  } else {
    actionButton.addEventListener('click', handlers.onUnfollowChannel);
  }

  const backButton = document.createElement('button');
  backButton.className = 'secondary-btn channel-back-btn';
  backButton.textContent = 'Назад к каналам';
  backButton.addEventListener('click', () => navigate('channels-list'));

  screen.append(head, actionButton, feed, backButton);
  applyPendingScroll(screen, routeKey);
}

function renderSkeleton(screen) {
  const wrap = document.createElement('div');
  wrap.className = 'stack';
  wrap.append(createSkeletonCard(), createSkeletonCard(), createSkeletonCard());
  screen.append(wrap);
  return wrap;
}

export function render({ navigate, route }) {
  const channelId = route.params.channelId || '';
  const routeSelector = buildSelectorFromRoute(route, channelId);
  const routeKey = `${routeSelector?.ownerBlockchainName || ''}:${routeSelector?.channelRootBlockNumber || ''}:${routeSelector?.channelRootBlockHash || ''}`;

  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--channel';
  const appScreen = document.getElementById('app-screen');
  appScreen?.classList.add('channels-scroll-clean');

  const statusBox = document.createElement('div');
  statusBox.className = 'card status-line is-unavailable channels-status';
  statusBox.style.display = 'none';

  const showStatus = (message) => {
    if (!message) {
      statusBox.style.display = 'none';
      statusBox.textContent = '';
      return;
    }
    statusBox.textContent = message;
    statusBox.style.display = '';
  };

  const rerender = () => {
    const current = document.querySelector('section.channels-screen--channel');
    if (!current) return;
    const next = render({ navigate, route });
    current.replaceWith(next);
  };

  const requireSigningSession = () => {
    const login = state.session.login;
    const storagePwd = state.session.storagePwdInMemory;
    if (!login || !storagePwd) {
      throw new Error('Сессия недействительна. Выполните вход заново.');
    }
    return { login, storagePwd };
  };

  const onToggleLike = async (messageRef, action) => {
    const actionKey = makeReactionActionKey(messageRef);
    if (!actionKey) {
      throw new Error('Некорректная ссылка на сообщение для реакции.');
    }
    if (pendingReactionActions.has(actionKey)) return;

    const previousReaction = getMessageReactionState(messageRef);
    const nextReaction = action === 'unlike' ? 'unliked' : 'liked';
    pendingReactionActions.add(actionKey);

    try {
      const { login, storagePwd } = requireSigningSession();
      if (action === 'unlike') {
        await authService.addBlockUnlike({ login, storagePwd, message: messageRef });
      } else {
        await authService.addBlockLike({ login, storagePwd, message: messageRef });
      }
      setMessageReactionState(messageRef, nextReaction);
      softHaptic(10);
      rerender();
    } catch (error) {
      setMessageReactionState(messageRef, previousReaction || 'unliked');
      rerender();
      throw error;
    } finally {
      pendingReactionActions.delete(actionKey);
    }
  };

  const onReply = async (messageRef, text) => {
    const { login, storagePwd } = requireSigningSession();
    await authService.addBlockReply({ login, storagePwd, message: messageRef, text });

    const scrollTarget = messageRefKey(messageRef);
    if (scrollTarget) pendingScrollByRoute.set(routeKey, scrollTarget);

    softHaptic(15);
    showToast('Ответ отправлен');
    rerender();
  };

  const onAddPost = async (bodyText) => {
    const { login, storagePwd } = requireSigningSession();
    if (!routeSelector?.ownerBlockchainName || routeSelector.channelRootBlockNumber == null) {
      throw new Error('Идентификатор канала не готов.');
    }

    await authService.addBlockTextPost({
      login,
      storagePwd,
      channel: routeSelector,
      text: bodyText,
    });

    pendingScrollByRoute.set(routeKey, '__LAST__');
    softHaptic(15);
    showToast('Сообщение отправлено');
    rerender();
  };

  const onEditDescription = async (descriptionText) => {
    const { login, storagePwd } = requireSigningSession();
    const selector = routeSelector;
    const param = channelDescriptionParamKey(selector);
    if (!param) throw new Error('Идентификатор канала не готов для обновления описания.');

    const value = JSON.stringify({ v: String(descriptionText || '').trim() });
    await authService.addBlockUserParam({
      login,
      storagePwd,
      param,
      value,
    });

    softHaptic(10);
    showToast('Описание канала сохранено');
    rerender();
  };

  screen.append(
    renderHeader({
      title: '',
      leftAction: { label: '<', onClick: () => navigate('channels-list') },
    }),
  );
  screen.append(statusBox);

  const skeleton = renderSkeleton(screen);

  (async () => {
    try {
      const apiData = await loadFromApi(route, channelId);
      skeleton.remove();
      renderBody(screen, navigate, routeKey, apiData, {
        onToggleLike: async (messageRef, action) => {
          try {
            await onToggleLike(messageRef, action);
            showStatus('');
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
        onAddPost: async (bodyText) => {
          try {
            await onAddPost(bodyText);
            showStatus('');
          } catch (error) {
            throw new Error(toUserMessage(error, 'Не удалось добавить сообщение.'));
          }
        },
        onEditDescription: async (descriptionText) => {
          try {
            await onEditDescription(descriptionText);
            showStatus('');
          } catch (error) {
            throw new Error(toUserMessage(error, 'Не удалось сохранить описание.'));
          }
        },
        onUnfollowChannel: async (event) => {
          animatePress(event?.currentTarget);
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

            softHaptic(15);
            showToast('Отписка от канала выполнена');
            navigate('channels-list');
          } catch (error) {
            showStatus(toUserMessage(error, 'Не удалось отписаться от канала.'));
          }
        },
      });
    } catch (error) {
      skeleton.remove();
      if (isChannelsDemoMode()) {
        renderDemoFallback(screen, navigate, error);
        return;
      }
      renderLoadError(screen, navigate, toUserMessage(error, 'Не удалось загрузить канал.'), rerender);
    }
  })();

  screen.cleanup = () => {
    appScreen?.classList.remove('channels-scroll-clean');
  };

  return screen;
}
