export function renderHeader({ title, leftAction, rightActions = [] }) {
  const wrap = document.createElement('header');
  wrap.className = 'page-header';

  const left = document.createElement('div');
  left.className = 'header-left';
  if (leftAction) {
    const btn = document.createElement('button');
    btn.className = 'icon-btn';
    btn.textContent = leftAction.label;
    btn.addEventListener('click', leftAction.onClick);
    left.append(btn);
  }

  const h1 = document.createElement('h1');
  h1.className = 'page-title';
  h1.textContent = title;

  const right = document.createElement('div');
  right.className = 'header-actions';
  rightActions.forEach((action) => {
    const btn = document.createElement('button');
    btn.className = 'icon-btn';
    btn.textContent = action.label;
    btn.addEventListener('click', action.onClick);
    right.append(btn);
  });

  wrap.append(left, h1, right);
  return wrap;
}
