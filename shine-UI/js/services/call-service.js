import { addChatMessage, state, authService } from '../state.js';

const TYPES = {
  INVITE: 100,
  RINGING: 110,
  ACCEPT: 120,
  DECLINE_BUSY: 130,
  TIMEOUT: 140,
  HANGUP: 150,
  OFFER: 200,
  ANSWER: 210,
  ICE: 220,
};

const calls = new Map();
let activeCallId = '';

function nowMs() {
  return Date.now();
}

function makeCallId() {
  return `${Date.now()}${Math.floor(Math.random() * 1_000_000_000)}`;
}

function getCall(callId) {
  return calls.get(callId) || null;
}

function setStatus(call, text) {
  call.status = text;
  addChatMessage(call.peerLogin, `[call] ${text}`);
}

function cleanupTimers(call) {
  if (call.timers?.ack5s) clearTimeout(call.timers.ack5s);
  if (call.timers?.total22s) clearTimeout(call.timers.total22s);
  if (call.timers?.incoming20s) clearTimeout(call.timers.incoming20s);
}

async function closeMedia(call) {
  try { call.pc?.close(); } catch {}
  try { call.localStream?.getTracks()?.forEach((t) => t.stop()); } catch {}
  call.pc = null;
  call.localStream = null;
}

async function finishCall(call, reason, notifyRemote = false) {
  if (!call) return;
  cleanupTimers(call);
  if (notifyRemote && call.remoteSessionId) {
    try {
      await authService.callSignalToSession({
        toLogin: call.peerLogin,
        targetSessionId: call.remoteSessionId,
        callId: call.callId,
        type: TYPES.HANGUP,
        data: '',
      });
    } catch {}
  }
  await closeMedia(call);
  setStatus(call, `завершен: ${reason}`);
  calls.delete(call.callId);
  if (activeCallId === call.callId) activeCallId = '';
}

async function ensurePeerConnection(call) {
  if (call.pc) return call.pc;

  const pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
  });

  pc.onicecandidate = async (event) => {
    if (!event.candidate || !call.remoteSessionId) return;
    try {
      await authService.callSignalToSession({
        toLogin: call.peerLogin,
        targetSessionId: call.remoteSessionId,
        callId: call.callId,
        type: TYPES.ICE,
        data: JSON.stringify(event.candidate),
      });
    } catch {}
  };

  pc.onconnectionstatechange = () => {
    if (pc.connectionState === 'connected') {
      setStatus(call, 'соединение установлено');
    }
    if (pc.connectionState === 'failed' || pc.connectionState === 'closed' || pc.connectionState === 'disconnected') {
      finishCall(call, `state=${pc.connectionState}`, false);
    }
  };

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    call.localStream = stream;
    stream.getTracks().forEach((track) => pc.addTrack(track, stream));
  } catch (e) {
    setStatus(call, `нет доступа к микрофону: ${e?.message || 'unknown'}`);
  }

  pc.ontrack = (evt) => {
    const audio = new Audio();
    audio.autoplay = true;
    audio.srcObject = evt.streams[0];
    call.remoteAudio = audio;
  };

  call.pc = pc;
  return pc;
}

async function sendSignal(call, type, data = '') {
  if (!call.remoteSessionId) return;
  await authService.callSignalToSession({
    toLogin: call.peerLogin,
    targetSessionId: call.remoteSessionId,
    callId: call.callId,
    type,
    data,
  });
}

async function onAccept(call) {
  cleanupTimers(call);
  const pc = await ensurePeerConnection(call);
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  await sendSignal(call, TYPES.OFFER, JSON.stringify(offer));
  setStatus(call, 'отправлен offer');
}

export async function startOutgoingCall(peerLogin) {
  const cleanPeer = String(peerLogin || '').trim();
  if (!cleanPeer) return;

  if (activeCallId) {
    addChatMessage(cleanPeer, '[call] уже есть активный звонок');
    return;
  }

  const callId = makeCallId();
  const call = {
    callId,
    peerLogin: cleanPeer,
    direction: 'out',
    state: 'dialing',
    remoteSessionId: '',
    timers: {},
    startedAtMs: nowMs(),
    pc: null,
    localStream: null,
  };
  calls.set(callId, call);
  activeCallId = callId;
  setStatus(call, 'набираем...');

  call.timers.ack5s = setTimeout(() => {
    if (call.state === 'dialing') {
      finishCall(call, 'нет ответа 5с', false);
    }
  }, 5000);

  call.timers.total22s = setTimeout(() => {
    finishCall(call, 'таймаут 22с', false);
  }, 22000);

  await authService.callInviteBroadcast({ toLogin: cleanPeer, callId, type: TYPES.INVITE });
}

