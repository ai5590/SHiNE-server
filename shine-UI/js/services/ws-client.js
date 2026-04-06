import { captureClientError } from './client-error-reporter.js?v=20260406221807';

const DEFAULT_TIMEOUT_MS = 12000;

function buildWsUrl(raw) {
  const value = (raw || '').trim();
  if (!value) return 'wss://shineup.me/ws';
  if (value.startsWith('ws://') || value.startsWith('wss://')) return value;
  if (value.startsWith('http://')) return `ws://${value.slice('http://'.length)}`;
  if (value.startsWith('https://')) return `wss://${value.slice('https://'.length)}`;
  return value;
}

function createRequestId(op) {
  return `${op}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export class WsJsonClient {
  constructor(url) {
    this.url = buildWsUrl(url);
    this.ws = null;
    this.pending = new Map();
    this.openPromise = null;
    this.eventListeners = new Map();
  }

  async open() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return;
    if (this.openPromise) return this.openPromise;

    this.openPromise = new Promise((resolve, reject) => {
      const ws = new WebSocket(this.url);
      this.ws = ws;

      ws.addEventListener('open', () => {
        resolve();
      }, { once: true });

      ws.addEventListener('error', () => {
        captureClientError({
          kind: 'ws_open_error',
          message: `Failed to connect WebSocket ${this.url}`,
          context: { url: this.url },
        });
        reject(new Error(`Не удалось подключиться к ${this.url}`));
      }, { once: true });

      ws.addEventListener('close', () => {
        this.failPending('Соединение WebSocket закрыто');
      });

      ws.addEventListener('message', (event) => {
        this.handleMessage(event.data);
      });
    }).finally(() => {
      this.openPromise = null;
    this.eventListeners = new Map();
    });

    return this.openPromise;
  }

  async request(op, payload = {}, timeoutMs = DEFAULT_TIMEOUT_MS) {
    await this.open();
    const requestId = createRequestId(op);
    const body = { op, requestId, payload };

    const responsePromise = new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        this.pending.delete(requestId);
        if (op !== 'ClientErrorLog') {
          captureClientError({
            kind: 'ws_timeout',
            message: `Timeout waiting for ${op}`,
            requestOp: op,
            requestIdRef: requestId,
            context: { url: this.url, timeoutMs },
          });
        }
        reject(new Error(`Таймаут ответа для операции ${op}`));
      }, timeoutMs);

      this.pending.set(requestId, {
        op,
        resolve: (value) => {
          window.clearTimeout(timer);
          resolve(value);
        },
        reject: (error) => {
          window.clearTimeout(timer);
          reject(error);
        },
      });
    });

    this.ws.send(JSON.stringify(body));
    return responsePromise;
  }

  close() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  handleMessage(raw) {
    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      captureClientError({
        kind: 'ws_bad_json',
        message: 'Received non-JSON message from server',
        context: { raw: String(raw).slice(0, 1000) },
      });
      return;
    }

    const requestId = data?.requestId;
    const isEvent = data?.event === true || (requestId && !this.pending.has(requestId));
    if (isEvent) {
      this.emitEvent(data?.op || '', data);
      return;
    }

    if (!requestId) return;
    const slot = this.pending.get(requestId);
    if (!slot) return;
    this.pending.delete(requestId);
    slot.resolve(data);
  }

  onEvent(op, callback) {
    if (!op || typeof callback !== 'function') return () => {};
    if (!this.eventListeners.has(op)) {
      this.eventListeners.set(op, new Set());
    }
    const set = this.eventListeners.get(op);
    set.add(callback);
    return () => {
      set.delete(callback);
      if (!set.size) this.eventListeners.delete(op);
    };
  }

  emitEvent(op, data) {
    const listeners = this.eventListeners.get(op);
    if (!listeners) return;
    listeners.forEach((cb) => {
      try { cb(data); } catch {}
    });
  }

  failPending(message) {
    const pendingOps = [...this.pending.values()]
      .map((slot) => slot.op)
      .filter((op) => op && op !== 'ClientErrorLog');
    if (pendingOps.length > 0) {
      captureClientError({
        kind: 'ws_closed',
        message,
        context: { url: this.url, pendingOps },
      });
    }

    const error = new Error(message);
    for (const [, slot] of this.pending.entries()) {
      slot.reject(error);
    }
    this.pending.clear();
  }
}
