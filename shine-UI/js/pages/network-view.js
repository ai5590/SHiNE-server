import { renderHeader } from '../components/header.js';
import { authService, state } from '../state.js';

export const pageMeta = { id: 'network-view', title: 'Связи' };

function makeNode(name, cls = '') {
  const n = document.createElement('div');
  n.className = `node ${cls}`.trim();
  n.dataset.nodeLogin = name;
  n.innerHTML = `<div class="node-dot">${(name[0] || '?').toUpperCase()}</div><div class="node-label">${name}</div>`;
  return n;
}

function unique(list) {
  return [...new Set((Array.isArray(list) ? list : []).filter(Boolean))];
}

export function render({ navigate }) {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const board = document.createElement('div');
  board.className = 'network-board';
  board.style.height = 'calc(100dvh - 170px)';

  const note = document.createElement('p');
  note.className = 'meta-muted';
  note.textContent = 'Загрузка связей...';

  let activeMenu = null;
  let centerLogin = state.session.login || '';

  function closeNodeMenu() {
    if (!activeMenu) return;
    activeMenu.remove();
    activeMenu = null;
  }

  function openNodeMenu(node, login) {
    closeNodeMenu();
    const menu = document.createElement('div');
    menu.className = 'node-menu card';
    menu.innerHTML = '<button class="ghost-btn" type="button">Показать информацию о пользователе</button>';

    const rect = node.getBoundingClientRect();
    const boardRect = board.getBoundingClientRect();
    const x = rect.left + rect.width / 2 - boardRect.left;
    const y = rect.bottom - boardRect.top + 8;

    menu.style.left = `${Math.max(8, Math.min(x - 120, boardRect.width - 248))}px`;
    menu.style.top = `${Math.max(8, Math.min(y, boardRect.height - 58))}px`;

    const btn = menu.querySelector('button');
    btn.addEventListener('click', () => {
      navigate(`user-profile-view/${encodeURIComponent(login)}/network-view`);
      closeNodeMenu();
    });

    board.append(menu);
    activeMenu = menu;
  }

  function bindNodeInteraction(node, login, onLongPress) {
    let timerId = 0;
    let startX = 0;
    let startY = 0;
    let longPressTriggered = false;

    const clearTimer = () => {
      if (timerId) {
        window.clearTimeout(timerId);
        timerId = 0;
      }
    };

    node.addEventListener('pointerdown', (event) => {
      if (event.button !== 0) return;
      startX = event.clientX;
      startY = event.clientY;
      longPressTriggered = false;
      clearTimer();
      timerId = window.setTimeout(async () => {
        longPressTriggered = true;
        closeNodeMenu();
        await onLongPress(login);
      }, 500);
    });

    node.addEventListener('pointermove', (event) => {
      if (!timerId) return;
      const dx = Math.abs(event.clientX - startX);
      const dy = Math.abs(event.clientY - startY);
      if (dx > 8 || dy > 8) clearTimer();
    });

    node.addEventListener('pointerleave', clearTimer);
    node.addEventListener('pointercancel', clearTimer);

    node.addEventListener('pointerup', (event) => {
      if (event.button !== 0) return;
      clearTimer();
      if (longPressTriggered) return;
      openNodeMenu(node, login);
    });
  }

  async function load(nextCenterLogin = '') {
    const targetCenter = nextCenterLogin || centerLogin || state.session.login;
    centerLogin = targetCenter;
    closeNodeMenu();
    note.textContent = 'Загрузка связей...';

    try {
      const graph = await authService.getUserConnectionsGraph(targetCenter);
      board.innerHTML = '';

      const center = makeNode(graph.login || targetCenter, 'center');
      center.style.left = '50%';
      center.style.top = '50%';
      board.append(center);

      const all = unique([...(graph.outFriends || []), ...(graph.inFriends || [])]);
      const left = all.slice(0, Math.ceil(all.length / 2));
      const right = all.slice(Math.ceil(all.length / 2));

      const mk = (name, side, idx, total) => {
        const node = makeNode(name);
        const y = 15 + ((idx + 1) * 70) / (Math.max(total, 1) + 1);
        node.style.left = side === 'left' ? '20%' : '80%';
        node.style.top = `${y}%`;
        bindNodeInteraction(node, name, load);
        board.append(node);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', '50');
        line.setAttribute('y1', '50');
        line.setAttribute('x2', side === 'left' ? '20' : '80');
        line.setAttribute('y2', String(y));
        line.setAttribute('stroke', 'rgba(125,170,255,0.6)');
        line.setAttribute('stroke-width', '1.5');
        return line;
      };

      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('class', 'network-svg');
      svg.setAttribute('viewBox', '0 0 100 100');
      svg.setAttribute('preserveAspectRatio', 'none');

      left.forEach((name, i) => svg.append(mk(name, 'left', i, left.length)));
      right.forEach((name, i) => svg.append(mk(name, 'right', i, right.length)));
      board.prepend(svg);

      note.textContent = 'Тап по узлу: информация о пользователе. Долгое нажатие: центрировать граф.';
    } catch (e) {
      note.textContent = `Ошибка загрузки связей: ${e.message || 'unknown'}`;
    }
  }

  const outsideTapHandler = (event) => {
    if (!activeMenu) return;
    if (!(event.target instanceof Node)) return;
    if (activeMenu.contains(event.target)) return;
    closeNodeMenu();
  };
  document.addEventListener('pointerdown', outsideTapHandler, true);
  screen.cleanup = () => {
    document.removeEventListener('pointerdown', outsideTapHandler, true);
  };

  board.addEventListener('pointerdown', (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (target.closest('.node')) return;
    if (target.closest('.node-menu')) return;
    closeNodeMenu();
  });

  load();

  screen.append(renderHeader({ title: 'Связи' }), board, note);
  return screen;
}