export async function handleIncomingCallInvite(evt) {
  const payload = evt?.payload || {};
  const callId = String(payload.callId || '').trim();
  const fromLogin = String(payload.fromLogin || '').trim();
  const fromSessionId = String(payload.fromSessionId || '').trim();
  if (!callId || !fromLogin || !fromSessionId) return;

  if (activeCallId && activeCallId !== callId) {
    try {
      await authService.callSignalToSession({
        toLogin: fromLogin,
        targetSessionId: fromSessionId,
        callId,
        type: TYPES.DECLINE_BUSY,
        data: 'busy',
      });
    } catch {}
    return;
  }

  let call = getCall(callId);
  if (!call) {
    call = {
      callId,
      peerLogin: fromLogin,
      direction: 'in',
      state: 'incoming',
      remoteSessionId: fromSessionId,
      timers: {},
      startedAtMs: nowMs(),
      pc: null,
      localStream: null,
    };
    calls.set(callId, call);
  }

  activeCallId = callId;
  setStatus(call, `входящий звонок от ${fromLogin}`);

  try {
    await sendSignal(call, TYPES.RINGING, 'ringing');
  } catch {}

  call.timers.incoming20s = setTimeout(async () => {
    await sendSignal(call, TYPES.TIMEOUT, 'timeout_20s');
    await finishCall(call, 'не ответили 20с', false);
  }, 20000);

  const accepted = window.confirm(`Вам звонит ${fromLogin}. Принять звонок?`);
  if (!accepted) {
    await sendSignal(call, TYPES.DECLINE_BUSY, 'decline');
    await finishCall(call, 'отклонен', false);
    return;
  }

  call.state = 'accepted';
  await sendSignal(call, TYPES.ACCEPT, 'accept');
  setStatus(call, 'принят, ждём offer');
}

export async function handleIncomingCallSignal(evt) {
  const payload = evt?.payload || {};
  const callId = String(payload.callId || '').trim();
  const fromLogin = String(payload.fromLogin || '').trim();
  const fromSessionId = String(payload.fromSessionId || '').trim();
  const type = Number(payload.type);
  const data = String(payload.data || '');
  if (!callId || !fromLogin || !Number.isFinite(type)) return;

  const call = getCall(callId);
  if (!call) return;
  if (!call.remoteSessionId) call.remoteSessionId = fromSessionId;

  if (type === TYPES.RINGING) {
    call.state = 'ringing';
    setStatus(call, 'идут гудки');
    return;
  }

  if (type === TYPES.ACCEPT) {
    call.state = 'accepted';
    setStatus(call, 'звонок принят');
    await onAccept(call);
    return;
  }

  if (type === TYPES.DECLINE_BUSY) {
    await finishCall(call, 'занят/отклонено', false);
    return;
  }

  if (type === TYPES.TIMEOUT) {
    await finishCall(call, 'таймаут на стороне собеседника', false);
    return;
  }

  if (type === TYPES.HANGUP) {
    await finishCall(call, 'собеседник завершил звонок', false);
    return;
  }

  if (type === TYPES.OFFER) {
    const pc = await ensurePeerConnection(call);
    await pc.setRemoteDescription(new RTCSessionDescription(JSON.parse(data)));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await sendSignal(call, TYPES.ANSWER, JSON.stringify(answer));
    setStatus(call, 'получен offer, отправлен answer');
    return;
  }

  if (type === TYPES.ANSWER) {
    const pc = await ensurePeerConnection(call);
    await pc.setRemoteDescription(new RTCSessionDescription(JSON.parse(data)));
    setStatus(call, 'получен answer');
    return;
  }

  if (type === TYPES.ICE) {
    const pc = await ensurePeerConnection(call);
    await pc.addIceCandidate(new RTCIceCandidate(JSON.parse(data)));
  }
}

export async function hangupActiveCall() {
  if (!activeCallId) return;
  const call = getCall(activeCallId);
  await finishCall(call, 'сброшен пользователем', true);
}
