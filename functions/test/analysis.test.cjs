const { test } = require('node:test');
const assert = require('node:assert/strict');
const { parseTextInput, parseEventDraft, requireActiveProfile, distanceKm, parseTextAssessment, parseEventAssessment } = require('../lib/analysis');

const now = Date.now();
const draft = { title: 'Torneo comunitario', description: 'Partido amistoso de fútbol.', category: 'SPORTS',
  latitude: 4.5339, longitude: -75.6811, startsAt: now + 60000, endsAt: now + 120000 };
const blocked = fn => assert.throws(fn);
test('acepta texto válido en español e inglés y elimina espacios exteriores', () => {
  assert.deepEqual(parseTextInput({ text: ' Hola ' }), { text: 'Hola', language: 'es' });
  assert.equal(parseTextInput({ text: 'Hello', language: 'en' }).language, 'en');
});
test('rechaza texto vacío, demasiado extenso, objetos y lenguajes desconocidos', () => {
  for (const input of [null, [], {}, { text: '' }, { text: 'x'.repeat(5001) }, { text: 'hola', language: 'xx' }]) blocked(() => parseTextInput(input));
});
test('un borrador válido conserva categoría, coordenadas y fechas reales', () => {
  assert.equal(parseEventDraft(draft, 'owner', now).category, 'SPORTS');
});
test('rechaza fechas vencidas, coordenadas inválidas y categorías inventadas', () => {
  for (const change of [{ startsAt: now }, { endsAt: now + 1 }, { latitude: 91 }, { longitude: NaN }, { category: 'OTHER' }, { startsAt: now + 1.5 }]) {
    blocked(() => parseEventDraft({ ...draft, ...change }, 'owner', now));
  }
});
test('las imágenes solo pueden referir un borrador propio, nunca identidades, URL u otro usuario', () => {
  assert.equal(parseEventDraft({ ...draft, imagePath: 'event-drafts/owner/abc-123' }, 'owner', now).imagePath, 'event-drafts/owner/abc-123');
  for (const imagePath of ['event-drafts/other/abc', 'identities/owner/abc', 'events/owner/abc', 'https://example.com/a.png', 'event-drafts/owner/../abc', 'event-drafts/owner/']) {
    blocked(() => parseEventDraft({ ...draft, imagePath }, 'owner', now));
  }
});
test('solo una cuenta activa puede solicitar análisis', () => {
  blocked(() => requireActiveProfile(undefined, false));
  blocked(() => requireActiveProfile({ accountStatus: 'SUSPENDED', role: 'ADMINISTRATOR' }, false));
  requireActiveProfile({ accountStatus: 'ACTIVE', role: 'PARTICIPANT' }, false);
});
test('los eventos exigen un administrador o un organizador verificado', () => {
  for (const profile of [{ role: 'PARTICIPANT' }, { role: 'ORGANIZER', organizerVerificationStatus: 'PENDING' }, { role: 'ORGANIZER', organizerVerificationStatus: 'REJECTED' }]) {
    blocked(() => requireActiveProfile({ accountStatus: 'ACTIVE', ...profile }, true));
  }
  requireActiveProfile({ accountStatus: 'ACTIVE', role: 'ADMINISTRATOR' }, true);
  requireActiveProfile({ accountStatus: 'ACTIVE', role: 'ORGANIZER', organizerVerificationStatus: 'VERIFIED' }, true);
});
test('una respuesta permisiva debe tener tipos correctos, no strings true o false', () => {
  blocked(() => parseTextAssessment({ allowed: 'true', reasons: [], suggestedText: null }));
  assert.equal(parseTextAssessment({ allowed: true, reasons: [], suggestedText: null }).allowed, true);
});
test('un rechazo exige motivo y versión alternativa', () => {
  blocked(() => parseTextAssessment({ allowed: false, reasons: [], suggestedText: null }));
  blocked(() => parseTextAssessment({ allowed: false, reasons: ['Insulto'], suggestedText: '' }));
  assert.equal(parseTextAssessment({ allowed: false, reasons: ['Insulto'], suggestedText: 'Conversemos con respeto.' }).allowed, false);
});
test('el resultado de imagen debe concordar con la presencia de imagen', () => {
  const result = { text: { allowed: true, reasons: [], suggestedText: null }, image: { relevant: null, reason: '' }, possibleDuplicates: [] };
  parseEventAssessment(result, false, []);
  blocked(() => parseEventAssessment(result, true, []));
  blocked(() => parseEventAssessment({ ...result, image: { relevant: true, reason: '' } }, false, []));
});
test('el modelo no puede inventar IDs de duplicados ni repetirlos', () => {
  const result = { text: { allowed: true, reasons: [], suggestedText: null }, image: { relevant: null, reason: '' }, possibleDuplicates: [{ eventId: 7, reason: 'Mismo evento' }] };
  blocked(() => parseEventAssessment(result, false, [{ eventId: 8 }]));
  assert.equal(parseEventAssessment(result, false, [{ eventId: 7 }]).possibleDuplicates[0].eventId, 7);
  blocked(() => parseEventAssessment({ ...result, possibleDuplicates: [...result.possibleDuplicates, ...result.possibleDuplicates] }, false, [{ eventId: 7 }, { eventId: 8 }]));
});
test('la distancia corresponde a las coordenadas y permite distinguir zonas', () => {
  assert.equal(distanceKm(draft, draft), 0);
  assert.ok(distanceKm(draft, { latitude: 4.54, longitude: -75.6811 }) < 1);
  assert.ok(distanceKm(draft, { latitude: 4.6, longitude: -75.6811 }) > 1);
});
