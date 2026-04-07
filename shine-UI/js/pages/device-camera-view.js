import { renderHeader } from '../components/header.js?v=20260407105357';

export const pageMeta = { id: 'device-camera-view', title: 'Подключить через камеру' };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  screen.append(
    renderHeader({
      title: 'Подключить через камеру',
      leftAction: { label: '←', onClick: () => navigate('connect-device-view') },
    }),
  );

  const frame = document.createElement('div');
  frame.className = 'camera-shell';
  frame.innerHTML = `
    <div class="camera-placeholder">Область камеры (демо-заглушка)</div>
    <div class="camera-frame"></div>
    <div class="camera-hint">Логика сканирования пока не реализована</div>
  `;

  screen.append(frame);
  return screen;
}
