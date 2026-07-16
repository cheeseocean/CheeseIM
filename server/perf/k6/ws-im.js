import ws from 'k6/ws';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const VUS = Number(__ENV.CONNECTIONS || 20);
const RAMP = __ENV.RAMP || '10s';
const HOLD = __ENV.HOLD || '30s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';
const MESSAGE_RATE = Number(__ENV.MESSAGE_RATE || 10);
const HEARTBEAT_MS = Number(__ENV.HEARTBEAT_MS || 15000);
const TICKETS_FILE = __ENV.TICKETS_FILE || './tickets.json';
const tickets = JSON.parse(open(TICKETS_FILE));

const authOk = new Counter('im_auth_ok');
const chatSent = new Counter('im_chat_sent');
const brokerAccepted = new Counter('im_broker_accepted');
const receivedUnique = new Counter('im_received_unique');
const receivedDuplicate = new Counter('im_received_duplicate');
const deliveryAckSent = new Counter('im_delivery_ack_sent');
const readAckSent = new Counter('im_read_ack_sent');
const protocolErrors = new Counter('im_protocol_errors');
const connectionFailures = new Rate('im_connection_failures');
const brokerAckLatency = new Trend('im_broker_ack_latency', true);

export const options = {
  scenarios: {
    im_connections: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    im_connection_failures: ['rate<0.01'],
    im_protocol_errors: ['count==0'],
  },
};

export default function () {
  const identity = tickets[exec.vu.idInTest - 1];
  if (!identity) {
    connectionFailures.add(true);
    throw new Error(`tickets.json 缺少 VU ${exec.vu.idInTest} 的一次性 ticket`);
  }
  const pending = new Map();
  const received = new Set();
  let authenticated = false;
  let sequence = 0;
  const url = __ENV.WS_URL || 'ws://127.0.0.1:5147/ws';

  const response = ws.connect(url, {}, (socket) => {
    socket.on('open', () => socket.sendBinary(clientEnvelope(10, requestId('auth'), 10,
      stringField(1, identity.ticket))));

    socket.on('message', (data) => {
      if (typeof data === 'string') {
        protocolErrors.add(1);
        return;
      }
      const envelope = parseMessage(new Uint8Array(data));
      const command = numberValue(envelope.get(1));
      const request = stringValue(envelope.get(2));
      if (command === 10) {
        authenticated = true;
        authOk.add(1);
        return;
      }
      if (command === 31) {
        brokerAccepted.add(1);
        const startedAt = pending.get(request);
        if (startedAt !== undefined) {
          brokerAckLatency.add(Date.now() - startedAt);
          pending.delete(request);
        }
        return;
      }
      if (command === 32) {
        onChatMessage(socket, envelope.get(13), identity, received);
        return;
      }
      if (command === 90) {
        protocolErrors.add(1);
      }
    });

    socket.on('error', () => connectionFailures.add(true));
    socket.setInterval(() => {
      if (authenticated) socket.sendBinary(clientEnvelope(20, requestId('hb')));
    }, HEARTBEAT_MS);

    const perVuIntervalMs = MESSAGE_RATE <= 0
      ? 2147483647
      : Math.max(1, Math.round(1000 * VUS / MESSAGE_RATE));
    socket.setInterval(() => {
      if (!authenticated) return;
      sequence += 1;
      const req = requestId(`send-${sequence}`);
      pending.set(req, Date.now());
      socket.sendBinary(clientEnvelope(30, req, 11, chatMessage(identity, req)));
      chatSent.add(1);
    }, perVuIntervalMs);
  });

  connectionFailures.add(response && response.status === 101 ? false : true);
  check(response, { 'WebSocket upgrade is 101': (r) => r && r.status === 101 });
}

