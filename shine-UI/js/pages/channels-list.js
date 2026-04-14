import { channels as mockChannels } from '../mock-data.js';
import { authService, setChannelsFeed, state } from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';
import {
  animatePress,
  createSkeletonCard,
  formatRelativeTime,
  readChannelNotificationsState,
  showToast,
  softHaptic,
  writeChannelNotificationsState,
} from '../services/channels-ux.js';

export const pageMeta = { id: 'channels-list', title: 'Каналы' };

const CREATE_CHANNEL_FLASH_KEY = 'shine-channels-create-success';
const MENU_OVERLAY_ID = 'channels-context-menu-overlay';

function isChannelsDemoMode() {
  try {
    const qs = new URLSearchParams(window.location.search);
    if (qs.get('channelsDemo') === '1') return true;
    return localStorage.getItem('shine-channels-demo') === '1';
  } catch {
    return false;
  }
}

function normalizeHash(hash) {
  const normalized = String(hash || '').trim().toLowerCase();
  return normalized || '0';
}

function encodeRoutePart(value = '') {
  return encodeURIComponent(String(value));
}

function normalizeLoginInput(value) {
  return String(value || '').trim().replace(/^@+/, '');
}

function buildChannelRouteFromSummary(summary, fallbackId) {
  const ownerBch = summary?.channel?.ownerBlockchainName;
  const rootBlockNumber = summary?.channel?.channelRoot?.blockNumber;
  const rootBlockHash = normalizeHash(summary?.channel?.channelRoot?.blockHash);
  if (!ownerBch || rootBlockNumber == null) return `channel-view/${fallbackId}`;
  return `channel-view/${encodeRoutePart(ownerBch)}/${Number(rootBlockNumber)}/${rootBlockHash}`;
}

function avatarLetterFromName(name = '') {
  const first = Array.from(String(name || '').trim())[0] || '#';
  return first.toUpperCase();
}

function allFeedSummaries() {
  const feed = state.channelsFeed || {};
  return [
    ...(feed.ownedChannels || []),
    ...(feed.followedUsersChannels || []),
    ...(feed.followedChannels || []),
  ];
}

function uniqueBy(items, keySelector) {
  const seen = new Set();
  const out = [];
  for (const item of items) {
    const key = keySelector(item);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

function createDebounced(fn, delayMs = 250) {
  let timer = null;
  return (...args) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, delayMs);
  };
}

