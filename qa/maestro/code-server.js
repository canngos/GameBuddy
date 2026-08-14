#!/usr/bin/env node
/**
 * A tiny local service that hands out the current verification code for an address.
 *
 *   node qa/maestro/code-server.js          # listens on 127.0.0.1:8099
 *
 * Why this exists
 * ---------------
 * The signup flow cannot be driven with a code captured in advance. Signing in as an
 * unverified account shows "Account not verified" with an *Email me a code* button, and
 * tapping it issues a **new** code — invalidating whatever provision.js read a moment
 * earlier. So the flow has to read the code after the button is tapped, from inside the
 * flow.
 *
 * Maestro's `runScript` runs on the host and can make HTTP calls, but it cannot open a
 * database connection. This bridges that gap and nothing else: one route, loopback only,
 * and it refuses any address that is not a `@qa.gamebuddy.invalid` test account — so even
 * though it hands out verification codes, it cannot be pointed at a real one.
 *
 * Local test tooling. Never run this anywhere that matters.
 */

const http = require('node:http');
const db = require('../functional/helpers/db');

const PORT = Number(process.env.GB_CODE_PORT || 8099);
const ALLOWED = /@qa\.gamebuddy\.invalid$/i;

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  res.setHeader('Content-Type', 'application/json');

  if (url.pathname !== '/code') {
    res.writeHead(404).end(JSON.stringify({ error: 'not found' }));
    return;
  }

  const email = url.searchParams.get('email') || '';
  if (!ALLOWED.test(email)) {
    // The guard that makes this safe to run: it only ever answers for addresses in a
    // reserved domain that cannot receive mail and cannot belong to a person.
    res.writeHead(403).end(JSON.stringify({ error: 'only @qa.gamebuddy.invalid addresses' }));
    return;
  }

  try {
    const code = db.verificationCode(email);
    if (!code) {
      res.writeHead(404).end(JSON.stringify({ error: 'no code stored for that address' }));
      return;
    }
    res.writeHead(200).end(JSON.stringify({ code: String(code) }));
  } catch (e) {
    res.writeHead(500).end(JSON.stringify({ error: e.message }));
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`verification-code helper on http://127.0.0.1:${PORT}/code?email=...`);
});
