import { WebSocketServer, WebSocket } from 'ws';
import { TikTokLiveConnection } from 'tiktok-live-connector';
import { createServer } from 'node:http';

const PORT = Number(process.env.PORT || 8080);
const TOKEN = process.env.MOORTY_TOKEN || '';
if (!TOKEN) { console.error('Falta MOORTY_TOKEN: no se inicia el relay sin autenticación.'); process.exit(1); }
const httpServer = createServer((req, res) => {
  if (req.url === '/health') { res.writeHead(200, {'content-type':'application/json'}); res.end(JSON.stringify({ok:true, service:'moorty-relay'})); return; }
  res.writeHead(404); res.end('Moorty Relay');
});
const wss = new WebSocketServer({ noServer: true, maxPayload: 1024 * 64 });
const rooms = new Map();

httpServer.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  if (url.searchParams.get('token') !== TOKEN) { socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n'); socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws, req));
});

function send(ws, payload) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload)); }
function userName(data) { return data?.user?.nickname || data?.user?.uniqueId || data?.user?.displayId || data?.nickname || 'alguien'; }
function emit(room, event) { for (const client of room.clients) send(client, event); }
function safeString(v, fallback='') { return v == null ? fallback : String(v).slice(0, 500); }

async function subscribe(ws, username) {
  username = safeString(username).replace(/^@/, '').trim();
  if (!username || !/^[a-zA-Z0-9._]{2,40}$/.test(username)) { send(ws, {type:'error', message:'Nombre de usuario TikTok no válido.'}); return; }
  const key = username.toLowerCase();
  let room = rooms.get(key);
  if (!room) {
    room = { username, clients: new Set(), connection: null, connecting: false };
    rooms.set(key, room);
  }
  room.clients.add(ws); ws.roomKey = key;
  if (room.connection || room.connecting) { send(ws, {type:'status', message:`Esperando conexión con @${username}…`}); return; }
  room.connecting = true;
  const connection = new TikTokLiveConnection(username, { processInitialData: false });
  room.connection = connection;
  const on = (event, cb) => connection.on(event, data => { try { cb(data || {}); } catch (err) { console.error('event handler', event, err); } });
  on('chat', d => emit(room, {type:'comment', user:userName(d), text:safeString(d.comment)}));
  on('gift', d => emit(room, {type:'gift', user:userName(d), gift:safeString(d.giftDetails?.giftName || d.giftName || d.giftId, 'un regalo')}));
  on('member', d => {
    const user = d?.user || {};
    const subscriber = typeof user.isSubscriber === 'boolean' ? user.isSubscriber : (typeof user.isSub === 'boolean' ? user.isSub : null);
    emit(room, {type:'join', user:userName(d), subscriber});
  });
  on('follow', d => emit(room, {type:'follow', user:userName(d)}));
  on('share', d => emit(room, {type:'share', user:userName(d)}));
  on('like', d => emit(room, {type:'like', user:userName(d), count:Number(d.likeCount || d.count || 0)}));
  on('superFan', d => emit(room, {type:'subscribe', user:userName(d)}));
  on('connected', () => { room.connecting = false; emit(room, {type:'connected', user:'', message:`Conectado a @${username}`}); });
  on('disconnected', () => { emit(room, {type:'status', message:`TikTok desconectó @${username}`}); });
  on('error', err => { room.connecting = false; emit(room, {type:'error', message:safeString(err?.message, 'error al conectar con TikTok')}); });
  try {
    await connection.connect();
    room.connecting = false;
    emit(room, {type:'connected', message:`Conectado a @${username}`});
  } catch (err) {
    room.connecting = false;
    room.connection = null;
    emit(room, {type:'error', message:safeString(err?.message, 'No se pudo conectar. Verifica que la cuenta esté en directo y revisa la configuración del proveedor de firma.')});
  }
}

wss.on('connection', ws => {
  ws.on('message', async raw => {
    let msg; try { msg = JSON.parse(raw.toString()); } catch { send(ws, {type:'error', message:'Mensaje inválido'}); return; }
    if (msg.action === 'subscribe') await subscribe(ws, msg.username);
    else send(ws, {type:'error', message:'Acción no reconocida'});
  });
  ws.on('close', () => {
    const key = ws.roomKey; if (!key) return;
    const room = rooms.get(key); if (!room) return;
    room.clients.delete(ws);
    if (room.clients.size === 0 && room.connection) {
      const connection = room.connection; room.connection = null;
      Promise.resolve(connection.disconnect()).catch(()=>{});
      rooms.delete(key);
    }
  });
});

httpServer.listen(PORT, '0.0.0.0', () => console.log(`Moorty Relay listening on ${PORT}`));