async function resolveChannelTargetFromInput(rawInput) {
  const input = String(rawInput || '').trim();
  if (!input) throw new Error('Введите канал.');

  const bySelector = input.match(/^([A-Za-z0-9._-]+-\d+)\s*[:/]\s*(\d+)\s*[:/]\s*([A-Fa-f0-9]{1,64})$/);
  if (bySelector) {
    return {
      ownerBlockchainName: bySelector[1],
      rootBlockNumber: Number(bySelector[2]),
      rootBlockHash: normalizeHash(bySelector[3]),
    };
  }

  const byOwnerAndName = input.match(/^@?([^/#\s]+)\s*\/\s*#?(.+)$/);
  if (byOwnerAndName) {
    const ownerLogin = normalizeLoginInput(byOwnerAndName[1]);
    const channelName = String(byOwnerAndName[2] || '').trim().toLowerCase();
    if (!ownerLogin || !channelName) {
      throw new Error('Укажите канал в формате user/channel.');
    }

    const user = await authService.getUser(ownerLogin);
    if (!user?.exists || !user?.blockchainName) {
      throw new Error('Пользователь не найден.');
    }

    const ownerFeed = await authService.listSubscriptionsFeed(ownerLogin, 500);
    const ownChannels = Array.isArray(ownerFeed?.ownedChannels) ? ownerFeed.ownedChannels : [];
    const matches = ownChannels.filter((item) => (
      String(item?.channel?.channelName || '').trim().toLowerCase() === channelName
    ));

    if (!matches.length) {
      throw new Error('Канал не найден у указанного автора.');
    }

    const primaryMatches = matches.filter((item) => (
      String(item?.channel?.ownerBlockchainName || '') === String(user.blockchainName || '')
    ));
    const pool = primaryMatches.length ? primaryMatches : matches;
    const match = [...pool].sort((a, b) => (
      Number(b?.channel?.channelRoot?.blockNumber || -1) - Number(a?.channel?.channelRoot?.blockNumber || -1)
    ))[0];

    return {
      ownerBlockchainName: String(match?.channel?.ownerBlockchainName || user.blockchainName),
      rootBlockNumber: Number(match?.channel?.channelRoot?.blockNumber),
      rootBlockHash: normalizeHash(match?.channel?.channelRoot?.blockHash),
    };
  }

  const byNameOnly = input.replace(/^#/, '').trim().toLowerCase();
  const summaries = allFeedSummaries();
  const matches = summaries.filter((summary) => (
    String(summary?.channel?.channelName || '').trim().toLowerCase() === byNameOnly
  ));

  if (!matches.length) {
    throw new Error('Канал не найден. Укажите user/channel или выберите из списка.');
  }
  if (matches.length > 1) {
    throw new Error('Найдено несколько каналов с таким именем. Уточните в формате user/channel.');
  }

  const one = matches[0];
  return {
    ownerBlockchainName: String(one?.channel?.ownerBlockchainName || ''),
    rootBlockNumber: Number(one?.channel?.channelRoot?.blockNumber),
    rootBlockHash: normalizeHash(one?.channel?.channelRoot?.blockHash),
  };
}

function channelSuggestionsByInput(rawInput) {
  const q = String(rawInput || '').trim().toLowerCase();
  if (q.length < 1) return [];

  const rows = allFeedSummaries().map((summary) => {
    const owner = String(summary?.channel?.ownerLogin || '').trim();
    const channel = String(summary?.channel?.channelName || '').trim();
    if (!owner || !channel) return null;
    return {
      key: `${owner.toLowerCase()}/${channel.toLowerCase()}`,
      label: `@${owner}/${channel}`,
      owner,
      channel,
    };
  }).filter(Boolean);

  return uniqueBy(rows, (it) => it.key)
    .filter((it) => (
      it.channel.toLowerCase().includes(q) ||
      `${it.owner.toLowerCase()}/${it.channel.toLowerCase()}`.includes(q) ||
      `@${it.owner.toLowerCase()}/${it.channel.toLowerCase()}`.includes(q)
    ))
    .slice(0, 7)
    .map((it) => it.label);
}

function renderSuggestions(container, values, onPick) {
  container.innerHTML = '';
  if (!values.length) {
    container.style.display = 'none';
    return;
  }

  container.style.display = '';
  values.forEach((value) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'channel-search-item';
    btn.textContent = value;
    btn.addEventListener('click', () => onPick(value));
    container.append(btn);
  });
}

function normalizeComparableLogin(value) {
  return normalizeLoginInput(value).toLowerCase();
}

function isFollowedUserVisible(targetLogin) {
  const expected = normalizeComparableLogin(targetLogin);
  if (!expected) return false;
  const rows = Array.isArray(state.channelsFeed?.followedUsersChannels)
    ? state.channelsFeed.followedUsersChannels
    : [];
  return rows.some((row) => normalizeComparableLogin(row?.channel?.ownerLogin) === expected);
}

function isFollowedChannelVisible(target) {
  const rows = Array.isArray(state.channelsFeed?.followedChannels)
    ? state.channelsFeed.followedChannels
    : [];
  const expectedBch = String(target?.ownerBlockchainName || '');
  const expectedNo = Number(target?.rootBlockNumber);
  const expectedHash = normalizeHash(target?.rootBlockHash);
  if (!expectedBch || !Number.isFinite(expectedNo)) return false;

  return rows.some((row) => {
    const rowBch = String(row?.channel?.ownerBlockchainName || '');
    const rowNo = Number(row?.channel?.channelRoot?.blockNumber);
    const rowHash = normalizeHash(row?.channel?.channelRoot?.blockHash);
    return rowBch === expectedBch && rowNo === expectedNo && rowHash === expectedHash;
  });
}

function openSimpleSubscribeModal({ kind, kindLabel, submitLabel, unfollow = false, onSuccess }) {
  const targetHint = kind === 'channel'
    ? '<p class="meta-muted">Канал: user/channel, имя канала или bch:number:hash.</p>'
    : '<p class="meta-muted">Автор: @login или login.</p>';
  const submitText = submitLabel || (unfollow ? 'Отписаться' : 'Подписаться');
  const placeholder = kind === 'channel' ? '@owner/channel' : '@login';

  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channels-subscribe-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">${kindLabel}</h3>
        ${targetHint}
        <label class="meta-muted" for="subscribe-input">Поиск</label>
        <input id="subscribe-input" class="input" placeholder="${placeholder}" autocomplete="off" />
        <div id="subscribe-suggest" class="channels-search-suggest" style="display:none"></div>
        <div id="subscribe-error" class="meta-muted inline-error"></div>
        <div class="form-actions-grid">
          <button class="secondary-btn" id="sub-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="sub-submit" type="button">${submitText}</button>
        </div>
      </div>
    </div>
  `;

  const inputEl = root.querySelector('#subscribe-input');
  const suggestEl = root.querySelector('#subscribe-suggest');
  const errorEl = root.querySelector('#subscribe-error');
  const submitEl = root.querySelector('#sub-submit');

  let inFlight = false;

  const setBusy = (busy) => {
    inFlight = !!busy;
    submitEl.disabled = inFlight;
    if (inputEl) inputEl.disabled = inFlight;
    submitEl.textContent = inFlight ? 'Выполняем...' : submitText;
  };

  const close = () => {
    root.innerHTML = '';
  };

  const applySuggestion = (value) => {
    if (!inputEl) return;
    inputEl.value = value;
    if (suggestEl) suggestEl.style.display = 'none';
    inputEl.focus();
  };

  const refreshSuggestions = createDebounced(async () => {
    if (!inputEl || !suggestEl || inFlight) return;

    const raw = String(inputEl.value || '').trim();
    if (!raw) {
      suggestEl.style.display = 'none';
      suggestEl.innerHTML = '';
      return;
    }

    try {
      if (kind === 'user') {
        const prefix = normalizeLoginInput(raw);
        if (prefix.length < 2) {
          suggestEl.style.display = 'none';
          suggestEl.innerHTML = '';
          return;
        }
        const logins = await authService.searchUsers(prefix);
        const suggestions = (Array.isArray(logins) ? logins : []).slice(0, 8).map((login) => `@${login}`);
        renderSuggestions(suggestEl, suggestions, applySuggestion);
        return;
      }

      const suggestions = channelSuggestionsByInput(raw);
      renderSuggestions(suggestEl, suggestions, applySuggestion);
    } catch {
      suggestEl.style.display = 'none';
      suggestEl.innerHTML = '';
    }
  }, 240);

  root.querySelector('#sub-cancel')?.addEventListener('click', close);

  inputEl?.addEventListener('input', () => {
    if (!errorEl) return;
    errorEl.textContent = '';
    refreshSuggestions();
  });

  submitEl?.addEventListener('click', async () => {
    if (inFlight) return;

    const login = state.session.login;
    const storagePwd = state.session.storagePwdInMemory;
    const value = String(inputEl?.value || '').trim();

    if (!login || !storagePwd) {
      errorEl.textContent = 'Сессия недействительна. Выполните вход заново.';
      return;
    }

    if (!value) {
      errorEl.textContent = 'Введите идентификатор.';
      return;
    }

    setBusy(true);
    errorEl.textContent = '';

    try {
      let channelTarget = null;
      let userTargetLogin = '';

      if (kind === 'user') {
        userTargetLogin = normalizeLoginInput(value);
        await authService.addBlockFollowUser({
          login,
          targetLogin: userTargetLogin,
          storagePwd,
          unfollow,
        });
      } else if (kind === 'channel') {
        channelTarget = await resolveChannelTargetFromInput(value);
        if (!channelTarget?.ownerBlockchainName || !Number.isFinite(channelTarget.rootBlockNumber)) {
          throw new Error('Канал не найден.');
        }

        await authService.addBlockFollowChannel({
          login,
          storagePwd,
          targetBlockchainName: channelTarget.ownerBlockchainName,
          targetBlockNumber: channelTarget.rootBlockNumber,
          targetBlockHashHex: channelTarget.rootBlockHash,
          unfollow,
        });
      } else {
        throw new Error('Неподдерживаемый тип подписки');
      }

      if (typeof onSuccess === 'function') {
        await onSuccess();
      }

      if (kind === 'user') {
        const visible = isFollowedUserVisible(userTargetLogin);
        if (!unfollow && !visible) {
          throw new Error('Подписка не подтвердилась после обновления списка.');
        }
        if (unfollow && visible) {
          throw new Error('Отписка не подтвердилась после обновления списка.');
        }
      }

      if (kind === 'channel') {
        const visible = isFollowedChannelVisible(channelTarget);
        if (!unfollow && !visible) {
          throw new Error('Подписка на канал не подтвердилась после обновления списка.');
        }
        if (unfollow && visible) {
          throw new Error('Отписка от канала не подтвердилась после обновления списка.');
        }
      }

      softHaptic(15);
      showToast(unfollow ? 'Отписка выполнена' : 'Подписка выполнена');
      close();
    } catch (error) {
      errorEl.textContent = toUserMessage(error, `${submitText} не удалось.`);
      setBusy(false);
    }
  });

  if (inputEl) inputEl.focus();
}

function mapMockGroups() {
  const mapRow = (channel) => ({
    ...channel,
    route: `channel-view/${channel.id}`,
    tabCategory: channel.kind === 'own' || channel.kind === 'own-personal' ? 'my' : 'subscriptions',
    messagePreview: channel.lastMessage || 'Ждем ваших начинаний',
    isSubscribed: channel.kind !== 'own' && channel.kind !== 'own-personal',
    isOwnChannel: channel.kind === 'own' || channel.kind === 'own-personal',
    notificationsEnabled: false,
    messagesCount: Number(channel.messagesCount || 0),
    lastMessageAt: 0,
    ownerName: String(channel.ownerName || 'неизвестно'),
    channelName: String(channel.channelName || channel.title || ''),
    title: String(channel.title || channel.channelName || ''),
  });

  const ownChannels = mockChannels
    .filter((channel) => channel.kind === 'own-personal' || channel.kind === 'own')
    .map((item) => ({ ...mapRow(item), tabCategory: 'my' }));

  const followedUserChannels = mockChannels
    .filter((channel) => channel.kind === 'followed-user-channel')
    .map((item) => ({ ...mapRow(item), tabCategory: 'authors' }));

  const subscribedChannels = mockChannels
    .filter((channel) => channel.kind === 'subscribed')
    .map((item) => ({ ...mapRow(item), tabCategory: 'subscriptions' }));

  return { ownChannels, followedUserChannels, subscribedChannels, index: {} };
}

function mapApiChannelRow(summary, bucketKey, idx, index, notificationsState) {
  const rowId = `${bucketKey}-${idx}`;
  index[rowId] = summary;

  const ownerLogin = summary?.channel?.ownerLogin || 'неизвестно';
  const channelName = summary?.channel?.channelName || '(без названия)';
  const channelDescription = String(summary?.channel?.channelDescription || '').trim();
  const isOwn = bucketKey === 'own';
  const tabCategory = bucketKey === 'own'
    ? 'my'
    : bucketKey === 'followedUsers'
      ? 'authors'
      : 'subscriptions';

  const title = isOwn
    ? channelName
    : tabCategory === 'authors'
      ? channelName
      : `${ownerLogin}/${channelName}`;

  return {
    id: rowId,
    route: buildChannelRouteFromSummary(summary, rowId),
    ownerName: ownerLogin,
    ownerBlockchainName: summary?.channel?.ownerBlockchainName || '',
    channelRootBlockNumber: Number(summary?.channel?.channelRoot?.blockNumber),
    channelRootBlockHash: normalizeHash(summary?.channel?.channelRoot?.blockHash),
    avatar: avatarLetterFromName(channelName),
    title,
    channelName,
    channelDescription,
    messagePreview: summary?.lastMessage?.text || 'Ждем ваших начинаний',
    messagesCount: Number(summary?.messagesCount || 0),
    lastMessageAt: Number(summary?.lastMessage?.createdAtMs || 0),
    tabCategory,
    isOwnChannel: isOwn,
    isSubscribed: !isOwn,
    notificationsEnabled: notificationsState[rowId] === true,
    pending: false,
  };
}

function pullCreateSuccessFlash() {
  try {
    const value = String(sessionStorage.getItem(CREATE_CHANNEL_FLASH_KEY) || '').trim();
    if (value) sessionStorage.removeItem(CREATE_CHANNEL_FLASH_KEY);
    return value;
  } catch {
    return '';
  }
}

function mapApiFeed(feed, notificationsState) {
  const index = {};
  const ownChannels = (feed?.ownedChannels || []).map((it, idx) => mapApiChannelRow(it, 'own', idx, index, notificationsState));
  const followedUserChannels = (feed?.followedUsersChannels || []).map((it, idx) => mapApiChannelRow(it, 'followedUsers', idx, index, notificationsState));
  const subscribedChannels = (feed?.followedChannels || []).map((it, idx) => mapApiChannelRow(it, 'followedChannels', idx, index, notificationsState));

  return { ownChannels, followedUserChannels, subscribedChannels, index };
}

function toListModel(groups) {
  return [
    ...(groups.ownChannels || []),
    ...(groups.followedUserChannels || []),
    ...(groups.subscribedChannels || []),
  ];
}

function renderEmptyState(activeTab, navigate) {
  const wrap = document.createElement('div');
  wrap.className = 'channels-empty-state';

  const icon = document.createElement('div');
  icon.className = 'channels-empty-icon';
  icon.textContent = '◌';

  const text = document.createElement('div');
  text.className = 'meta-muted';
  text.textContent = 'В этом разделе пока нет каналов';

  wrap.append(icon, text);

  if (activeTab === 'my') {
    const cta = document.createElement('button');
    cta.type = 'button';
    cta.className = 'secondary-btn';
    cta.textContent = 'Создать первый канал';
    cta.addEventListener('click', () => navigate('add-channel-view'));
    wrap.append(cta);
  }

  return wrap;
}

function renderSkeletonList(container, count = 4) {
  container.innerHTML = '';
  const list = document.createElement('div');
  list.className = 'stack';
  for (let i = 0; i < count; i += 1) {
    list.append(createSkeletonCard());
  }
  container.append(list);
}

function renderErrorState(container, error, onRetry) {
  const errCard = document.createElement('div');
  errCard.className = 'card stack channels-status';

  const title = document.createElement('strong');
  title.textContent = 'Не удалось загрузить каналы';

  const details = document.createElement('p');
  details.className = 'meta-muted';
  details.textContent = toUserMessage(error, 'Проверьте подключение к серверу и повторите попытку.');

  const retry = document.createElement('button');
  retry.className = 'primary-btn';
  retry.type = 'button';
  retry.textContent = 'Повторить';
  retry.addEventListener('click', onRetry);

  errCard.append(title, details, retry);
  container.append(errCard);
}

function renderDemoFallback(container, navigate, error, onRetry) {
  const info = document.createElement('div');
  info.className = 'card stack';
  info.innerHTML = `
    <strong>Включен демо-режим</strong>
    <p class="meta-muted">Данные сервера недоступны. Показаны мок-каналы, потому что включен channelsDemo.</p>
    <p class="meta-muted">${toUserMessage(error, 'Ошибка API/WS')}</p>
  `;

  const retry = document.createElement('button');
  retry.className = 'secondary-btn';
  retry.type = 'button';
  retry.textContent = 'Повторить запрос к серверу';
  retry.addEventListener('click', onRetry);

  info.append(retry);
  container.append(info);

  const groups = mapMockGroups();
  const list = document.createElement('div');
  list.className = 'stack';

  toListModel(groups).forEach((channel) => {
    const row = document.createElement('article');
    row.className = 'channel-row';
    row.innerHTML = `
      <div class="avatar">${channel.avatar || channel.initials || '#'}</div>
      <div class="channel-row-main">
        <strong class="channel-row-title">${channel.title || channel.displayName || channel.name}</strong>
        <p class="channel-row-message">${channel.messagePreview || 'Ждем ваших начинаний'}</p>
      </div>
      <div class="channel-row-controls">
        <span class="channel-row-time">—</span>
      </div>
    `;
    row.addEventListener('click', () => navigate(channel.route || `channel-view/${channel.id}`));
    list.append(row);
  });

  container.append(list);
}

function closeChannelMenu(listState, clearOpenMenuId = true) {
  if (typeof listState.menuCleanup === 'function') {
    listState.menuCleanup();
  }
  listState.menuCleanup = null;
  const root = document.getElementById('modal-root');
  if (root) {
    const overlay = root.querySelector(`#${MENU_OVERLAY_ID}`);
    if (overlay) overlay.remove();
  }
  if (clearOpenMenuId) {
    listState.openMenuId = null;
  }
}

function openChannelMenu({ listState, channel, anchorEl, refreshFeed, rerenderList }) {
  closeChannelMenu(listState, false);

  const root = document.getElementById('modal-root');
  if (!root || !anchorEl) return;

  const rect = anchorEl.getBoundingClientRect();
  const menuWidth = Math.min(250, Math.max(220, window.innerWidth - 28));
  let left = rect.right - menuWidth;
  left = Math.max(12, Math.min(left, window.innerWidth - menuWidth - 12));

  const estimatedHeight = 210;
  let top = rect.bottom + 8;
  if (top + estimatedHeight > window.innerHeight - 10) {
    top = Math.max(12, rect.top - estimatedHeight - 8);
  }

  const overlay = document.createElement('div');
  overlay.id = MENU_OVERLAY_ID;
  overlay.className = 'channels-menu-overlay';

  const menu = document.createElement('div');
  menu.className = 'channel-menu-wrap channel-menu-wrap--portal';
  menu.style.left = `${Math.round(left)}px`;
  menu.style.top = `${Math.round(top)}px`;
  menu.style.width = `${Math.round(menuWidth)}px`;

  const canToggleSubscription = !channel.isOwnChannel;
  const actionBtn = document.createElement('button');
  actionBtn.type = 'button';
  actionBtn.className = `channel-menu-item ${channel.isSubscribed ? 'destructive' : ''}`.trim();

  if (canToggleSubscription) {
    actionBtn.textContent = channel.pending
      ? 'Выполняется...'
      : channel.isSubscribed
        ? 'Отписаться'
        : 'Подписаться';
    actionBtn.disabled = !!channel.pending;

    actionBtn.addEventListener('click', async (event) => {
      event.stopPropagation();
      if (channel.pending) return;

      const login = state.session.login;
      const storagePwd = state.session.storagePwdInMemory;
      if (!login || !storagePwd) {
        showToast('Сессия недействительна. Выполните вход заново.', { kind: 'error' });
        return;
      }

      channel.pending = true;
      actionBtn.disabled = true;
      actionBtn.textContent = 'Выполняется...';

      const nextSubscribed = !channel.isSubscribed;
      try {
        await authService.addBlockFollowChannel({
          login,
          storagePwd,
          targetBlockchainName: channel.ownerBlockchainName,
          targetBlockNumber: channel.channelRootBlockNumber,
          targetBlockHashHex: channel.channelRootBlockHash,
          unfollow: !nextSubscribed,
        });

        channel.isSubscribed = nextSubscribed;
        channel.pending = false;
        softHaptic(15);
        showToast(nextSubscribed ? 'Подписка на канал включена' : 'Подписка на канал отключена');
        closeChannelMenu(listState);
        await refreshFeed();
      } catch (error) {
        channel.pending = false;
        actionBtn.disabled = false;
        actionBtn.textContent = channel.isSubscribed ? 'Отписаться' : 'Подписаться';
        showToast(toUserMessage(error, 'Не удалось изменить подписку.'), { kind: 'error' });
        rerenderList();
      }
    });
  } else {
    actionBtn.textContent = 'Собственный канал';
    actionBtn.disabled = true;
  }

  const toggleWrap = document.createElement('div');
  toggleWrap.className = 'channel-menu-toggle';

  const toggleLabel = document.createElement('span');
  toggleLabel.textContent = 'Уведомления';

  const toggleBtn = document.createElement('button');
  toggleBtn.type = 'button';
  toggleBtn.className = `channel-toggle-btn ${channel.notificationsEnabled ? 'is-on' : ''}`.trim();
  toggleBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    animatePress(toggleBtn);

    channel.notificationsEnabled = !channel.notificationsEnabled;
    const next = { ...listState.notificationsState, [channel.id]: channel.notificationsEnabled };
    listState.notificationsState = next;
    writeChannelNotificationsState(next);

    toggleBtn.classList.toggle('is-on', channel.notificationsEnabled);
    softHaptic(10);
  });

  toggleWrap.append(toggleLabel, toggleBtn);
  menu.append(actionBtn, toggleWrap);
  overlay.append(menu);
  root.append(overlay);

  const onOverlayClick = (event) => {
    if (event.target === overlay) {
      closeChannelMenu(listState);
      rerenderList();
    }
  };

  const onWindowResize = () => {
    closeChannelMenu(listState);
    rerenderList();
  };

  overlay.addEventListener('click', onOverlayClick);
  window.addEventListener('resize', onWindowResize);

  listState.menuCleanup = () => {
    overlay.removeEventListener('click', onOverlayClick);
    window.removeEventListener('resize', onWindowResize);
  };
}

