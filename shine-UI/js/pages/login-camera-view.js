import { renderHeader } from '../components/header.js?v=20260406221807';

export const pageMeta = { id: 'login-camera-view', title: 'Войти по камере', showAppChrome: false };

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const frame = document.createElement('div');
  frame.className = 'camera-shell';
  frame.innerHTML = `
    <video class="camera-video" autoplay playsinline muted></video>
    <div class="camera-frame"></div>
    <div class="camera-hint">Наведите QR-код в рамку</div>
  `;

  const video = frame.querySelector('video');
  let stream = null;

  const stopCamera = () => {
    if (stream) {
      stream.getTracks().forEach((track) => track.stop());
      stream = null;
    }
  };

  if (navigator.mediaDevices?.getUserMedia) {
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((nextStream) => {
        stream = nextStream;
        video.srcObject = nextStream;
      })
      .catch(() => {
        frame.insertAdjacentHTML('beforeend', '<div class="camera-error">Не удалось открыть камеру. Проверьте разрешения браузера.</div>');
      });
  } else {
    frame.insertAdjacentHTML('beforeend', '<div class="camera-error">Камера не поддерживается в этом браузере.</div>');
  }

  const backButton = document.createElement('button');
  backButton.className = 'ghost-btn';
  backButton.type = 'button';
  backButton.textContent = 'Назад';
  backButton.addEventListener('click', () => {
    stopCamera();
    navigate('login-view');
  });

  screen.append(
    renderHeader({
      title: 'Войти по камере',
      leftAction: {
        label: '←',
        onClick: () => {
          stopCamera();
          navigate('login-view');
        },
      },
    }),
    frame,
    backButton,
  );

  screen.cleanup = stopCamera;
  return screen;
}
