export const profile = {
  login: '@shine.alex',
  name: 'Алексей сияющий',
  avatarInitials: 'АС',
  phone: '+7 (916) 221-45-88',
  address: 'Москва, Пресненская наб., 12',
  email: 'alex.shine@demo.local',
  socials: '@alexshine / t.me/alexshine',
  badges: ['Официальный аккаунт', 'Сияющий'],
};

export const wallet = {
  balanceSOL: '182.4571',
  publicAddress: '9sVAXJ2CqP3BrtC6AFeQHhcuWjN1kUyhY7L8pkQJxMZe',
  updatedAt: 'сегодня, 14:42',
};

export const deviceSessions = [
  {
    sessionId: 'sess_7c5e5c4b',
    clientInfoFromClient: 'Android 15; Pixel 9',
    clientInfoFromRequest: 'UA=Java-http-client/17.0.18; remote=127.0.0.1',
    geo: 'RU/Moscow',
    lastAuthenticatedAtMs: 1774600010500,
  },
  {
    sessionId: 'sess_90ab11de',
    clientInfoFromClient: 'iOS 19; iPhone 17',
    clientInfoFromRequest: 'UA=ShineMobile/2.4; remote=10.0.2.12',
    geo: 'RU/Moscow',
    lastAuthenticatedAtMs: 1774553310000,
  },
  {
    sessionId: 'sess_3ea4f11c',
    clientInfoFromClient: 'Windows 11; Chrome 124',
    clientInfoFromRequest: 'UA=Mozilla/5.0; remote=192.168.1.21',
    geo: 'RU/Kazan',
    lastAuthenticatedAtMs: 1774499010000,
  },
];

export const directMessages = [
  {
    id: 'u1',
    name: 'Марина К.',
    initials: 'МК',
    lastMessage: 'Вечером скину обновления по макетам.',
    time: '15:08',
    unread: 2,
  },
  {
    id: 'u2',
    name: 'Илья П.',
    initials: 'ИП',
    lastMessage: 'Спасибо, уже проверяю!',
    time: '14:31',
    unread: 0,
  },
  {
    id: 'u3',
    name: 'Елена Д.',
    initials: 'ЕД',
    lastMessage: 'Тестовый стенд снова доступен.',
    time: '13:02',
    unread: 5,
  },
  {
    id: 'u4',
    name: 'Никита О.',
    initials: 'НО',
    lastMessage: 'Отлично, давай так и сделаем.',
    time: 'вчера',
    unread: 0,
  },
];

export const contactDirectory = [
  {
    id: 'u5',
    name: 'Марк С.',
    initials: 'МС',
    about: 'Продуктовый аналитик, любит короткие созвоны и длинные отчёты.',
  },
  {
    id: 'u6',
    name: 'Мария Л.',
    initials: 'МЛ',
    about: 'UI-дизайнер, собирает референсы и следит за визуальным стилем.',
  },
  {
    id: 'u7',
    name: 'Марина Р.',
    initials: 'МР',
    about: 'Контент-менеджер, ведёт каналы и готовит анонсы.',
  },
  {
    id: 'u8',
    name: 'Максим В.',
    initials: 'МВ',
    about: 'Frontend-разработчик, отвечает за анимации и адаптивность.',
  },
  {
    id: 'u9',
    name: 'Мадина А.',
    initials: 'МА',
    about: 'Комьюнити-менеджер, быстро находит нужных людей.',
  },
  {
    id: 'u10',
    name: 'Ирина П.',
    initials: 'ИП',
    about: 'Редактор новостей, помогает с текстами и публикациями.',
  },
  {
    id: 'u11',
    name: 'Николай Д.',
    initials: 'НД',
    about: 'Технический писатель, структурирует знания по продукту.',
  },
  {
    id: 'u12',
    name: 'Егор Т.',
    initials: 'ЕТ',
    about: 'QA-инженер, любит проверять сложные сценарии вручную.',
  },
];

