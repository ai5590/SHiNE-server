import { renderHeader } from '../components/header.js';
import { channels as mockChannels } from '../mock-data.js';
import { authService, setChannelsFeed, state } from '../state.js';
import { toUserMessage } from '../services/ui-error-texts.js';

export const pageMeta = { id: 'channels-list', title: 'Каналы' };
const CREATE_CHANNEL_FLASH_KEY = 'shine-channels-create-success';

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

function buildChannelRouteFromSummary(summary, fallbackId) {
  const ownerBch = summary?.channel?.ownerBlockchainName;
  const rootBlockNumber = summary?.channel?.channelRoot?.blockNumber;
  const rootBlockHash = normalizeHash(summary?.channel?.channelRoot?.blockHash);
  if (!ownerBch || rootBlockNumber == null) return `channel-view/${fallbackId}`;
  return `channel-view/${encodeRoutePart(ownerBch)}/${Number(rootBlockNumber)}/${rootBlockHash}`;
}

function initialsFromName(name = '') {
  const parts = name.split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] || '#') + (parts[1]?.[0] || '');
}

function allFeedSummaries() {
  const feed = state.channelsFeed || {};
  return [
    ...(feed.ownedChannels || []),
    ...(feed.followedUsersChannels || []),
    ...(feed.followedChannels || []),
  ];
}

