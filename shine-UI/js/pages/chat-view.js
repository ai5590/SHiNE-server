import { renderHeader } from '../components/header.js';
import { directMessages } from '../mock-data.js';
import { addAppLogEntry, addChatMessage, getChatMessages, authService, state } from '../state.js';
import { startOutgoingCall, hangupActiveCall } from '../services/call-service.js';

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
        onClick: async () => {
          const confirmed = window.confirm('Позвонить этому пользователю?');
          if (!confirmed) return;
          try {
            await startOutgoingCall(chatId);
            renderLog(log, chatId);
          } catch (e) {
            addChatMessage(chatId, `[call] Ошибка звонка: ${e.message || 'unknown'}`);
            renderLog(log, chatId);
          }
        },
      }, {
        label: 'Сброс',
        onClick: async () => {
          try {
            await hangupActiveCall();
            renderLog(log, chatId);
          } catch (e) {
            addChatMessage(chatId, `[call] Ошибка сброса: ${e.message || 'unknown'}`);
            renderLog(log, chatId);
          }
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
      const result = await authService.sendDirectMessage({
        login: state.session.login,
        toLogin: chatId,
        text,
        storagePwd: state.session.storagePwdInMemory,
      });
      addAppLogEntry({
        level: 'info',
        source: 'outgoing-dm',
        message: `Сообщение отправлено для ${chatId}`,
        details: {
          toLogin: chatId,
          messageId: result?.messageId || '',
          deliveredWsSessions: Number(result?.deliveredWsSessions || 0),
          deliveredWebPushSessions: Number(result?.deliveredWebPushSessions || 0),
          sessionNotFound: Boolean(result?.sessionNotFound),
        },
      });
    } catch (e) {
      addChatMessage(chatId, `Ошибка отправки: ${e.message || 'unknown'}`);
      addAppLogEntry({
        level: 'warn',
        source: 'outgoing-dm',
        message: 'Ошибка отправки личного сообщения',
        details: {
          toLogin: chatId,
          error: e?.message || 'unknown',
        },
      });
      renderLog(log, chatId);
    }
  });

  renderLog(log, chatId);
  wrap.append(log, form);
  screen.append(wrap);
  return screen;
}