function renderChannelMain(channel, activeTab) {
  const main = document.createElement('div');
  main.className = 'channel-row-main';

  if (activeTab === 'authors') {
    const author = document.createElement('p');
    author.className = 'channel-row-author';
    author.textContent = `@${channel.ownerName}`;

    const title = document.createElement('strong');
    title.className = 'channel-row-title';
    title.textContent = channel.channelName ? `#${channel.channelName}` : channel.title;

    const preview = document.createElement('p');
    preview.className = 'channel-row-message';
    preview.textContent = channel.messagePreview || 'Ждем ваших начинаний';

    const meta = document.createElement('p');
    meta.className = 'channel-row-owner';
    meta.textContent = `Сообщений: ${channel.messagesCount || 0}`;

    main.append(author, title, preview, meta);
    return main;
  }

  const title = document.createElement('strong');
  title.className = 'channel-row-title';
  title.textContent = activeTab === 'my' ? channel.channelName : channel.title;

  if (activeTab === 'my' && channel.channelDescription) {
    const desc = document.createElement('p');
    desc.className = 'channel-row-description';
    desc.textContent = channel.channelDescription;
    main.append(desc);
  }

  const preview = document.createElement('p');
  preview.className = 'channel-row-message';
  preview.textContent = channel.messagePreview || 'Ждем ваших начинаний';

  const meta = document.createElement('p');
  meta.className = 'channel-row-owner';
  meta.textContent = `Сообщений: ${channel.messagesCount || 0}`;

  main.prepend(title);
  main.append(preview, meta);
  return main;
}

