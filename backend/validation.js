export function parseIncident(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw Error('Invalid incident');
  const { eventId, riderName, contacts, medicalId, location } = input;
  if (typeof eventId !== 'string' || !/^[a-f0-9-]{36}$/i.test(eventId)) throw Error('Invalid event ID');
  if (typeof riderName !== 'string' || riderName.trim().length < 2 || riderName.length > 80) throw Error('Invalid rider name');
  if (!Array.isArray(contacts) || contacts.length < 1 || contacts.length > 3 ||
      contacts.some(c => typeof c?.name !== 'string' || c.name.trim().length < 1 || c.name.length > 60 ||
        typeof c?.phone !== 'string' || !/^\+[1-9]\d{6,14}$/.test(c.phone))) throw Error('Invalid contacts; use international numbers');
  if (new Set(contacts.map(c => c.phone)).size !== contacts.length) throw Error('Duplicate contact');
  if (medicalId !== undefined && (typeof medicalId !== 'string' || medicalId.length > 850)) throw Error('Invalid Medical ID');
  if (location !== undefined && location !== null &&
    (typeof location.latitude !== 'number' || !Number.isFinite(location.latitude) || Math.abs(location.latitude) > 90 ||
     typeof location.longitude !== 'number' || !Number.isFinite(location.longitude) || Math.abs(location.longitude) > 180 ||
     typeof location.accuracyMeters !== 'number' || location.accuracyMeters < 0 || location.accuracyMeters > 100000 ||
     typeof location.capturedAt !== 'number' || Math.abs(Date.now() - location.capturedAt) > 30 * 60_000)) throw Error('Invalid location');
  return {
    eventId, riderName: riderName.trim(),
    contacts: contacts.map(c => ({ name: c.name.trim(), phone: c.phone })),
    medicalId: medicalId || '', location: location || null
  };
}

export function alertBody(incident, repeat) {
  const text = `CrashAlert: possible crash involving ${incident.riderName}. No response to the safety check. Please call the rider and seek help if needed.`;
  const where = incident.location
    ? ` Last reported location (±${Math.round(incident.location.accuracyMeters)} m, not live): https://maps.google.com/?q=${incident.location.latitude.toFixed(6)},${incident.location.longitude.toFixed(6)}`
    : ' Location unavailable.';
  const medical = incident.medicalId ? ` Self-reported Medical ID: ${incident.medicalId}` : '';
  return `${repeat ? 'FOLLOW-UP: ' : ''}${text}${where}${medical}`;
}