function resolveChannelTargetFromInput(rawInput) {
  const input = String(rawInput || '').trim();
  if (!input) return null;

  const bySelector = input.match(/^([A-Za-z0-9._-]+-\d+)\s*[:/]\s*(\d+)\s*[:/]\s*([A-Fa-f0-9]{1,64})$/);
  if (bySelector) {
    return {
      ownerBlockchainName: bySelector[1],
      rootBlockNumber: Number(bySelector[2]),
      rootBlockHash: normalizeHash(bySelector[3]),
    };
  }

  const summaries = allFeedSummaries();

  const byOwnerAndName = input.match(/^@?([^/#\s]+)\s*\/\s*#?(.+)$/);
  if (byOwnerAndName) {
    const owner = byOwnerAndName[1].trim().toLowerCase();
    const channelName = byOwnerAndName[2].trim().toLowerCase();
    const match = summaries.find((summary) => (
      String(summary?.channel?.ownerLogin || '').toLowerCase() === owner
      && String(summary?.channel?.channelName || '').toLowerCase() === channelName
    ));
    if (!match) return null;
    return {
      ownerBlockchainName: match.channel?.ownerBlockchainName,
      rootBlockNumber: Number(match.channel?.channelRoot?.blockNumber),
      rootBlockHash: normalizeHash(match.channel?.channelRoot?.blockHash),
    };
  }

  const byNameOnly = input.replace(/^#/, '').trim().toLowerCase();
  if (!byNameOnly) return null;

  const matches = summaries.filter((summary) => (
    String(summary?.channel?.channelName || '').toLowerCase() === byNameOnly
  ));

  if (matches.length !== 1) return null;

  return {
    ownerBlockchainName: matches[0].channel?.ownerBlockchainName,
    rootBlockNumber: Number(matches[0].channel?.channelRoot?.blockNumber),
    rootBlockHash: normalizeHash(matches[0].channel?.channelRoot?.blockHash),
  };
}

function openSimpleSubscribeModal({ kind, kindLabel, submitLabel, unfollow = false, onSuccess }) {
  const targetHint = kind === 'channel'
    ? '<p class="meta-muted">Цель канала: owner/channel или bch:number:hash.</p>'
    : '<p class="meta-muted">Цель пользователя: @login.</p>';
  const submitText = submitLabel || (unfollow ? 'Отписаться' : 'Подписаться');
  const placeholder = kind === 'channel' ? '@owner/#channel или bch:number:hash' : '@login';

  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="channels-subscribe-modal">
      <div class="modal-card stack">
        <h3 class="modal-title">${kindLabel}</h3>
        ${targetHint}
        <label class="meta-muted" for="subscribe-input">Идентификатор</label>
        <input id="subscribe-input" class="input" placeholder="${placeholder}" />
        <div id="subscribe-error" class="meta-muted inline-error"></div>
        <div class="form-actions-grid">
          <button class="secondary-btn" id="sub-cancel" type="button">Отмена</button>
          <button class="primary-btn" id="sub-submit" type="button">${submitText}</button>
        </div>
      </div>
    </div>
  `;

  const inputEl = root.querySelector('#subscribe-input');
  const errorEl = root.querySelector('#subscribe-error');
  const submitEl = root.querySelector('#sub-submit');

  const close = () => {
    root.innerHTML = '';
  };

  root.querySelector('#sub-cancel').addEventListener('click', close);

  submitEl.addEventListener('click', async () => {
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

    submitEl.disabled = true;
    errorEl.textContent = '';

    try {
      if (kind === 'user') {
        await authService.addBlockFollowUser({
          login,
          targetLogin: value.replace(/^@+/, ''),
          storagePwd,
          unfollow,
        });
      } else if (kind === 'channel') {
        const target = resolveChannelTargetFromInput(value);
        if (!target?.ownerBlockchainName || !Number.isFinite(target.rootBlockNumber)) {
          throw new Error('Канал не найден. Используйте owner/channel или bch:number:hash.');
        }

        await authService.addBlockFollowChannel({
          login,
          storagePwd,
          targetBlockchainName: target.ownerBlockchainName,
          targetBlockNumber: target.rootBlockNumber,
          targetBlockHashHex: target.rootBlockHash,
          unfollow,
        });
      } else {
        throw new Error('Неподдерживаемый тип подписки');
      }

      close();
      if (typeof onSuccess === 'function') onSuccess();
    } catch (error) {
      errorEl.textContent = toUserMessage(error, `${submitText} не удалось.`);
      submitEl.disabled = false;
    }
  });

  if (inputEl) inputEl.focus();
}

function mapMockGroups() {
  const mapRow = (channel) => ({
    ...channel,
    route: `channel-view/${channel.id}`,
  });

  const ownChannels = mockChannels
    .filter((channel) => channel.kind === 'own-personal' || channel.kind === 'own')
    .map(mapRow);
  const followedUserChannels = mockChannels
    .filter((channel) => channel.kind === 'followed-user-channel')
    .map(mapRow);
  const subscribedChannels = mockChannels
    .filter((channel) => channel.kind === 'subscribed')
    .map(mapRow);

  return { ownChannels, followedUserChannels, subscribedChannels, index: {} };
}

function mapApiChannelRow(summary, bucketKey, idx, index) {
  const rowId = `${bucketKey}-${idx}`;
  index[rowId] = summary;
  const ownerLogin = summary.channel?.ownerLogin || 'неизвестно';
  const channelName = summary.channel?.channelName || '(без названия)';
  const displayName = `${ownerLogin}/${channelName}`;

  return {
    id: rowId,
    source: 'api',
    route: buildChannelRouteFromSummary(summary, rowId),
    ownerName: ownerLogin,
    initials: initialsFromName(channelName || ownerLogin || '?'),
    name: channelName,
    displayName,
    description: `bch=${summary.channel?.ownerBlockchainName || '-'}`,
    lastMessage: summary.lastMessage?.text || 'Сообщений пока нет',
    time: summary.lastMessage?.createdAtMs ? new Date(summary.lastMessage.createdAtMs).toLocaleString('ru-RU') : '-',
    messagesCount: summary.messagesCount || 0,
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

function mapApiFeed(feed) {
  const index = {};

  const ownChannels = (feed?.ownedChannels || []).map((it, idx) => mapApiChannelRow(it, 'own', idx, index));
  const followedUserChannels = (feed?.followedUsersChannels || []).map((it, idx) => mapApiChannelRow(it, 'followedUsers', idx, index));
  const subscribedChannels = (feed?.followedChannels || []).map((it, idx) => mapApiChannelRow(it, 'followedChannels', idx, index));

  ownChannels.sort((a, b) => {
    const ap = index[a.id]?.channel?.personal === true;
    const bp = index[b.id]?.channel?.personal === true;
    if (ap && !bp) return -1;
    if (!ap && bp) return 1;
    return a.name.localeCompare(b.name, 'ru');
  });

  return { ownChannels, followedUserChannels, subscribedChannels, index };
}

function renderChannelRow(channel, navigate) {
  const row = document.createElement('article');
  row.className = 'channel-row';
  row.innerHTML = `
    <div class="avatar">${channel.initials}</div>
    <div class="channel-row-main">
      <strong class="channel-row-title">${channel.displayName || channel.name}</strong>
      <p class="channel-row-description">${channel.description}</p>
      <p class="channel-row-message">${channel.lastMessage}</p>
      <p class="channel-row-owner">Владелец: ${channel.ownerName}</p>
    </div>
    <div class="channel-row-meta">
      <span class="channel-row-kind">Канал</span>
      <span class="channel-row-time">${channel.time}</span>
      <span class="unread channel-row-count">${channel.messagesCount}</span>
    </div>
  `;
  row.addEventListener('click', () => navigate(channel.route || `channel-view/${channel.id}`));
  return row;
}

function renderSection(title, items, navigate) {
  const wrap = document.createElement('section');
  wrap.className = 'stack channels-section';

  const header = document.createElement('h3');
  header.className = 'section-title';
  header.textContent = title;

  wrap.append(header);
  if (!items.length) {
    const empty = document.createElement('div');
    empty.className = 'channels-list-empty';
    empty.textContent = 'Пока пусто.';
    wrap.append(empty);
    return wrap;
  }

  items.forEach((channel) => wrap.append(renderChannelRow(channel, navigate)));

  return wrap;
}

function renderGroupedList(container, navigate, groups) {
  const listWrap = document.createElement('div');
  listWrap.className = 'channels-scroll-wrap';

  const list = document.createElement('div');
  list.className = 'stack channels-groups';

  list.append(renderSection('Мои каналы', groups.ownChannels, navigate));

  const dividerOne = document.createElement('hr');
  dividerOne.className = 'channels-divider';
  list.append(dividerOne);

  list.append(renderSection('Каналы пользователей, на которых я подписан', groups.followedUserChannels, navigate));

  const dividerTwo = document.createElement('hr');
  dividerTwo.className = 'channels-divider';
  list.append(dividerTwo);

  list.append(renderSection('Каналы, на которые я подписан', groups.subscribedChannels, navigate));

  const addChannelButton = document.createElement('button');
  addChannelButton.className = 'primary-btn channels-bottom-action';
  addChannelButton.textContent = 'Создать канал';
  addChannelButton.addEventListener('click', () => navigate('add-channel-view'));

  list.append(addChannelButton);

  const scrollHint = document.createElement('div');
  scrollHint.className = 'channels-scroll-hint';

  listWrap.append(list, scrollHint);
  container.append(listWrap);
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
  renderGroupedList(container, navigate, mapMockGroups());
}

async function loadFeedAndRender(container, navigate) {
  container.innerHTML = '';

  const status = document.createElement('div');
  status.className = 'card meta-muted';
  status.textContent = 'Загрузка каналов...';
  container.append(status);

  try {
    if (!state.session.login) throw new Error('not_authorized');
    const feed = await authService.listSubscriptionsFeed(state.session.login, 200);
    const groups = mapApiFeed(feed);
    setChannelsFeed(feed, groups.index);

    container.innerHTML = '';
    renderGroupedList(container, navigate, groups);
  } catch (error) {
    setChannelsFeed(null, {});
    container.innerHTML = '';
    if (isChannelsDemoMode()) {
      renderDemoFallback(container, navigate, error, () => loadFeedAndRender(container, navigate));
      return;
    }
    renderErrorState(container, error, () => loadFeedAndRender(container, navigate));
  }
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack channels-screen channels-screen--list';
  const createSuccessFlash = pullCreateSuccessFlash();
  const hero = document.createElement('div');
  hero.className = 'card channels-hero';
  hero.innerHTML = `
    <div class="channels-hero-emblem" aria-hidden="true"></div>
    <div class="channels-hero-copy">
      <p class="channels-hero-kicker">SHiNE</p>
      <p class="channels-hero-title">Каналы</p>
      <p class="channels-hero-subtitle">Ленты, треды и подписки в одном экране.</p>
    </div>
  `;

  const currentUser = document.createElement('div');
  currentUser.className = 'card channels-user-chip';
  currentUser.textContent = `Вы вошли как @${state.session.login || 'неизвестно'}`;

  let flashCard = null;
  if (createSuccessFlash) {
    flashCard = document.createElement('div');
    flashCard.className = 'card status-line is-available';
    flashCard.textContent = createSuccessFlash;
  }

  const help = document.createElement('div');
  help.className = 'card stack channels-help-card';
  help.innerHTML = `
    <strong>Быстрый ручной тест</strong>
    <p class="meta-muted">
      1) Создайте пользователей A и B.<br />
      2) Под A создайте 2 канала и сообщения.<br />
      3) Под B проверьте follow/unfollow user и channel.<br />
      4) Откройте канал: проверьте like/unlike, reply и thread.
    </p>
  `;

  const content = document.createElement('div');
  const refresh = () => {
    loadFeedAndRender(content, navigate);
  };

  screen.append(
    renderHeader({
      title: 'Каналы',
    })
  );

  const actions = document.createElement('div');
  actions.className = 'channels-action-grid';

  const actionButtons = [
    {
      label: 'Подписаться на пользователя',
      className: 'primary-btn channels-action-btn',
      onClick: () => openSimpleSubscribeModal({
        kind: 'user',
        kindLabel: 'Подписка на пользователя',
        submitLabel: 'Подписаться',
        onSuccess: refresh,
      }),
    },
    {
      label: 'Отписаться от пользователя',
      className: 'destructive-btn channels-action-btn',
      onClick: () => openSimpleSubscribeModal({
        kind: 'user',
        kindLabel: 'Отписка от пользователя',
        submitLabel: 'Отписаться',
        unfollow: true,
        onSuccess: refresh,
      }),
    },
    {
      label: 'Подписаться на канал',
      className: 'primary-btn channels-action-btn',
      onClick: () => openSimpleSubscribeModal({
        kind: 'channel',
        kindLabel: 'Подписка на канал',
        submitLabel: 'Подписаться',
        onSuccess: refresh,
      }),
    },
    {
      label: 'Отписаться от канала',
      className: 'destructive-btn channels-action-btn',
      onClick: () => openSimpleSubscribeModal({
        kind: 'channel',
        kindLabel: 'Отписка от канала',
        submitLabel: 'Отписаться',
        unfollow: true,
        onSuccess: refresh,
      }),
    },
  ];

  actionButtons.forEach((config) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = config.className;
    btn.textContent = config.label;
    btn.addEventListener('click', config.onClick);
    actions.append(btn);
  });

  const limitations = document.createElement('div');
  limitations.className = 'channels-info-strip';
  limitations.textContent = 'Подписка на пользователя и подписка на конкретный канал работают независимо.';

  screen.append(hero);
  screen.append(actions);
  screen.append(currentUser);
  if (flashCard) screen.append(flashCard);
  screen.append(help);
  screen.append(limitations);
  screen.append(content);
  refresh();
  return screen;
}

