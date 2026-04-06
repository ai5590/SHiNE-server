import { renderHeader } from '../components/header.js?v=20260406221807';
import { checkServerAvailability, saveEntrySettings, state } from '../state.js?v=20260406221807';

export const pageMeta = { id: 'entry-settings-view', title: 'Настройки входа', showAppChrome: false };

const SERVER_FIELDS = [
  { key: 'solanaServer', label: 'Адрес Solana сервера' },
  { key: 'shineServer', label: 'Адрес сервера Сияние' },
  { key: 'arweaveServer', label: 'Адрес сервера Arweave' },
];

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const draft = {
    language: state.entrySettings.language,
    solanaServer: state.entrySettings.solanaServer,
    shineServer: state.entrySettings.shineServer,
    arweaveServer: state.entrySettings.arweaveServer,
    statuses: { ...state.entrySettings.statuses },
  };

  const timers = new Map();

  const body = document.createElement('div');
  body.className = 'card stack';

  const languageLabel = document.createElement('label');
  languageLabel.className = 'stack';
  languageLabel.innerHTML = `<span class="field-label">Язык</span>`;

  const languageSelect = document.createElement('select');
  languageSelect.className = 'select';
  languageSelect.innerHTML = `
    <option value="ru">Русский</option>
    <option value="en">English</option>
  `;
  languageSelect.value = draft.language;
  languageSelect.addEventListener('change', () => {
    draft.language = languageSelect.value;
  });
  languageLabel.append(languageSelect);

  body.append(languageLabel);

  SERVER_FIELDS.forEach((field) => {
    const block = document.createElement('div');
    block.className = 'stack';

    const title = document.createElement('label');
    title.className = 'field-label';
    title.textContent = field.label;

    const input = document.createElement('input');
    input.className = 'input';
    input.type = 'text';
    input.value = draft[field.key];

    const controls = document.createElement('div');
    controls.className = 'row wrap-row';

    const checkButton = document.createElement('button');
    checkButton.className = 'ghost-btn server-check-btn';
    checkButton.type = 'button';
    checkButton.textContent = 'Проверить';

    const status = document.createElement('span');
    status.className = 'status-line';

    const applyStatus = (value) => {
      draft.statuses[field.key] = value;
      checkButton.classList.remove('is-available', 'is-unavailable');
      status.classList.remove('is-available', 'is-unavailable');

      if (value === 'available') {
        status.textContent = 'Доступен';
        checkButton.classList.add('is-available');
        status.classList.add('is-available');
      } else if (value === 'unavailable') {
        status.textContent = 'Недоступен';
        checkButton.classList.add('is-unavailable');
        status.classList.add('is-unavailable');
      } else {
        status.textContent = 'Статус не проверен';
      }
    };

    const runCheck = () => {
      draft[field.key] = input.value.trim();
      applyStatus(checkServerAvailability(input.value));
    };

    applyStatus(draft.statuses[field.key]);

    checkButton.addEventListener('click', runCheck);
    input.addEventListener('input', () => {
      draft[field.key] = input.value;
      applyStatus('idle');
      window.clearTimeout(timers.get(field.key));
      timers.set(field.key, window.setTimeout(runCheck, 3000));
    });
    input.addEventListener('blur', runCheck);
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        runCheck();
      }
    });

    controls.append(checkButton, status);
    block.append(title, input, controls);
    body.append(block);
  });

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const cancelButton = document.createElement('button');
  cancelButton.className = 'ghost-btn';
  cancelButton.type = 'button';
  cancelButton.textContent = 'Отмена';
  cancelButton.addEventListener('click', () => navigate('start-view'));

  const saveButton = document.createElement('button');
  saveButton.className = 'primary-btn';
  saveButton.type = 'button';
  saveButton.textContent = 'Сохранить';
  saveButton.addEventListener('click', () => {
    saveEntrySettings(draft);
    navigate('start-view');
  });

  actions.append(cancelButton, saveButton);

  const help = document.createElement('button');
  help.className = 'help-fab';
  help.type = 'button';
  help.textContent = '?';
  help.addEventListener('click', () => {
    window.alert(
      'Текст для разработчиков: после ввода адреса любого сервера автопроверка запускается по кнопке "Проверить", после перехода в другое поле или если подождать больше 3 секунд. Зелёный статус означает доступность, красный — недоступность.',
    );
  });

  screen.append(
    renderHeader({
      title: 'Настройки входа',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    body,
    actions,
    help,
  );

  screen.cleanup = () => {
    timers.forEach((timerId) => window.clearTimeout(timerId));
  };

  return screen;
}
