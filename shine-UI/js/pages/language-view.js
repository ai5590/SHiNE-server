import { renderHeader } from '../components/header.js?v=20260405171816';
import { state } from '../state.js?v=20260405171816';

export const pageMeta = { id: 'language-view', title: 'Язык' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Язык',
      leftAction: { label: '←', onClick: () => navigate('settings-view') },
    }),
  );

  const card = document.createElement('div');
  card.className = 'card stack';
  card.innerHTML = `
    <label class="checkbox-row"><input type="radio" name="language" value="ru" ${state.entrySettings.language === 'ru' ? 'checked' : ''} /> Русский</label>
    <label class="checkbox-row"><input type="radio" name="language" value="en" ${state.entrySettings.language === 'en' ? 'checked' : ''} /> English</label>
  `;

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';
  actions.innerHTML = `
    <button class="primary-btn" type="button" id="language-ok">ОК</button>
    <button class="ghost-btn" type="button" id="language-cancel">Отмена</button>
  `;

  actions.querySelector('#language-ok').addEventListener('click', () => {
    const selected = card.querySelector('input[name="language"]:checked');
    if (selected) {
      state.entrySettings.language = selected.value;
    }
    navigate('settings-view');
  });

  actions.querySelector('#language-cancel').addEventListener('click', () => navigate('settings-view'));

  screen.append(card, actions);
  return screen;
}
