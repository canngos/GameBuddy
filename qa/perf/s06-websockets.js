/**
 * S6 — concurrent STOMP sockets.
 *
 * A separate dimension from HTTP throughput, and the one the DAU model implies most
 * directly: 50,000 DAU means ~750 people with the app open, and every one of them holds a
 * socket for the whole session whether or not they send anything. The HTTP ladder never
 * measures that, because its connections close.
 *
 * What is being asked: how much does an idle-but-open connection cost, and does the
 * in-memory SimpleBroker hold that many? Each VU opens a socket, authenticates the STOMP
 * CONNECT, subscribes to the three destinations the app subscribes to, and then sits there
 * heartbeating for the duration — which is what a real client does between messages.
 *
 * Read the answer from the monitor's memory column, not from k6: the interesting number is
 * megabytes of JVM heap per thousand sockets, and only the container knows that.
 */

import ws from 'k6/ws';
import { check } from 'k6';
import { tokens } from './workload.js';

const SOCKETS = Number(__ENV.GB_SOCKETS || 750);
const HOLD_SECONDS = Number(__ENV.GB_HOLD_SECONDS || 60);
const WS_URL = (__ENV.GB_BASE_URL || 'http://localhost:8180').replace(/^http/, 'ws') + '/ws';

export const options = {
  scenarios: {
    hold: {
      executor: 'per-vu-iterations',
      vus: SOCKETS,
      iterations: 1,
      maxDuration: `${HOLD_SECONDS + 120}s`,
    },
  },
  thresholds: {
    'ws_connecting': ['p(95)<2000'],
    checks: ['rate>0.99'],
  },
};

/** STOMP is a text protocol; four frames is the whole client. NUL-terminated. */
const frame = (command, headers = {}, body = '') =>
  `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`;

export default function () {
  // One token per VU, spread across the pool so no gamer holds hundreds of sockets.
  const { token } = tokens[(__VU - 1) % tokens.length];

  const res = ws.connect(WS_URL, {}, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        host: 'localhost',
        // The same 10s heartbeat the app negotiates. Heartbeats are not free at scale —
        // they are the reason an idle socket still costs CPU — so leaving them out would
        // flatter the result.
        'heart-beat': '10000,10000',
        Authorization: `Bearer ${token}`,
      }));
    });

    let connected = false;
    socket.on('message', (data) => {
      if (!connected && data.startsWith('CONNECTED')) {
        connected = true;
        check(null, { 'stomp connected': () => true });

        for (const dest of ['/user/queue/messages', '/user/queue/presence', '/user/queue/typing']) {
          socket.send(frame('SUBSCRIBE', { id: `sub-${__VU}-${dest}`, destination: dest }));
        }
      }
    });

    // Client heartbeat: a bare newline, which is what @stomp/stompjs sends.
    socket.setInterval(() => socket.send('\n'), 10000);

    socket.setTimeout(() => {
      socket.send(frame('DISCONNECT'));
      socket.close();
    }, HOLD_SECONDS * 1000);

    socket.on('error', (e) => {
      if (e.error() !== 'websocket: close sent') {
        check(null, { 'socket stayed open': () => false });
      }
    });
  });

  check(res, { 'handshake 101': (r) => r && r.status === 101 });
}

export function handleSummary(data) {
  const v = (k) => data.metrics[k]?.values ?? {};
  const summary = {
    plan: __ENV.GB_PLAN || 'unknown',
    sockets: SOCKETS,
    holdSeconds: HOLD_SECONDS,
    sessions: v('ws_sessions').count,
    connectingMs: { p95: v('ws_connecting')['p(95)'], max: v('ws_connecting').max },
    checksPassed: v('checks').rate,
  };
  return {
    stdout: `\n=== ${summary.plan}: ${SOCKETS} STOMP sockets held ${HOLD_SECONDS}s ===\n` +
      `sessions opened ${summary.sessions}\n` +
      `handshake p95 ${summary.connectingMs.p95?.toFixed(0)}ms  max ${summary.connectingMs.max?.toFixed(0)}ms\n` +
      `checks ${(summary.checksPassed * 100).toFixed(1)}%\n` +
      'Memory cost per socket: read the backend column in the monitor log.\n\n',
    [`results/websockets-${summary.plan}-${SOCKETS}.json`]: JSON.stringify(summary, null, 2),
  };
}
