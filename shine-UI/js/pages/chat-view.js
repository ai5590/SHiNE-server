import { renderHeader } from '../components/header.js?v=20260407105357';
import { directMessages } from '../mock-data.js?v=20260407105357';
import { addChatMessage, getChatMessages, authService, state } from '../state.js?v=20260407105357';

export const pageMeta = { id: 'chat-view', title: 'Чат' };

function renderLog(list, chatId) {
  list.innerHTML = '';
  const messages = getChatMessages(chatId);
  messages.forEach((msg) => {
    const bubble = document.createElement('div');
    bubble.className = `bubble ${msg.from}`;
    bubble.textContent = msg.text;
    list.append(bubble);
  });
  list.scrollTop = list.scrollHeight;
}

export function render({ navigate, route }) {
  const chatId = route.params.chatId || 'u1';
  const contact = directMessages.find((d) => d.id === chatId) || {
    id: chatId,
    name: chatId,
    initials: (chatId[0] || '?').toUpperCase(),
  };

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: `Чат: ${contact.name}`,
      leftAction: { label: '←', onClick: () => navigate('messages-list') },
    })
  );

  const isContact = state.contacts.includes(chatId);
  if (!isContact) {
    const warning = document.createElement('div');
    warning.className = 'card stack';
    warning.innerHTML = '<p class="meta-muted">Пользователь не в контактах. Можно писать ему сразу (MVP).</p>';
    const btn = document.createElement('button');
    btn.className = 'primary-btn';
    btn.type = 'button';
    btn.textContent = 'Добавить в контакты';
    btn.addEventListener('click', () => {
      state.contacts = [...state.contacts, chatId];
      warning.remove();
    });
    warning.append(btn);
    screen.append(warning);
  }

  const wrap = document.createElement('div');
  wrap.className = 'chat-wrap';

  const log = document.createElement('div');
  log.className = 'messages-log';

  const form = document.createElement('form');
  form.className = 'chat-input';
  form.innerHTML = `
    <input class="input" type="text" name="message" placeholder="Введите сообщение" maxlength="300" />
    <button class="primary-btn" type="submit">Отправить</button>
  `;

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = form.elements.message;
    const text = input.value.trim();
    if (!text) return;

    addChatMessage(chatId, text);
    input.value = '';
    renderLog(log, chatId);

    try {
      await authService.sendDirectMessage(chatId, text);
    } catch (e) {
      addChatMessage(chatId, `Ошибка отправки: ${e.message || 'unknown'}`);
      renderLog(log, chatId);
    }
  });

  renderLog(log, chatId);
  wrap.append(log, form);
  screen.append(wrap);
  return screen;
}