function renderListContent({ screen, container, listState, navigate, refreshFeed }) {
  container.innerHTML = '';

  const allChannels = listState.channels || [];
  const activeTab = listState.activeTab;
  const filtered = allChannels.filter((channel) => channel.tabCategory === activeTab);

  if (!filtered.length) {
    container.append(renderEmptyState(activeTab, navigate));
    return;
  }

  const list = document.createElement('div');
  list.className = 'stack channels-groups channels-list-body-fade';

  const rerenderList = () => renderListContent({ screen, container, listState, navigate, refreshFeed });

  filtered.forEach((channel) => {
    const row = document.createElement('article');
    row.className = 'channel-row';

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = channel.avatar;

    const main = renderChannelMain(channel, activeTab);

    const controls = document.createElement('div');
    controls.className = 'channel-row-controls';

    const menuButton = document.createElement('button');
    menuButton.type = 'button';
    menuButton.className = 'channel-menu-trigger';
    menuButton.textContent = '…';
    menuButton.addEventListener('click', (event) => {
      event.stopPropagation();
      animatePress(menuButton);

      if (listState.openMenuId === channel.id) {
        closeChannelMenu(listState);
        rerenderList();
        return;
      }

      listState.openMenuId = channel.id;
      openChannelMenu({
        listState,
        channel,
        anchorEl: menuButton,
        refreshFeed: async () => loadFeedAndRender({ screen, listState, contentEl: container, navigate }),
        rerenderList,
      });
      rerenderList();
    });

    const time = document.createElement('span');
    time.className = 'channel-row-time';
    time.textContent = channel.lastMessageAt ? formatRelativeTime(channel.lastMessageAt) : '—';

    const count = document.createElement('span');
    count.className = 'unread channel-row-count';
    count.textContent = String(channel.messagesCount || 0);

    controls.append(menuButton, time, count);

    row.append(avatar, main, controls);
    row.addEventListener('click', () => navigate(channel.route || `channel-view/${channel.id}`));
    list.append(row);
  });

  container.append(list);
}

