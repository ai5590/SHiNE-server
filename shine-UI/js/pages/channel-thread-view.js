import { renderHeader } from '../components/header.js';
import { authService, getMessageReactionState, setMessageReactionState, state } from '../state.js';
import { captureClientError } from '../services/client-error-reporter.js';
import { toUserMessage } from '../services/ui-error-texts.js';
import {
  animatePress,
  createSkeletonCard,
  showToast,
  softHaptic,
} from '../services/channels-ux.js';

export const pageMeta = { id: 'channel-thread-view', title: 'Тред' };

const pendingReactionActions = new Set();
const pendingThreadScroll = new Map();

function logThreadRuntimeError(stage, error, context = {}) {
  const message = String(error?.message || error || 'thread runtime error');
  console.error(`[channel-thread-view:${stage}]`, error, context);
  captureClientError({
    kind: 'channels_thread_runtime',
    message,
    stack: error?.stack || '',
    context: { stage, ...context },
  });
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

function parseThreadSelector(route) {
  const params = route?.params || {};
  const blockNumber = toSafeInt(params.messageBlockNumber);
  if (!params.messageBlockchainName || blockNumber == null) return null;

  return {
    message: {
      blockchainName: String(params.messageBlockchainName),
      blockNumber,
      blockHash: normalizeRouteHash(params.messageBlockHash),
    },
    channel: {
      ownerBlockchainName: String(params.channelOwnerBlockchainName || ''),
      rootBlockNumber: toSafeInt(params.channelRootBlockNumber),
      rootBlockHash: normalizeRouteHash(params.channelRootBlockHash),
    },
  };
}

function allFeedSummaries() {
  const feed = state.channelsFeed || {};
  return [
    ...(feed.ownedChannels || []),
    ...(feed.followedUsersChannels || []),
    ...(feed.followedChannels || []),
  ];
}

function resolveChannelDisplayName(channelSelector) {
  if (!channelSelector?.ownerBlockchainName || channelSelector?.rootBlockNumber == null) return '';
  const ownerBch = String(channelSelector.ownerBlockchainName);
  const rootNo = Number(channelSelector.rootBlockNumber);
  const rootHash = normalizeRouteHash(channelSelector.rootBlockHash);

  const found = allFeedSummaries().find((summary) => (
    String(summary?.channel?.ownerBlockchainName || '') === ownerBch
    && Number(summary?.channel?.channelRoot?.blockNumber) === rootNo
    && normalizeRouteHash(summary?.channel?.channelRoot?.blockHash) === rootHash
  ));
  if (!found) return '';
  return `${found.channel?.ownerLogin || 'неизвестно'}/${found.channel?.channelName || 'канал'}`;
}

function buildBackRoute(selector) {
  const channel = selector?.channel;
  if (channel?.ownerBlockchainName && channel.rootBlockNumber != null) {
    return [
      'channel-view',
      encodeRoutePart(channel.ownerBlockchainName),
      channel.rootBlockNumber,
      channel.rootBlockHash,
    ].join('/');
  }
  return 'channels-list';
}

function buildTargetFromNode(node) {
  const blockchainName = String(node?.authorBlockchainName || '').trim();
  const blockNumber = Number(node?.messageRef?.blockNumber);
  const blockHash = normalizeMessageHash(node?.messageRef?.blockHash);
  if (!blockchainName || !Number.isFinite(blockNumber) || blockNumber < 0 || !blockHash) return null;
  return { blockchainName, blockNumber, blockHash };
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

function resolveNodeText(node) {
  return firstNonEmptyText(
    node?.text,
    node?.message,
    node?.body,
    latestVersionText(node?.versions),
  );
}

function openReplyModal({ onSubmit }) {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="thread-reply-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">Ответ</h3>
        <textarea id="thread-reply-text" class="input" rows="5" maxlength="2000" placeholder="Текст ответа"></textarea>
        <div class="meta-muted inline-error" id="thread-reply-error"></div>
        <div class="form-actions-grid">
          <button class="secondary-btn" id="thread-reply-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="thread-reply-submit" type="button">Отправить</button>
        </div>
      </div>
    </div>
  `;

  const textEl = root.querySelector('#thread-reply-text');
  const errorEl = root.querySelector('#thread-reply-error');
  const submitEl = root.querySelector('#thread-reply-submit');
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

  root.querySelector('#thread-reply-cancel')?.addEventListener('click', close);
  root.querySelector('#thread-reply-submit')?.addEventListener('click', async () => {
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

function renderNodeCard(node, heading, handlers, localNumber) {
  const card = document.createElement('article');
  card.className = 'card stack thread-node-card';

  const author = node?.authorLogin || 'автор';
  const text = resolveNodeText(node) || '(пусто)';
  const likes = Number(node?.likesCount || 0);
  const replies = Number(node?.repliesCount || 0);
  const versions = Number(node?.versionsTotal || 1);

  const headingEl = document.createElement('strong');
  headingEl.className = 'thread-node-heading';
  headingEl.textContent = heading;

  const meta = document.createElement('p');
  meta.className = 'thread-node-meta';
  meta.innerHTML = `
    <span class="author-line-login">${author}</span>
    <span class="author-line-num">· #${localNumber}</span>
  `;

  const body = document.createElement('p');
  body.className = 'thread-node-body';
  body.textContent = text;

  const stats = document.createElement('p');
  stats.className = 'thread-node-stats';
  stats.textContent = `Лайки: ${likes}, ответы: ${replies}, версий: ${versions}`;

  card.append(headingEl, meta, body, stats);

  const target = buildTargetFromNode(node);
  if (!target || !handlers) return card;

  const refKey = messageRefKey(target);
  if (refKey) card.dataset.messageKey = refKey;

  setMessageReactionState(target, node?.likedByMe === true ? 'liked' : 'unliked');

  const actionKey = makeReactionActionKey(target);
  const isPending = actionKey ? pendingReactionActions.has(actionKey) : false;

  const isLiked = getMessageReactionState(target) === 'liked';

  const actions = document.createElement('div');
  actions.className = 'thread-node-actions';

  const likeButton = document.createElement('button');
  likeButton.type = 'button';
  likeButton.className = 'secondary-btn thread-like-btn';
  if (isLiked) likeButton.classList.add('is-liked');
  likeButton.textContent = isPending ? 'Выполняется...' : (isLiked ? 'Убрать лайк' : 'Лайк');
  likeButton.disabled = isPending;
  likeButton.addEventListener('click', async (event) => {
    animatePress(event.currentTarget);
    if (isPending) return;
    likeButton.disabled = true;
    likeButton.textContent = 'Выполняется...';
    try {
      await handlers.onToggleLike(target, isLiked ? 'unlike' : 'like');
    } catch (error) {
      logThreadRuntimeError('like_click', error, {
        action: isLiked ? 'unlike' : 'like',
        targetBlockchainName: target?.blockchainName || '',
        targetBlockNumber: target?.blockNumber,
      });
      handlers?.onActionError?.(error, isLiked ? 'unlike' : 'like');
    }
  });

  const replyButton = document.createElement('button');
  replyButton.type = 'button';
  replyButton.className = 'secondary-btn thread-reply-btn';
  replyButton.textContent = 'Ответить';
  replyButton.addEventListener('click', (event) => {
    animatePress(event.currentTarget);
    openReplyModal({
      onSubmit: async (textValue) => handlers.onReply(target, textValue),
    });
  });

  actions.append(likeButton, replyButton);
  card.append(actions);
  return card;
}

function renderDescendants(items, handlers, nextNumber, depth = 0) {
  const wrap = document.createElement('div');
  wrap.className = 'stack';

  const normalized = Array.isArray(items) ? items : [];
  normalized.forEach((branch, index) => {
    try {
      const nodeNumber = nextNumber();
      const row = renderNodeCard(branch?.node, `Ответ ${index + 1}`, handlers, nodeNumber);
      row.classList.add('thread-node-level');
      row.style.setProperty('--depth', String(Math.min(depth, 4)));
      wrap.append(row);

      if (Array.isArray(branch?.children) && branch.children.length) {
        wrap.append(renderDescendants(branch.children, handlers, nextNumber, depth + 1));
      }
    } catch (error) {
      logThreadRuntimeError('render_descendants_branch', error, { depth, index });
    }
  });

  return wrap;
}

function applyPendingScroll(screen, routeKey) {
  const target = pendingThreadScroll.get(routeKey);
  if (!target) return;

  const doScroll = () => {
    if (target === '__LAST_REPLY__') {
      const cards = screen.querySelectorAll('.thread-block--replies [data-message-key]');
      const last = cards[cards.length - 1];
      if (last) {
        last.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      pendingThreadScroll.delete(routeKey);
      return;
    }

    const node = screen.querySelector(`[data-message-key="${target}"]`);
    if (node) {
      node.scrollIntoView({ behavior: 'smooth', block: 'center' });
      pendingThreadScroll.delete(routeKey);
    }
  };

  setTimeout(doScroll, 20);
}

function renderSkeleton(screen) {
  const wrap = document.createElement('div');
  wrap.className = 'stack';
  wrap.append(createSkeletonCard(), createSkeletonCard(), createSkeletonCard());
  screen.append(wrap);
  return wrap;
}

export function render({ navigate, route }) {
  const selector = parseThreadSelector(route);
  const backRoute = buildBackRoute(selector);
  const channelDisplayName = resolveChannelDisplayName(selector?.channel);
  const routeKey = `${selector?.message?.blockchainName || ''}:${selector?.message?.blockNumber || ''}:${selector?.message?.blockHash || ''}`;

  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--thread';
  const appScreen = document.getElementById('app-screen');
  appScreen?.classList.add('channels-scroll-clean');

  const userIndicator = document.createElement('div');
  userIndicator.className = 'card channels-user-chip';
  userIndicator.textContent = `Вы вошли как @${state.session.login || 'неизвестно'}`;

  const channelIndicator = document.createElement('div');
  channelIndicator.className = 'card channels-user-chip';
  channelIndicator.textContent = `Канал: ${channelDisplayName || selector?.channel?.ownerBlockchainName || 'неизвестно'}`;

  const statusBox = document.createElement('div');
  statusBox.className = 'card status-line is-unavailable channels-status';
  statusBox.style.display = 'none';

  const rerender = () => {
    try {
      const current = document.querySelector('section.channels-screen--thread');
      if (!current) return;
      const next = render({ navigate, route });
      current.replaceWith(next);
    } catch (error) {
      logThreadRuntimeError('rerender', error, { routeHash: window.location.hash });
    }
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
    if (!login || !storagePwd) throw new Error('Сессия недействительна. Выполните вход заново.');
    return { login, storagePwd };
  };

  const handlers = {
    onToggleLike: async (target, action) => {
      const actionKey = makeReactionActionKey(target);
      if (!actionKey) throw new Error('Некорректная ссылка на сообщение для реакции.');
      if (pendingReactionActions.has(actionKey)) return;

      const previousReaction = getMessageReactionState(target);
      const nextReaction = action === 'unlike' ? 'unliked' : 'liked';

      pendingReactionActions.add(actionKey);
      try {
        const { login, storagePwd } = requireSigningSession();
        if (action === 'unlike') {
          await authService.addBlockUnlike({ login, storagePwd, message: target });
        } else {
          await authService.addBlockLike({ login, storagePwd, message: target });
        }

        setMessageReactionState(target, nextReaction);
        softHaptic(10);
        rerender();
      } catch (error) {
        setMessageReactionState(target, previousReaction || 'unliked');
        rerender();
        throw error;
      } finally {
        pendingReactionActions.delete(actionKey);
      }
    },
    onReply: async (target, textValue) => {
      const { login, storagePwd } = requireSigningSession();
      await authService.addBlockReply({ login, storagePwd, message: target, text: textValue });
      pendingThreadScroll.set(routeKey, '__LAST_REPLY__');
      softHaptic(15);
      showToast('Ответ отправлен');
      showStatus('');
      rerender();
    },
    onActionError: (error, action) => {
      const fallback = action === 'unlike'
        ? 'Не удалось убрать лайк.'
        : 'Не удалось поставить лайк.';
      showStatus(toUserMessage(error, fallback));
    },
  };

  screen.append(
    renderHeader({
      title: 'Тред',
      leftAction: { label: '<', onClick: () => navigate(backRoute) },
    }),
  );
  screen.append(userIndicator, channelIndicator, statusBox);

  if (!selector) {
    const invalid = document.createElement('div');
    invalid.className = 'card meta-muted';
    invalid.textContent = 'Некорректный идентификатор треда в адресе страницы.';
    screen.append(invalid);
    return screen;
  }

  const skeleton = renderSkeleton(screen);

  (async () => {
    try {
      const payload = await authService.getMessageThread(selector.message, 20, 2, 50, state.session.login);
      skeleton.remove();

      const ancestors = Array.isArray(payload?.ancestors) ? payload.ancestors : [];
      const focus = payload?.focus || null;
      const descendants = Array.isArray(payload?.descendants) ? payload.descendants : [];

      const summary = document.createElement('div');
      summary.className = 'card thread-summary';
      summary.textContent = `Предки: ${ancestors.length}, ответы: ${descendants.length}`;
      screen.append(summary);

      let seq = 0;
      const nextNumber = () => {
        seq += 1;
        return seq;
      };

      if (ancestors.length) {
        const ancestorsWrap = document.createElement('div');
        ancestorsWrap.className = 'stack thread-block thread-block--ancestors';
        const title = document.createElement('h3');
        title.className = 'section-title';
        title.textContent = 'Предыдущие сообщения';
        ancestorsWrap.append(title);
        ancestors.forEach((node, index) => {
          ancestorsWrap.append(renderNodeCard(node, `Предок ${index + 1}`, handlers, nextNumber()));
        });
        screen.append(ancestorsWrap);
      }

      if (focus) {
        const focusWrap = document.createElement('div');
        focusWrap.className = 'stack thread-block thread-block--focus';
        const title = document.createElement('h3');
        title.className = 'section-title';
        title.textContent = 'Текущее сообщение';
        focusWrap.append(title, renderNodeCard(focus, 'Выбранное сообщение', handlers, nextNumber()));
        screen.append(focusWrap);
      }

      const descendantsWrap = document.createElement('div');
      descendantsWrap.className = 'stack thread-block thread-block--replies';
      const descendantsTitle = document.createElement('h3');
      descendantsTitle.className = 'section-title';
      descendantsTitle.textContent = 'Ответы';
      descendantsWrap.append(descendantsTitle);

      if (descendants.length) {
        descendantsWrap.append(renderDescendants(descendants, handlers, nextNumber));
      } else {
        const empty = document.createElement('div');
        empty.className = 'card meta-muted';
        empty.textContent = 'Ответов пока нет.';
        descendantsWrap.append(empty);
      }

      screen.append(descendantsWrap);
      applyPendingScroll(screen, routeKey);
    } catch (error) {
      skeleton.remove();
      const failed = document.createElement('div');
      failed.className = 'card meta-muted';
      failed.textContent = `Не удалось загрузить тред: ${toUserMessage(error, 'неизвестная ошибка')}`;
      screen.append(failed);
    }
  })();

  screen.cleanup = () => {
    appScreen?.classList.remove('channels-scroll-clean');
  };

  return screen;
}
