import http from 'node:http';
import { randomUUID, timingSafeEqual } from 'node:crypto';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import twilio from 'twilio';
import { parseIncident, alertBody } from './validation.js';

const required = ['FIREBASE_PROJECT_ID', 'TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM', 'CRON_SECRET'];
for (const key of required) if (!process.env[key]) throw Error(`Missing ${key}`);
initializeApp({ credential: applicationDefault(), projectId: process.env.FIREBASE_PROJECT_ID });
const db = getFirestore();
const sms = twilio(process.env.TWILIO_ACCOUNT_SID, process.env.TWILIO_AUTH_TOKEN);
const INTERVAL_MS = 120_000;
const MAX_SLOTS = 6; // One initial contact alert and up to five follow-ups.

function reply(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}
async function readJson(req) {
  let value = '';
  for await (const chunk of req) {
    value += chunk.toString();
    if (value.length > 5000) throw Error('Payload too large');
  }
  return JSON.parse(value);
}
async function userId(req) {
  const match = /^Bearer (.+)$/.exec(req.headers.authorization || '');
  if (!match) throw Error('Sign in required');
  const token = await getAuth().verifyIdToken(match[1], true);
  if (!token.phone_number) throw Error('Verified phone number required');
  return token.uid;
}
function cronAuthenticated(req) {
  const given = Buffer.from((req.headers.authorization || '').replace(/^Bearer /, ''));
  const expected = Buffer.from(process.env.CRON_SECRET);
  return given.length === expected.length && timingSafeEqual(given, expected);
}
async function sendSlot(ref, slot) {
  const snap = await ref.get();
  const incident = snap.data();
  if (!incident || incident.status !== 'active' || incident.slot !== slot) return;
  const outcomes = [];
  for (const contact of incident.contacts) {
    // Recheck cancellation before each provider request.
    if ((await ref.get()).data()?.status !== 'active') break;
    try {
      const result = await sms.messages.create({
        from: process.env.TWILIO_FROM, to: contact.phone,
        body: alertBody(incident, slot > 0)
      });
      outcomes.push({ to: contact.phone, providerId: result.sid, providerStatus: result.status });
    } catch (error) {
      outcomes.push({ to: contact.phone, error: String(error.code || 'provider_error') });
    }
  }
  await ref.collection('attempts').doc(String(slot)).set({ slot, at: Date.now(), outcomes });
  if (process.env.N8N_WEBHOOK_URL && process.env.N8N_WEBHOOK_SECRET) {
    try {
      await fetch(process.env.N8N_WEBHOOK_URL, {
        method: 'POST', headers: { 'content-type': 'application/json', 'x-crashalert-secret': process.env.N8N_WEBHOOK_SECRET },
        body: JSON.stringify({ eventId: incident.eventId, slot, attempted: outcomes.length, providerStatuses: outcomes.map(o => o.providerStatus || o.error) }),
        signal: AbortSignal.timeout(5000)
      });
    } catch (error) { console.error('n8n notification failed', error); }
  }
}
async function createIncident(uid, input) {
  const value = parseIncident(input);
  const ref = db.collection('incidents').doc(`${uid}_${value.eventId}`);
  let created = false;
  await db.runTransaction(async tx => {
    const rider = db.collection('riders').doc(uid);
    const [prior, riderSnap] = await Promise.all([tx.get(ref), tx.get(rider)]);
    if (prior.exists) return;
    const now = Date.now();
    if (riderSnap.exists && now - riderSnap.data().lastIncidentAt < 10 * 60_000) throw Error('Incident rate limit');
    tx.set(rider, { lastIncidentAt: now });
    tx.set(ref, { ...value, uid, status: 'active', createdAt: now, dueAt: now + INTERVAL_MS,
      expiresAt: now + 7 * 24 * 60 * 60_000, slot: 0 });
    created = true;
  });
  if (created) await sendSlot(ref, 0);
  return { eventId: value.eventId, created, status: 'attempted' };
}
async function advance(ref) {
  let claimed = null;
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists || snap.data().status !== 'active' || snap.data().dueAt > Date.now()) return;
    const next = snap.data().slot + 1;
    if (next >= MAX_SLOTS) { tx.update(ref, { status: 'completed', dueAt: null }); return; }
    tx.update(ref, { slot: next, dueAt: Date.now() + INTERVAL_MS });
    claimed = next;
  });
  if (claimed !== null) await sendSlot(ref, claimed);
}
const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') return reply(res, 200, { ok: true });
    if (req.method === 'POST' && req.url === '/v1/jobs/tick') {
      if (!cronAuthenticated(req)) return reply(res, 401, { error: 'Unauthorized' });
      const items = await db.collection('incidents').where('dueAt', '<=', Date.now()).limit(100).get();
      for (const item of items.docs) await advance(item.ref);
      return reply(res, 200, { checked: items.size });
    }
    const uid = await userId(req);
    if (req.method === 'POST' && req.url === '/v1/incidents') {
      const result = await createIncident(uid, await readJson(req));
      return reply(res, result.created ? 202 : 200, result);
    }
    const cancel = /^\/v1\/incidents\/([a-f0-9-]{36})\/cancel$/i.exec(req.url || '');
    if (req.method === 'POST' && cancel) {
      const ref = db.collection('incidents').doc(`${uid}_${cancel[1]}`);
      if (!(await ref.get()).exists) return reply(res, 404, { error: 'Not found' });
      await ref.update({ status: 'cancelled', dueAt: null, cancelledAt: Date.now() });
      return reply(res, 200, { status: 'cancelled' });
    }
    reply(res, 404, { error: 'Not found' });
  } catch (error) {
    console.error(randomUUID(), error);
    const invalid = /Invalid|Duplicate|Payload|rate limit|Sign in|Verified phone|Unexpected token/.test(String(error.message));
    reply(res, invalid ? 400 : 500, { error: invalid ? error.message : 'Request failed' });
  }
});
server.listen(Number(process.env.PORT || 8080));
