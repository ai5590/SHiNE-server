import { renderHeader } from '../components/header.js?v=20260403081123';
import { directMessages } from '../mock-data.js?v=20260403081123';
import { authService, ensureChat, setContacts, state } from '../state.js?v=20260403081123';

export const pageMeta = { id: 'contact-search-view', title: 'Поиск контактов' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const input = document.createElement('input');
  input.className = 'input';
  input.type = 'text';
  input.name = 'contact';
  input.placeholder = 'Введите начало логина';
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
      status.textContent = 'Введите начало логина пользователя.';
      return;
    }

    if (!matches.length) {
      status.textContent = 'Совпадений не найдено.';
      return;
    }

    status.textContent = `Найдено пользователей: ${matches.length}`;

    matches.forEach((login) => {
      const row = document.createElement('article');
      row.className = 'list-item';
      row.innerHTML = `
        <div class="avatar">${(login[0] || '?').toUpperCase()}</div>
        <div>
          <strong>${login}</strong>
          <p class="meta-muted" style="margin-top:4px;">Пользователь сервера</p>
        </div>
        <div class="meta-muted">Профиль</div>
      `;
      resultsList.append(row);
    });
  };

  const searchButton = document.createElement('button');
  searchButton.className = 'primary-btn';
  searchButton.type = 'button';
  searchButton.textContent = 'Поиск';
  searchButton.addEventListener('click', async () => {
    try {
      const logins = await authService.searchUsers(input.value.trim());
      renderResults(logins, input.value);
    } catch (e) {
      status.textContent = `Ошибка поиска: ${e.message || 'unknown'}`;
      resultsCard.hidden = false;
    }
  });

  const addButton = document.createElement('button');
  addButton.className = 'ghost-btn';
  addButton.type = 'button';
  addButton.textContent = 'Открыть чат';
  addButton.addEventListener('click', () => {
    if (!latestMatches.length) {
      status.textContent = 'Сначала выполните поиск.';
      resultsCard.hidden = false;
      return;
    }

    const login = latestMatches[0];
    const exists = directMessages.some((item) => item.id === login);

    if (!exists) {
      directMessages.unshift({
        id: login,
        name: login,
        initials: (login[0] || '?').toUpperCase(),
        lastMessage: 'Диалог создан. Пользователь пока не в контактах.',
        time: 'сейчас',
        unread: 0,
      });
    }

    if (!state.contacts.includes(login)) {
      setContacts([...state.contacts, login]);
    }

    ensureChat(login);
    navigate(`chat-view/${login}`);
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