export const chatMessages = {
  u1: [
    { from: 'in', text: 'Привет! Видел новые карточки?' },
    { from: 'out', text: 'Да, смотрятся сильно. Нужен финальный текст.' },
    { from: 'in', text: 'Вечером скину обновления по макетам.' },
  ],
  u2: [
    { from: 'out', text: 'Скинул доступы в чат команды.' },
    { from: 'in', text: 'Спасибо, уже проверяю!' },
  ],
  u3: [
    { from: 'in', text: 'Тестовый стенд снова доступен.' },
    { from: 'out', text: 'Отлично, запускаю прогон сценариев.' },
  ],
  u4: [
    { from: 'in', text: 'Подтверждаю план на завтра.' },
    { from: 'out', text: 'Отлично, давай так и сделаем.' },
  ],
};

export const channels = [
  {
    id: 'ch1',
    name: 'Новости продукта',
    initials: 'НП',
    description: 'Официальный канал команды Shine с релизами и обновлениями.',
    lastMessage: 'Опубликовали обзор нового демо-прототипа мобильного интерфейса.',
    time: '16:05',
    unread: 14,
  },
  {
    id: 'ch2',
    name: 'Анекдоты дня',
    initials: 'АД',
    description: 'Лёгкий развлекательный канал с короткими шутками и мемами.',
    lastMessage: 'Новый пост: как дизайнер, разработчик и дедлайн зашли в бар.',
    time: '15:20',
    unread: 3,
  },
  {
    id: 'ch3',
    name: 'Новости рынка',
    initials: 'НР',
    description: 'Короткие ежедневные сводки по рынку, технологиям и сообществам.',
    lastMessage: 'В ленте свежая подборка новостей и главных событий дня.',
    time: 'вчера',
    unread: 0,
  },
];

export const channelPosts = {
  ch1: [
    {
      id: 'p1',
      title: 'Новый экран профиля',
      body: 'Добавлены бейджи статуса, переработан верхний блок и улучшены быстрые переходы.',
    },
    {
      id: 'p2',
      title: 'Навигация без перезагрузки',
      body: 'Переходы между экранами теперь стабильнее работают в SPA-режиме через hash-router.',
    },
  ],
  ch2: [
    {
      id: 'p3',
      title: 'Анекдот утра',
      body: 'Разработчик говорит: "Я починил один баг". Баги в ответ: "Нас было трое".',
    },
    {
      id: 'p4',
      title: 'Анекдот про дедлайн',
      body: 'Дедлайн был настолько близко, что команда начала здороваться с ним по имени.',
    },
  ],
  ch3: [
    {
      id: 'p5',
      title: 'Утренний дайджест',
      body: 'Собрали ключевые новости дня: обновления продуктов, движения рынка и заметные релизы.',
    },
    {
      id: 'p6',
      title: 'Что обсуждают сегодня',
      body: 'В фокусе дня: рост интереса к мобильным dApp-интерфейсам и новые анонсы сообществ.',
    },
  ],
};

export const notifications = {
  replies: [
    { id: 'r1', title: 'Марина К. ответила на ваш комментарий', text: 'Согласна, такую структуру и оставим.', time: '12 минут назад' },
    { id: 'r2', title: 'Илья П. ответил в обсуждении', text: 'Добавил примеры экранов для onboarding.', time: '48 минут назад' },
  ],
  events: [
    { id: 'e1', title: 'Елена Д. добавила вас в друзья', text: 'Теперь вы в связях первого уровня.', time: 'сегодня' },
    { id: 'e2', title: 'Никита О. удалил из друзей', text: 'Связь перенесена в архив событий.', time: 'вчера' },
    { id: 'e3', title: 'Марина К. поставила лайк', text: 'Оценен ваш пост о прототипе.', time: '2 дня назад' },
  ],
};

export const networkGraph = {
  center: { id: 'me', name: 'Вы', initials: 'ВЫ', x: 50, y: 50 },
  peers: [
    { id: 'p1', name: 'Марина', initials: 'МК', x: 20, y: 24 },
    { id: 'p2', name: 'Илья', initials: 'ИП', x: 80, y: 22 },
    { id: 'p3', name: 'Елена', initials: 'ЕД', x: 18, y: 78 },
    { id: 'p4', name: 'Никита', initials: 'НО', x: 82, y: 76 },
  ],
};
