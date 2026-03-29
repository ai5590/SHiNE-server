import { renderHeader } from '../components/header.js?v=20260327192619';
import { networkGraph } from '../mock-data.js?v=20260327192619';

export const pageMeta = { id: 'network-view', title: 'Связи' };

function toPoint(v) {
  return `${v.x}%`;
}

function showHelpModal() {
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal" id="network-help-modal">
      <div class="modal-card stack">
        <h3 style="font-size:18px;">Справка по схеме связей</h3>
        <p class="meta-muted">В центре находишься ты.</p>
        <p class="meta-muted">Рядом показаны друзья первого уровня.</p>
        <p class="meta-muted">Далее могут существовать друзья второго уровня.</p>
        <p class="meta-muted">При одном нажатии на узел можно показать его связи.</p>
        <p class="meta-muted">При двойном нажатии узел может переместиться в центр.</p>
        <p class="meta-muted">При долгом удержании может открываться меню действий.</p>
        <p class="meta-muted">Логика схемы строится на одном запросе связей пользователя, дальше дерево достраивается на его основе.</p>
        <button class="primary-btn" id="close-network-help">Понятно</button>
      </div>
    </div>
  `;

  root.querySelector('#close-network-help').addEventListener('click', () => {
    root.innerHTML = '';
  });
}

export function render() {
  const screen = document.createElement('section');
  screen.className = 'stack';

  const header = renderHeader({
    title: 'Связи',
    rightActions: [{ label: 'Справка', onClick: showHelpModal }],
  });

  const board = document.createElement('div');
  board.className = 'network-board';

  const lines = networkGraph.peers
    .map(
      (peer) =>
        `<line x1="${toPoint(networkGraph.center)}" y1="${networkGraph.center.y}%" x2="${peer.x}%" y2="${peer.y}%" stroke="rgba(125,170,255,0.55)" stroke-width="1.5"/>`
    )
    .join('');

  board.innerHTML = `<svg class="network-svg" viewBox="0 0 100 100" preserveAspectRatio="none">${lines}</svg>`;

  const centerNode = document.createElement('div');
  centerNode.className = 'node center';
  centerNode.style.left = `${networkGraph.center.x}%`;
  centerNode.style.top = `${networkGraph.center.y}%`;
  centerNode.innerHTML = `<div class="node-dot">${networkGraph.center.initials}</div><div class="node-label">${networkGraph.center.name}</div>`;

  board.append(centerNode);

  networkGraph.peers.forEach((peer) => {
    const node = document.createElement('div');
    node.className = 'node';
    node.style.left = `${peer.x}%`;
    node.style.top = `${peer.y}%`;
    node.innerHTML = `<div class="node-dot">${peer.initials}</div><div class="node-label">${peer.name}</div>`;
    board.append(node);
  });

  const note = document.createElement('p');
  note.className = 'meta-muted';
  note.textContent = 'Схема статичная для демо, архитектура подготовлена под дальнейшую интерактивность.';

  screen.append(header, board, note);
  return screen;
}
