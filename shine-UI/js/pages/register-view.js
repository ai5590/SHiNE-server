import { renderHeader } from '../components/header.js?v=20260327192619';
import { state } from '../state.js?v=20260327192619';

export const pageMeta = { id: 'register-view', title: 'Зарегистрироваться', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const form = document.createElement('div');
  form.className = 'card stack';

  const loginInput = document.createElement('input');
  loginInput.className = 'input';
  loginInput.type = 'text';
  loginInput.value = state.registrationDraft.login;
  loginInput.placeholder = 'Введите логин';

  const passwordInput = document.createElement('input');
  passwordInput.className = 'input';
  passwordInput.type = 'password';
  passwordInput.value = state.registrationDraft.password;
  passwordInput.placeholder = 'Введите пароль';

  const advanced = document.createElement('label');
  advanced.className = 'checkbox-row';
  advanced.innerHTML = `<input type="checkbox" /> <span>Расширенные настройки</span>`;
  const advancedInput = advanced.querySelector('input');
  advancedInput.addEventListener('change', () => {
    if (advancedInput.checked) {
      window.alert('Расширенные настройки в стартовой версии не работают и не будут работать.');
      advancedInput.checked = false;
    }
  });

  form.innerHTML = `
    <label class="stack"><span class="field-label">Логин</span></label>
    <label class="stack"><span class="field-label">Пароль</span></label>
  `;
  form.children[0].append(loginInput);
  form.children[1].append(passwordInput);
  form.append(advanced);

  const actions = document.createElement('div');
  actions.className = 'auth-footer-actions';

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('start-view'));

  const nextButton = document.createElement('button');
  nextButton.className = 'primary-btn';
  nextButton.type = 'button';
  nextButton.textContent = 'Далее';
  nextButton.addEventListener('click', () => {
    state.registrationDraft.login = loginInput.value;
    state.registrationDraft.password = passwordInput.value;
    navigate('registration-payment-view');
  });

  actions.append(backButton, nextButton);

  screen.append(
    renderHeader({
      title: 'Зарегистрироваться',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    form,
    actions,
  );

  return screen;
}
