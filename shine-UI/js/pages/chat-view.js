import { renderHeader } from '../components/header.js?v=20260327192619';
import { directMessages } from '../mock-data.js?v=20260327192619';
import { addChatMessage, getChatMessages } from '../state.js?v=20260327192619';

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
  const contact = directMessages.find((d) => d.id === chatId) || directMessages[0];

  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: `Чат: ${contact.name}`,
      leftAction: { label: '←', onClick: () => navigate('messages-list') },
    })
  );

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

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const input = form.elements.message;
    addChatMessage(chatId, input.value);
    input.value = '';
    renderLog(log, chatId);
  });

  renderLog(log, chatId);
  wrap.append(log, form);
  screen.append(wrap);
  return screen;
}
