import { renderHeader } from '../components/header.js?v=20260406221807';
import { profile } from '../mock-data.js?v=20260406221807';
import { state } from '../state.js?v=20260406221807';

export const pageMeta = { id: 'profile-view', title: 'Профиль' };

export function render({ navigate }) {
  const badgeHelp = {
    official: {
      title: 'Официальный аккаунт',
      text: 'Эта настройка включает или отключает отметку официального аккаунта в профиле. Используйте её, когда нужно показать или скрыть подтверждённый статус.',
    },
    shine: {
      title: 'Сияющий',
      text: 'Этот переключатель включает или отключает режим «Сияющий». Он управляет отображением дополнительного визуального акцента для профиля.',
    },
  };

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Профиль',
      rightActions: [
        { label: 'Кошелёк', onClick: () => navigate('wallet-view') },
        { label: 'Настройки', onClick: () => navigate('settings-view') },
      ],
    })
  );

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <div class="row">
      <div class="avatar large">${profile.avatarInitials}</div>
      <div class="stack" style="justify-items:end; text-align:right;">
        <button class="badge profile-badge-trigger" type="button" data-badge="official">✔ ${profile.badges[0]}</button>
        <button class="badge alt profile-badge-trigger" type="button" data-badge="shine">✨ ${profile.badges[1]}</button>
      </div>
    </div>
    <div>
      <h2 style="font-size:22px; margin-bottom:2px;">${profile.name}</h2>
      <p class="meta-muted">${state.session.login || profile.login}</p>
    </div>
    <div class="stack" style="gap:8px;">
      <div class="card" style="padding:10px;"><span class="meta-muted">Телефон:</span> ${profile.phone}</div>
      <div class="card" style="padding:10px;"><span class="meta-muted">Адрес:</span> ${profile.address}</div>
      <div class="card" style="padding:10px;"><span class="meta-muted">Email:</span> ${profile.email}</div>
      <div class="card" style="padding:10px;"><span class="meta-muted">Соцсети:</span> ${profile.socials}</div>
    </div>
  `;

  const modal = document.createElement('div');
  modal.className = 'profile-help-modal';
  modal.hidden = true;
  modal.innerHTML = `
    <div class="profile-help-backdrop" data-close="true"></div>
    <div class="profile-help-dialog card" role="dialog" aria-modal="true" aria-labelledby="profile-help-title" tabindex="-1">
      <div class="row" style="align-items:flex-start;">
        <div>
          <div class="meta-muted" style="margin-bottom:4px;">Управление функцией</div>
          <h3 id="profile-help-title" style="font-size:18px;"></h3>
        </div>
        <button class="icon-btn profile-help-close" type="button" aria-label="Закрыть">✕</button>
      </div>
      <p class="profile-help-text"></p>
    </div>
  `;

  const titleEl = modal.querySelector('#profile-help-title');
  const textEl = modal.querySelector('.profile-help-text');
  const dialogEl = modal.querySelector('.profile-help-dialog');

  function closeModal() {
    modal.hidden = true;
  }

  function openModal(type) {
    const content = badgeHelp[type];
    if (!content) return;

    titleEl.textContent = content.title;
    textEl.textContent = content.text;
    modal.hidden = false;
    dialogEl.focus();
  }

  card.querySelectorAll('.profile-badge-trigger').forEach((button) => {
    button.addEventListener('click', () => openModal(button.dataset.badge));
  });

  modal.addEventListener('click', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && (target.dataset.close === 'true' || target.classList.contains('profile-help-close'))) {
      closeModal();
    }
  });

  modal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeModal();
    }
  });

  screen.append(card, modal);
  return screen;
}