function onChatMessage(socket, payload, identity, received) {
  if (!payload) return;
  const message = parseMessage(payload);
  const serverMsgId = stringValue(message.get(2));
  const senderId = stringValue(message.get(3));
  const seq = numberValue(message.get(17));
  if (!serverMsgId || seq <= 0) {
    protocolErrors.add(1);
    return;
  }
  if (received.has(serverMsgId)) {
    receivedDuplicate.add(1);
    return;
  }
  received.add(serverMsgId);
  receivedUnique.add(1);
  const conversationId = privateConversationId(identity.userId, senderId);
  const op = requestId(`delivery-${seq}`);
  const delivery = concat(
    stringField(1, conversationId), intField(2, seq),
    stringField(3, identity.deviceId), stringField(4, op));
  socket.sendBinary(clientEnvelope(37, op, 15, delivery));
  deliveryAckSent.add(1);
  if ((__ENV.SEND_READ_ACK || 'true') === 'true') {
    const readOp = requestId(`read-${seq}`);
    const read = concat(
      stringField(1, conversationId), intField(2, seq),
      stringField(3, identity.deviceId), stringField(4, readOp));
    socket.sendBinary(clientEnvelope(33, readOp, 12, read));
    readAckSent.add(1);
  }
}

function chatMessage(identity, clientMsgId) {
  return concat(
    stringField(1, clientMsgId),
    stringField(4, identity.peerId),
    bytesField(6, utf8Encode(`k6:${clientMsgId}`)),
    intField(7, 101), intField(8, 1), intField(9, Date.now()));
}

function clientEnvelope(command, request, payloadField, payload) {
  const fields = [intField(1, command), stringField(2, request)];
  if (payloadField && payload) fields.push(bytesField(payloadField, payload));
  return concat(...fields).buffer;
}

function requestId(prefix) {
  return `k6-${__ENV.RUN_ID || 'smoke'}-${exec.vu.idInTest}-${prefix}-${Date.now()}`;
}

function privateConversationId(left, right) {
  return left < right ? `s:${left}:${right}` : `s:${right}:${left}`;
}

function intField(field, value) { return concat(varint((field << 3) | 0), varint(value)); }
function stringField(field, value) { return bytesField(field, utf8Encode(String(value))); }
function bytesField(field, value) { return concat(varint((field << 3) | 2), varint(value.length), value); }
function concat(...parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) { out.set(part, offset); offset += part.length; }
  return out;
}
function varint(value) {
  let n = BigInt(value);
  const out = [];
  while (n > 127n) { out.push(Number((n & 127n) | 128n)); n >>= 7n; }
  out.push(Number(n));
  return Uint8Array.from(out);
}
function parseMessage(bytes) {
  const fields = new Map();
  for (let i = 0; i < bytes.length;) {
    const tag = readVarint(bytes, i); i = tag.next;
    const field = Number(tag.value >> 3n);
    const wire = Number(tag.value & 7n);
    if (wire === 0) {
      const value = readVarint(bytes, i); i = value.next; fields.set(field, value.value);
    } else if (wire === 2) {
      const size = readVarint(bytes, i); i = size.next;
      const end = i + Number(size.value); fields.set(field, bytes.slice(i, end)); i = end;
    } else {
      throw new Error(`不支持的 protobuf wire type: ${wire}`);
    }
  }
  return fields;
}
function readVarint(bytes, start) {
  let value = 0n; let shift = 0n; let i = start;
  while (i < bytes.length) {
    const b = BigInt(bytes[i++]); value |= (b & 127n) << shift;
    if ((b & 128n) === 0n) return { value, next: i };
    shift += 7n;
  }
  throw new Error('截断的 protobuf varint');
}
function numberValue(value) { return typeof value === 'bigint' ? Number(value) : 0; }
function stringValue(value) { return value ? utf8Decode(value) : ''; }
function utf8Encode(value) {
  const encoded = unescape(encodeURIComponent(value));
  return Uint8Array.from(encoded, (character) => character.charCodeAt(0));
}
function utf8Decode(value) {
  let encoded = '';
  for (const byte of value) encoded += String.fromCharCode(byte);
  return decodeURIComponent(escape(encoded));
}
