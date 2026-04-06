import { renderHeader } from '../components/header.js?v=20260405171816';

export const pageMeta = { id: 'add-channel-view', title: 'Добавить канал' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Добавить канал',
      leftAction: { label: '←', onClick: () => navigate('channels-list') },
    })
  );

  const form = document.createElement('form');
  form.className = 'card stack';
  form.innerHTML = `
    <label for="channel-name">Имя канала</label>
    <input id="channel-name" class="input" maxlength="64" placeholder="Например: Новости команды" required />
    <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
      <button type="button" class="secondary-btn" id="cancel-create-channel">Отмена</button>
      <button type="submit" class="primary-btn">Создать</button>
    </div>
  `;

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    navigate('channels-list');
  });

  form.querySelector('#cancel-create-channel').addEventListener('click', () => {
    navigate('channels-list');
  });

  screen.append(form);
  return screen;
}
