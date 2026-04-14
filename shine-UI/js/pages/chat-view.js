import { renderHeader } from '../components/header.js';
import { directMessages } from '../mock-data.js';
import { addChatMessage, getChatMessages, authService, state } from '../state.js';

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
      rightActions: [{
        label: 'Позвонить',
        onClick: () => {
          const confirmed = window.confirm('Позвонить этому пользователю?');
          if (!confirmed) return;
          window.alert('Функция пока не реализована');
        },
      }],
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

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = form.elements.message;
    const text = input.value.trim();
    if (!text) return;

    addChatMessage(chatId, text);
    input.value = '';
    renderLog(log, chatId);

    try {
      await authService.sendDirectMessage({
        toLogin: chatId,
        text,
        storagePwd: state.session.storagePwdInMemory,
      });
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
