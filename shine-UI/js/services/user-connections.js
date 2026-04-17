import { authService, state } from '../state.js';
import { loadProfileSnapshot } from './user-profile-params.js';

function normalizeLogin(value) {
  return String(value || '').trim();
}

function normKey(value) {
  return normalizeLogin(value).toLowerCase();
}

function uniqueLogins(list) {
  const out = [];
  const seen = new Set();
  (Array.isArray(list) ? list : []).forEach((item) => {
    const login = normalizeLogin(item);
    if (!login) return;
    const key = normKey(login);
    if (seen.has(key)) return;
    seen.add(key);
    out.push(login);
  });
  return out;
}

function listContainsLogin(list, login) {
  const targetKey = normKey(login);
  if (!targetKey) return false;
  return uniqueLogins(list).some((value) => normKey(value) === targetKey);
}

function toFieldMap(snapshot) {
  const map = {};
  (snapshot?.fields || []).forEach((field) => {
    map[field.key] = String(field.value || '').trim();
  });
  return map;
}

function toToggleMap(snapshot) {
  const map = {};
  (snapshot?.toggles || []).forEach((toggle) => {
    map[toggle.key] = Boolean(toggle.enabled);
  });
  return map;
}

function readArray(payload, key) {
  const value = payload?.[key];
  return Array.isArray(value) ? uniqueLogins(value) : null;
}

function feedOwnerLogins(feedPayload) {
  const rows = Array.isArray(feedPayload?.followedUsersChannels) ? feedPayload.followedUsersChannels : [];
  const owners = rows
    .map((row) => normalizeLogin(row?.channel?.ownerLogin))
    .filter(Boolean);
  return uniqueLogins(owners);
}

async function buildRelationsModel(login) {
  const cleanLogin = normalizeLogin(login);
  if (!cleanLogin) {
    return {
      outFriends: [],
      inFriends: [],
      outContacts: [],
      inContacts: [],
      outFollows: [],
      inFollows: [],
    };
  }

  const graph = await authService.getUserConnectionsGraph(cleanLogin);

  let outContacts = readArray(graph, 'outContacts');
  let outFollows = readArray(graph, 'outFollows');

  const isCurrentSessionLogin = normKey(cleanLogin) === normKey(state.session.login);

  if (outContacts === null && isCurrentSessionLogin) {
    try {
      const contacts = await authService.listContacts();
      outContacts = uniqueLogins(contacts?.contacts || []);
    } catch {
      outContacts = [];
    }
  }
  if (outContacts === null) outContacts = [];

  if (outFollows === null) {
    try {
      const feed = await authService.listSubscriptionsFeed(cleanLogin, 200);
      outFollows = feedOwnerLogins(feed);
    } catch {
      outFollows = [];
    }
  }

  return {
    outFriends: readArray(graph, 'outFriends') || [],
    inFriends: readArray(graph, 'inFriends') || [],
    outContacts,
    inContacts: readArray(graph, 'inContacts') || [],
    outFollows,
    inFollows: readArray(graph, 'inFollows') || [],
  };
}

export function buildIdentityLines({ login, firstName, lastName }) {
  const lines = [];
  const first = String(firstName || '').trim();
  const last = String(lastName || '').trim();
  const cleanLogin = normalizeLogin(login);

  if (first) lines.push(first);
  if (last) lines.push(last);
  lines.push(cleanLogin || 'unknown');

  return lines;
}

export function buildAvatarInitials({ login, firstName, lastName }) {
  const first = String(firstName || '').trim();
  const last = String(lastName || '').trim();
  if (first || last) {
    const a = (first[0] || '').toUpperCase();
    const b = (last[0] || '').toUpperCase();
    const initials = `${a}${b}`.trim();
    if (initials) return initials;
  }

  const cleanLogin = normalizeLogin(login);
  return (cleanLogin[0] || '?').toUpperCase();
}

export async function loadCurrentRelations() {
  const login = normalizeLogin(state.session.login);
  if (!login) {
    return {
      outFriends: [],
      inFriends: [],
      outContacts: [],
      inContacts: [],
      outFollows: [],
      inFollows: [],
    };
  }
  return buildRelationsModel(login);
}

export function relationFlagsForTarget(relations, targetLogin) {
  return {
    outFriend: listContainsLogin(relations?.outFriends, targetLogin),
    inFriend: listContainsLogin(relations?.inFriends, targetLogin),
    outContact: listContainsLogin(relations?.outContacts, targetLogin),
    inContact: listContainsLogin(relations?.inContacts, targetLogin),
    outFollow: listContainsLogin(relations?.outFollows, targetLogin),
    inFollow: listContainsLogin(relations?.inFollows, targetLogin),
  };
}

export async function loadUserProfileCard(login) {
  const cleanLogin = normalizeLogin(login);
  if (!cleanLogin) throw new Error('Пустой login');

  const [user, snapshot] = await Promise.all([
    authService.getUser(cleanLogin),
    loadProfileSnapshot(cleanLogin),
  ]);

  if (!user?.exists) throw new Error('Пользователь не найден');

  const canonicalLogin = normalizeLogin(user.login || cleanLogin);
  const fields = toFieldMap(snapshot);
  const toggles = toToggleMap(snapshot);

  return {
    login: canonicalLogin,
    blockchainName: normalizeLogin(user.blockchainName),
    firstName: fields.first_name || '',
    lastName: fields.last_name || '',
    address: fields.address || '',
    web: fields.web || '',
    phone: fields.phone || '',
    gender: String(snapshot?.gender || 'unknown').trim().toLowerCase() || 'unknown',
    official: Boolean(toggles.official),
    shine: Boolean(toggles.shine),
  };
}

export async function loadRelationsForPair({ currentLogin, targetLogin }) {
  const cleanCurrent = normalizeLogin(currentLogin);
  const cleanTarget = normalizeLogin(targetLogin);
  const currentRelations = await buildRelationsModel(cleanCurrent);
  let flags = relationFlagsForTarget(currentRelations, cleanTarget);

  if (!flags.inContact || !flags.inFollow) {
    try {
      const targetRelations = await buildRelationsModel(cleanTarget);
      const backFlags = relationFlagsForTarget(targetRelations, cleanCurrent);
      flags = {
        ...flags,
        inContact: flags.inContact || backFlags.outContact,
        inFollow: flags.inFollow || backFlags.outFollow,
      };
    } catch {
      // ignore fallback failures for incoming direction
    }
  }

  return {
    ...flags,
    source: currentRelations,
  };
}
