import { renderHeader } from '../components/header.js?v=20260406221807';

export const pageMeta = { id: 'login-view', title: 'Войти', showAppChrome: false };

function createQrCode() {
  const svgNS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(svgNS, 'svg');
  svg.setAttribute('viewBox', '0 0 100 100');
  svg.classList.add('qr-code');

  const cells = [
    [6, 6, 22, 22], [72, 6, 22, 22], [6, 72, 22, 22], [14, 14, 6, 6], [80, 14, 6, 6], [14, 80, 6, 6],
    [38, 12, 8, 8], [52, 12, 8, 8], [38, 26, 8, 8], [52, 26, 8, 8], [32, 40, 10, 10], [48, 40, 10, 10],
    [64, 40, 10, 10], [40, 56, 8, 8], [56, 56, 8, 8], [72, 56, 8, 8], [32, 72, 8, 8], [48, 72, 8, 8],
    [64, 72, 8, 8], [48, 86, 8, 8],
  ];

  cells.forEach(([x, y, width, height]) => {
    const rect = document.createElementNS(svgNS, 'rect');
    rect.setAttribute('x', x);
    rect.setAttribute('y', y);
    rect.setAttribute('width', width);
    rect.setAttribute('height', height);
    rect.setAttribute('rx', '2');
    svg.append(rect);
  });

  return svg;
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const qrCard = document.createElement('div');
  qrCard.className = 'card stack qr-card';
  qrCard.append(createQrCode());

  const cameraButton = document.createElement('button');
  cameraButton.className = 'primary-btn';
  cameraButton.type = 'button';
  cameraButton.textContent = 'Войти по камере';
  cameraButton.addEventListener('click', () => navigate('login-camera-view'));

  const loginButton = document.createElement('button');
  loginButton.className = 'ghost-btn';
  loginButton.type = 'button';
  loginButton.textContent = 'Войти по логину';
  loginButton.addEventListener('click', () => navigate('login-password-view'));

  const actions = document.createElement('div');
  actions.className = 'auth-actions';
  actions.append(cameraButton, loginButton);

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => navigate('start-view'));

  screen.append(
    renderHeader({
      title: 'Войти',
      leftAction: { label: '←', onClick: () => navigate('start-view') },
    }),
    qrCard,
    actions,
    backButton,
  );

  return screen;
}
