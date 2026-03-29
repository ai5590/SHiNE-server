export function renderPageLabel(titleRu, pageId, collapsed, onToggle) {
  const label = document.createElement('div');
  label.className = `page-label${collapsed ? ' is-collapsed' : ''}`;

  const toggle = document.createElement('button');
  toggle.type = 'button';
  toggle.className = 'page-label-toggle';
  toggle.title = collapsed ? 'Показать подпись' : 'Скрыть подпись';
  toggle.setAttribute(
    'aria-label',
    collapsed ? 'Показать подпись страницы для разработки' : 'Скрыть подпись страницы для разработки',
  );
  toggle.addEventListener('click', onToggle);

  if (!collapsed) {
    label.append(toggle);

    const content = document.createElement('div');
    content.className = 'page-label-content';

    const hint = document.createElement('div');
    hint.className = 'page-label-hint';
    hint.textContent = 'Для разработки';

    const caption = document.createElement('div');
    caption.className = 'page-label-caption';
    caption.textContent = `${titleRu} (${pageId})`;

    content.append(hint, caption);
    label.append(content);
  } else {
    label.append(toggle);
  }

  return label;
}
