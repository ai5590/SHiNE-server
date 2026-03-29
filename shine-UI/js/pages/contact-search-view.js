import { renderHeader } from '../components/header.js?v=20260327192619';
import { contactDirectory, directMessages } from '../mock-data.js?v=20260327192619';
import { ensureChat } from '../state.js?v=20260327192619';

export const pageMeta = { id: 'contact-search-view', title: 'Поиск контактов' };

function getMatches(query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return [];

  return contactDirectory
    .filter((contact) => contact.name.toLowerCase().startsWith(normalized))
    .slice(0, 5);
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const input = document.createElement('input');
  input.className = 'input';
  input.type = 'text';
  input.name = 'contact';
  input.placeholder = 'Введите имя контакта';
  input.autocomplete = 'off';
  input.maxLength = 80;

  const resultsCard = document.createElement('section');
  resultsCard.className = 'card stack';
  resultsCard.hidden = true;

  const status = document.createElement('p');
  status.className = 'meta-muted';

  const resultsList = document.createElement('div');
  resultsList.className = 'stack';

  let latestMatches = [];

  const renderResults = (matches, query) => {
    latestMatches = matches;
    resultsList.innerHTML = '';
    resultsCard.hidden = false;

    if (!query.trim()) {
      status.textContent = 'Введите первые буквы имени, чтобы найти контакт.';
      return;
    }

    if (!matches.length) {
      status.textContent = 'Совпадений не найдено.';
      return;
    }

    status.textContent = `Найдено пользователей: ${matches.length}`;

    matches.forEach((contact) => {
      const row = document.createElement('article');
      row.className = 'list-item';
      row.innerHTML = `
        <div class="avatar">${contact.initials}</div>
        <div>
          <strong>${contact.name}</strong>
          <p class="meta-muted" style="margin-top:4px;">${contact.about}</p>
        </div>
        <div class="meta-muted">Контакт</div>
      `;
      resultsList.append(row);
    });
  };

  const searchButton = document.createElement('button');
  searchButton.className = 'primary-btn';
  searchButton.type = 'button';
  searchButton.textContent = 'Найти';
  searchButton.addEventListener('click', () => {
    renderResults(getMatches(input.value), input.value);
  });

  const addButton = document.createElement('button');
  addButton.className = 'ghost-btn';
  addButton.type = 'button';
  addButton.textContent = 'Добавить';
  addButton.addEventListener('click', () => {
    if (!latestMatches.length) {
      status.textContent = 'Сначала выполните поиск, чтобы добавить контакт.';
      resultsCard.hidden = false;
      return;
    }

    const contact = latestMatches[0];
    const exists = directMessages.some((item) => item.id === contact.id);

    if (!exists) {
      directMessages.unshift({
        id: contact.id,
        name: contact.name,
        initials: contact.initials,
        lastMessage: 'Новый контакт добавлен. Можно начинать диалог.',
        time: 'сейчас',
        unread: 0,
      });
    }

    ensureChat(contact.id);
    navigate(`chat-view/${contact.id}`);
  });

  const controls = document.createElement('div');
  controls.className = 'contact-search-actions';
  controls.append(searchButton, addButton);

  const formCard = document.createElement('section');
  formCard.className = 'card stack';
  formCard.append(input, controls);

  resultsCard.append(status, resultsList);

  screen.append(
    renderHeader({
      title: 'Поиск контактов',
      leftAction: { label: '←', onClick: () => navigate('messages-list') },
    }),
    formCard,
    resultsCard,
  );

  return screen;
}