function updateBottomCta({ button, listState, navigate, onReload }) {
  const tab = listState.activeTab;

  if (tab === 'subscriptions') {
    button.textContent = 'Подписаться на канал';
    button.className = 'primary-btn channels-bottom-action';
    button.onclick = () => openSimpleSubscribeModal({
      kind: 'channel',
      kindLabel: 'Подписка на канал',
      submitLabel: 'Подписаться',
      onSuccess: onReload,
    });
    return;
  }

  if (tab === 'authors') {
    button.textContent = 'Подписаться на автора';
    button.className = 'primary-btn channels-bottom-action';
    button.onclick = () => openSimpleSubscribeModal({
      kind: 'user',
      kindLabel: 'Подписка на автора',
      submitLabel: 'Подписаться',
      onSuccess: onReload,
    });
    return;
  }

  button.textContent = 'Создать канал';
  button.className = 'primary-btn channels-bottom-action';
  button.onclick = () => navigate('add-channel-view');
}

async function loadFeedAndRender({ screen, listState, contentEl, navigate }) {
  closeChannelMenu(listState);
  renderSkeletonList(contentEl, 5);

  try {
    if (!state.session.login) throw new Error('not_authorized');
    const feed = await authService.listSubscriptionsFeed(state.session.login, 200);
    const groups = mapApiFeed(feed, listState.notificationsState);

    listState.channels = toListModel(groups);
    setChannelsFeed(feed, groups.index);

    renderListContent({
      screen,
      container: contentEl,
      listState,
      navigate,
      refreshFeed: async () => loadFeedAndRender({ screen, listState, contentEl, navigate }),
    });
  } catch (error) {
    setChannelsFeed(null, {});
    contentEl.innerHTML = '';

    if (isChannelsDemoMode()) {
      renderDemoFallback(contentEl, navigate, error, () => loadFeedAndRender({ screen, listState, contentEl, navigate }));
      return;
    }

    renderErrorState(contentEl, error, () => loadFeedAndRender({ screen, listState, contentEl, navigate }));
  }
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--list';
  const appScreen = document.getElementById('app-screen');
  appScreen?.classList.add('channels-scroll-clean');

  const createSuccessFlash = pullCreateSuccessFlash();
  const notificationsState = readChannelNotificationsState();

  const listState = {
    activeTab: 'my',
    openMenuId: null,
    notificationsState,
    channels: [],
    menuCleanup: null,
  };

  const tabs = document.createElement('div');
  tabs.className = 'channels-tabs channels-tabs--sticky';

  const contentEl = document.createElement('div');
  contentEl.className = 'channels-list-content';

  const bottomCta = document.createElement('button');
  bottomCta.type = 'button';

  const reloadFeed = async () => loadFeedAndRender({ screen, listState, contentEl, navigate });

  const rerenderList = () => {
    tabItems.forEach((tab) => {
      const btn = tabs.querySelector(`[data-tab="${tab.key}"]`);
      if (btn) btn.classList.toggle('is-active', tab.key === listState.activeTab);
    });

    closeChannelMenu(listState);

    renderListContent({
      screen,
      container: contentEl,
      listState,
      navigate,
      refreshFeed: reloadFeed,
    });

    updateBottomCta({
      button: bottomCta,
      listState,
      navigate,
      onReload: reloadFeed,
    });
  };

  const tabItems = [
    { key: 'my', label: 'Мои' },
    { key: 'subscriptions', label: 'Подписки' },
    { key: 'authors', label: 'Авторы' },
  ];

  tabItems.forEach((tab) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.dataset.tab = tab.key;
    btn.className = `channels-tab-btn ${tab.key === listState.activeTab ? 'is-active' : ''}`;
    btn.textContent = tab.label;
    btn.addEventListener('click', () => {
      if (listState.activeTab === tab.key) return;
      listState.activeTab = tab.key;
      animatePress(btn);
      rerenderList();
    });
    tabs.append(btn);
  });

  screen.append(tabs, contentEl, bottomCta);

  if (createSuccessFlash) {
    showToast(createSuccessFlash);
  }

  updateBottomCta({
    button: bottomCta,
    listState,
    navigate,
    onReload: reloadFeed,
  });

  loadFeedAndRender({ screen, listState, contentEl, navigate });

  screen.cleanup = () => {
    closeChannelMenu(listState);
    appScreen?.classList.remove('channels-scroll-clean');
  };

  return screen;
}
