import { renderHeader } from '../components/header.js';
import { authService, state } from '../state.js';
import {
  buildAvatarInitials,
  buildIdentityLines,
  loadRelationsForPair,
  loadUserProfileCard,
} from '../services/user-connections.js';

export const pageMeta = { id: 'user-profile-view', title: 'Чужой профиль' };

function escapeHtml(text) {
  return String(text || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function boolText(flag) {
  return flag ? 'Да' : 'Нет';
}

function relationButtonLabel(kind, flags) {
  if (kind === 'follow') return flags.outFollow ? 'Отписаться' : 'Подписаться';
  if (kind === 'friend') return flags.outFriend ? 'Убрать из друзей' : 'Добавить в друзья';
  return flags.outContact ? 'Убрать из контактов' : 'Добавить в контакты';
}

function relationNextState(kind, flags) {
  if (kind === 'follow') return !flags.outFollow;
  if (kind === 'friend') return !flags.outFriend;
  return !flags.outContact;
}

function relationConfirmLabel(kind) {
  if (kind === 'follow') return 'подписку';
  if (kind === 'friend') return 'дружбу';
  return 'контакт';
}

function renderIdentity(card) {
  const lines = buildIdentityLines({
    login: card.login,
    firstName: card.firstName,
    lastName: card.lastName,
  });

  return `
    <div class="row" style="gap:12px; align-items:center;">
      <div class="avatar large">${escapeHtml(buildAvatarInitials(card))}</div>
      <div class="profile-identity-lines">
        ${lines.map((line, idx) => (
          `<div class="profile-identity-line${idx === lines.length - 1 ? ' profile-identity-login' : ''}">${escapeHtml(line)}</div>`
        )).join('')}
      </div>
    </div>
  `;
}

function renderReadOnlyBadges(card) {
  return `
    <div class="row wrap-row">
      <span class="badge ${card.official ? 'is-yes-official' : 'is-no'}">Официальный: ${card.official ? 'Yes' : 'No'}</span>
      <span class="badge ${card.shine ? 'is-yes-shine' : 'is-no'}">Сияющий: ${card.shine ? 'Yes' : 'No'}</span>
    </div>
  `;
}

function renderRelations(flags) {
  return `
    <div class="card stack user-relations-list">
      <div class="user-rel-row"><span>Вы подписаны:</span><strong>${boolText(flags.outFollow)}</strong></div>
      <div class="user-rel-row"><span>Подписан на вас:</span><strong>${boolText(flags.inFollow)}</strong></div>
      <div class="user-rel-row"><span>Вы добавили в друзья:</span><strong>${boolText(flags.outFriend)}</strong></div>
      <div class="user-rel-row"><span>Добавил вас в друзья:</span><strong>${boolText(flags.inFriend)}</strong></div>
      <div class="user-rel-row"><span>Вы добавили в контакты:</span><strong>${boolText(flags.outContact)}</strong></div>
      <div class="user-rel-row"><span>Добавил вас в контакты:</span><strong>${boolText(flags.inContact)}</strong></div>
    </div>
  `;
}

function renderReadOnlyParams(card) {
  const rows = [
    { label: 'Имя', value: card.firstName },
    { label: 'Фамилия', value: card.lastName },
    { label: 'Адрес', value: card.address },
    { label: 'Web', value: card.web },
    { label: 'Телефон', value: card.phone },
  ];

  return `
    <div class="card stack profile-param-list">
      ${rows.map((row) => `
        <div class="card profile-param-item row">
          <div class="profile-param-value"><b>${row.label}</b>: ${escapeHtml(String(row.value || '').trim() || 'не заполнено')}</div>
        </div>
      `).join('')}
    </div>
  `;
}

export function render({ navigate, route }) {
  const requestedLogin = String(route.params.login || '').trim();
  const fromPage = String(route.params.fromPage || 'messages-list').trim() || 'messages-list';
  const sessionLogin = String(state.session.login || '').trim();

  const screen = document.createElement('section');
  screen.className = 'stack';

  const status = document.createElement('div');
  status.className = 'status-line';
  status.textContent = 'Загрузка профиля...';

  const body = document.createElement('div');
  body.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Профиль пользователя',
      leftAction: { label: '←', onClick: () => navigate(fromPage) },
      rightActions: [{ label: 'Обновить', onClick: () => refresh() }],
    }),
    status,
    body,
  );

  let currentCard = null;
  let currentFlags = null;
  let isBusy = false;

  function syncActionButtons() {
    const followBtn = body.querySelector('[data-relation-action="follow"]');
    const friendBtn = body.querySelector('[data-relation-action="friend"]');
    const contactBtn = body.querySelector('[data-relation-action="contact"]');
    if (!followBtn || !friendBtn || !contactBtn || !currentFlags) return;
    const isSelf = currentCard && currentCard.login.toLowerCase() === sessionLogin.toLowerCase();
    followBtn.textContent = relationButtonLabel('follow', currentFlags);
    friendBtn.textContent = relationButtonLabel('friend', currentFlags);
    contactBtn.textContent = relationButtonLabel('contact', currentFlags);
    followBtn.disabled = Boolean(isSelf);
    friendBtn.disabled = Boolean(isSelf);
    contactBtn.disabled = Boolean(isSelf);
  }

  async function refresh() {
    if (!requestedLogin) {
      status.className = 'status-line is-unavailable';
      status.textContent = 'Не передан login пользователя.';
      return;
    }

    isBusy = true;
    status.className = 'status-line';
    status.textContent = 'Загрузка профиля...';

    try {
      const card = await loadUserProfileCard(requestedLogin);
      const flags = await loadRelationsForPair({
        currentLogin: sessionLogin,
        targetLogin: card.login,
      });

      currentCard = card;
      currentFlags = flags;

      body.innerHTML = `
        <div class="card stack">
          ${renderIdentity(card)}
        </div>
        ${renderReadOnlyBadges(card)}
        ${renderRelations(flags)}
        ${renderReadOnlyParams(card)}
        <div class="stack">
          <button class="primary-btn" type="button" data-relation-action="follow"></button>
          <button class="ghost-btn" type="button" data-relation-action="friend"></button>
          <button class="ghost-btn" type="button" data-relation-action="contact"></button>
        </div>
      `;

      syncActionButtons();
      status.className = 'status-line is-available';
      status.textContent = 'Профиль обновлён.';
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Ошибка загрузки профиля: ${error.message || 'unknown'}`;
      window.alert(`Не удалось загрузить профиль: ${error.message || 'unknown'}`);
    } finally {
      isBusy = false;
    }
  }

  async function onRelationAction(kind) {
    if (isBusy || !currentCard || !currentFlags) return;
    if (!sessionLogin) {
      window.alert('Для изменения связей нужен активный вход.');
      return;
    }
    if (!state.session.storagePwdInMemory) {
      window.alert('Нет storagePwd в памяти сессии. Выполните вход заново.');
      return;
    }

    const nextEnabled = relationNextState(kind, currentFlags);
    const confirmed = window.confirm(
      `Изменить ${relationConfirmLabel(kind)} с пользователем ${currentCard.login}?\n` +
      'Будет отправлен AddBlock CONNECTION.',
    );
    if (!confirmed) return;

    isBusy = true;
    status.className = 'status-line';
    status.textContent = 'Сохранение отношения в блокчейн...';

    try {
      await authService.setUserRelation({
        login: sessionLogin,
        toLogin: currentCard.login,
        kind,
        enabled: nextEnabled,
        storagePwd: state.session.storagePwdInMemory,
      });
      await refresh();
    } catch (error) {
      status.className = 'status-line is-unavailable';
      status.textContent = `Ошибка изменения связи: ${error.message || 'unknown'}`;
      window.alert(`Не удалось изменить связь: ${error.message || 'unknown'}`);
      isBusy = false;
    }
  }

  body.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const kind = target.dataset.relationAction;
    if (!kind) return;
    onRelationAction(kind);
  });

  refresh();
  return screen;
}
