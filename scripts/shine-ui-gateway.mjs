import http from 'node:http';
import net from 'node:net';
import path from 'node:path';
import { promises as fs } from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const uiRoot = path.resolve(projectRoot, 'shine-UI');

const listenPort = Number(process.env.SHINE_UI_PORT || 8088);
const backendHost = process.env.SHINE_BACKEND_HOST || '127.0.0.1';
const backendPort = Number(process.env.SHINE_BACKEND_PORT || 7071);
const backendWsPath = process.env.SHINE_BACKEND_WS_PATH || '/ws';

const textContentTypes = new Set([
  '.html',
  '.css',
  '.js',
  '.mjs',
  '.json',
  '.txt',
  '.svg',
  '.xml',
  '.webmanifest',
]);

const mimeByExt = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.svg': 'image/svg+xml; charset=utf-8',
  '.xml': 'application/xml; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function sanitizePathname(pathname) {
  const withoutQuery = String(pathname || '').split('?')[0];
  const decoded = decodeURIComponent(withoutQuery);
  const normalized = path.posix.normalize(decoded);
  if (normalized.includes('..')) return null;
  return normalized.startsWith('/') ? normalized : `/${normalized}`;
}

function toLocalFilePath(pathname) {
  const safePath = sanitizePathname(pathname);
  if (!safePath) return null;
  const target = safePath === '/' ? '/index.html' : safePath;
  return path.resolve(uiRoot, `.${target}`);
}

function isInsideUiRoot(filePath) {
  const rel = path.relative(uiRoot, filePath);
  return rel && !rel.startsWith('..') && !path.isAbsolute(rel);
}

async function tryReadFile(filePath) {
  try {
    const stat = await fs.stat(filePath);
    if (!stat.isFile()) return null;
    const data = await fs.readFile(filePath);
    return { data, stat };
  } catch {
    return null;
  }
}

function writeCorsAndCacheHeaders(res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
}

async function handleHttp(req, res) {
  if (req.method === 'OPTIONS') {
    writeCorsAndCacheHeaders(res);
    res.writeHead(204);
    res.end();
    return;
  }

  const rawUrl = req.url || '/';
  const pathname = rawUrl.split('?')[0] || '/';
  if (pathname.startsWith('/ws')) {
    writeCorsAndCacheHeaders(res);
    res.writeHead(426, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ ok: false, error: 'upgrade_required', message: 'Use WebSocket upgrade for /ws' }));
    return;
  }

  const directPath = toLocalFilePath(pathname);
  if (!directPath || !isInsideUiRoot(directPath)) {
    writeCorsAndCacheHeaders(res);
    res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Bad path');
    return;
  }

  let file = await tryReadFile(directPath);
  if (!file) {
    const fallback = path.resolve(uiRoot, 'index.html');
    file = await tryReadFile(fallback);
    if (!file) {
      writeCorsAndCacheHeaders(res);
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('index.html not found');
      return;
    }
  }

  const ext = path.extname(directPath).toLowerCase();
  const contentType = mimeByExt[ext] || 'application/octet-stream';
  writeCorsAndCacheHeaders(res);
  res.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': file.stat.size,
  });
  res.end(file.data);
}

function buildUpstreamUpgradeRequest(req) {
  const sourceUrl = new URL(req.url || '/ws', `http://${req.headers.host || 'localhost'}`);
  const targetPath = `${backendWsPath}${sourceUrl.search || ''}`;

  const headers = { ...req.headers };
  headers.host = `${backendHost}:${backendPort}`;
  headers.connection = 'Upgrade';
  headers.upgrade = 'websocket';

  let raw = `GET ${targetPath} HTTP/1.1\r\n`;
  Object.entries(headers).forEach(([name, value]) => {
    if (value == null) return;
    if (Array.isArray(value)) {
      value.forEach((single) => {
        raw += `${name}: ${single}\r\n`;
      });
      return;
    }
    raw += `${name}: ${value}\r\n`;
  });
  raw += '\r\n';
  return raw;
}

function handleUpgrade(req, socket, head) {
  const pathname = String(req.url || '').split('?')[0] || '';
  if (!pathname.startsWith('/ws')) {
    socket.destroy();
    return;
  }

  const upstream = net.connect(backendPort, backendHost, () => {
    const upstreamRequest = buildUpstreamUpgradeRequest(req);
    upstream.write(upstreamRequest);
    if (head?.length) {
      upstream.write(head);
    }
    socket.pipe(upstream).pipe(socket);
  });

  const closeBoth = () => {
    if (!socket.destroyed) socket.destroy();
    if (!upstream.destroyed) upstream.destroy();
  };

  upstream.on('error', closeBoth);
  socket.on('error', closeBoth);
}

const server = http.createServer((req, res) => {
  handleHttp(req, res).catch(() => {
    writeCorsAndCacheHeaders(res);
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Internal gateway error');
  });
});

server.on('upgrade', handleUpgrade);

server.listen(listenPort, () => {
  console.log(`[shine-ui-gateway] uiRoot=${uiRoot}`);
  console.log(`[shine-ui-gateway] listening=http://localhost:${listenPort}`);
  console.log(`[shine-ui-gateway] ws proxy=ws://${backendHost}:${backendPort}${backendWsPath}`);
});
