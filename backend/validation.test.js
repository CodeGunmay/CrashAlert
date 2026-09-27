import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseIncident, alertBody } from './validation.js';

const incident = { eventId: '550e8400-e29b-41d4-a716-446655440000', riderName: 'A Rider', contacts: [{ name: 'Family', phone: '+919876543210' }] };
test('accepts one valid contact and omits Medical ID without consent', () => {
  const parsed = parseIncident(incident);
  assert.equal(parsed.medicalId, '');
  assert.match(alertBody(parsed, false), /Location unavailable/);
  assert.doesNotMatch(alertBody(parsed, false), /Self-reported Medical/);
});
test('rejects unqualified and duplicate numbers', () => {
  assert.throws(() => parseIncident({ ...incident, contacts: [{ name: 'A', phone: '9876543210' }] }));
  assert.throws(() => parseIncident({ ...incident, contacts: [incident.contacts[0], incident.contacts[0]] }));
});
test('checks location freshness and exact user opt-in data', () => {
  const parsed = parseIncident({ ...incident, medicalId: 'Blood B+', location: { latitude: 12.9, longitude: 77.6, accuracyMeters: 18, capturedAt: Date.now() } });
  assert.match(alertBody(parsed, true), /FOLLOW-UP:.*Blood B\+/);
  assert.match(alertBody(parsed, true), /not live/);
  assert.throws(() => parseIncident({ ...incident, location: { ...parsed.location, capturedAt: Date.now() - 60 * 60_000 } }));
});
