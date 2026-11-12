const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const project = 'demo-share-you-ai';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;

function value(v) {
  if (v === null) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  if (typeof v === 'object') return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, value(x)])) } };
  return { stringValue: v };
}
async function write(path, data, token = 'owner') {
  if (token !== 'owner' && data.aiReviewId && /^(events|comments)\//.test(path)) {
    const fields = v => Object.fromEntries(Object.entries(v).map(([k, x]) => [k, value(x)]));
    return fetch(`${root}:commit`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ writes: [
        { update: { name: `projects/${project}/databases/(default)/documents/${path}`, fields: fields(data) } },
        { update: { name: `projects/${project}/databases/(default)/documents/aiReviewUses/${data.aiReviewId}`,
          fields: fields({ uid: data.lastActorId ?? data.userId, kind: path.startsWith('events/') ? 'event' : 'text', targetId: data.id }) } }
      ] }) });
  }
  return fetch(`${root}/${path}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, value(v)])) }) });
}
async function fixture() {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
  const response = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `publish-${randomUUID()}@example.test`, password: 'LocalTest123!', returnSecureToken: true }) });
  assert.equal(response.status, 200);
  const user = await response.json();
  assert.equal((await write(`users/${user.localId}`, { name: 'Autora', accountStatus: 'ACTIVE', role: 'ORGANIZER', organizerVerificationStatus: 'VERIFIED' })).status, 200);
  const now = Date.now();
  const id = Math.floor(Math.random() * 1_000_000_000) + 1;
  const reviewId = randomUUID();
  const event = { id, seriesId: id, previousEventId: null, title: 'Actividad comunitaria', description: 'Compartimos con los vecinos', category: 'COMMUNITY',
    startsAt: now + 3_600_000, endsAt: now + 7_200_000, latitude: 4.5339, longitude: -75.6811,
    location: 'Salón comunal', locationConfirmed: true, price: 0, capacity: 20, attendeeCount: 0,
    organizerId: user.localId, organizerName: 'Autora', submittedByName: 'Autora', submittedAtLabel: 'ahora',
    photoUri: null, distanceKm: 0, moderationStatus: 'PENDING', rejectionReason: null, isFinished: false, isDeleted: false,
    registrations: {}, registrationUserIds: [], revision: 1, lastActorId: user.localId, promotedUserId: null, isOutdoor: false,
    aiReviewId: reviewId, aiDuplicateConfirmed: false };
  const review = { kind: 'event', uid: user.localId, title: event.title, description: event.description, category: event.category,
    startsAt: event.startsAt, endsAt: event.endsAt, latitude: event.latitude, longitude: event.longitude,
    photoUri: null, sourceEventId: null, hasDuplicates: false, expiresAt: now + 900_000 };
  return { user, event, review, reviewId };
}
async function denied(response) { assert.equal(response.status, 403, await response.text()); }
async function accepted(response) { assert.equal(response.status, 200, await response.text()); }

test('no se puede publicar sin una validación emitida por el servidor ni fabricar una', async () => {
  const f = await fixture();
  await denied(await write(`events/${f.event.id}`, f.event, f.user.idToken));
  await denied(await write(`aiReviews/${f.reviewId}`, f.review, f.user.idToken));
});
test('una validación vigente permite guardar exactamente el contenido analizado', async () => {
  const f = await fixture();
  await accepted(await write(`aiReviews/${f.reviewId}`, f.review));
  await accepted(await write(`events/${f.event.id}`, f.event, f.user.idToken));
  await denied(await write(`events/${f.event.id + 1}`, { ...f.event, id: f.event.id + 1, seriesId: f.event.id + 1 }, f.user.idToken));
});
test('no se pueden cambiar texto, categoría, coordenadas, fechas ni foto después del análisis', async () => {
  const f = await fixture();
  await accepted(await write(`aiReviews/${f.reviewId}`, f.review));
  for (const patch of [{ title: 'Otro título' }, { description: 'Otro contenido' }, { category: 'SPORTS' },
    { latitude: 5 }, { longitude: -76 }, { startsAt: f.event.startsAt + 1 }, { endsAt: f.event.endsAt + 1 }, { photoUri: 'https://unrelated.test/image' }]) {
    await denied(await write(`events/${f.event.id}`, { ...f.event, ...patch }, f.user.idToken));
  }
});
test('rechaza validaciones vencidas, de otra cuenta o destinadas a otro evento', async () => {
  const f = await fixture();
  for (const patch of [{ expiresAt: Date.now() - 1000 }, { uid: 'other-user' }, { sourceEventId: 99 }]) {
    await accepted(await write(`aiReviews/${f.reviewId}`, { ...f.review, ...patch }));
    await denied(await write(`events/${f.event.id}`, f.event, f.user.idToken));
  }
});
test('los posibles duplicados requieren confirmación explícita', async () => {
  const f = await fixture();
  await accepted(await write(`aiReviews/${f.reviewId}`, { ...f.review, hasDuplicates: true }));
  await denied(await write(`events/${f.event.id}`, f.event, f.user.idToken));
  await accepted(await write(`events/${f.event.id}`, { ...f.event, aiDuplicateConfirmed: true }, f.user.idToken));
});
test('editar exige una validación para ese evento y se conserva la inscripción de documentos anteriores', async () => {
  const f = await fixture();
  await accepted(await write(`events/${f.event.id}`, { ...f.event, moderationStatus: 'APPROVED', aiReviewId: null }));
  const edit = { ...f.event, revision: 2, description: 'Actividad con información actualizada' };
  await accepted(await write(`aiReviews/${f.reviewId}`, { ...f.review, description: edit.description }));
  await denied(await write(`events/${f.event.id}`, edit, f.user.idToken));
  await accepted(await write(`aiReviews/${f.reviewId}`, { ...f.review, description: edit.description, sourceEventId: f.event.id }));
  await accepted(await write(`events/${f.event.id}`, edit, f.user.idToken));
});
test('se permiten aportes a eventos próximos con texto validado, sin convertirlos en reseñas de asistencia', async () => {
  const f = await fixture();
  await accepted(await write(`events/${f.event.id}`, { ...f.event, moderationStatus: 'APPROVED' }));
  const comment = { id: f.event.id, eventId: f.event.id, userId: f.user.localId, authorName: 'Autora',
    text: 'También hay acceso por la entrada sur.', createdAt: Date.now(), isContribution: true, aiReviewId: randomUUID() };
  await denied(await write(`comments/${comment.id}`, comment, f.user.idToken));
  await accepted(await write(`aiReviews/${comment.aiReviewId}`, { kind: 'text', uid: f.user.localId, text: comment.text, expiresAt: Date.now() + 900_000 }));
  await denied(await write(`comments/${comment.id}`, { ...comment, text: 'Texto diferente' }, f.user.idToken));
  await denied(await write(`comments/${comment.id}`, { ...comment, isContribution: false }, f.user.idToken));
  await accepted(await write(`comments/${comment.id}`, comment, f.user.idToken));
});
test('las inscripciones de eventos anteriores no exigen volver a analizar el contenido', async () => {
  const owner = await fixture(), attendee = await fixture();
  const { aiReviewId, aiDuplicateConfirmed, ...legacy } = owner.event;
  await accepted(await write(`events/${legacy.id}`, { ...legacy, moderationStatus: 'APPROVED' }));
  await accepted(await write(`events/${legacy.id}`, { ...legacy, moderationStatus: 'APPROVED', revision: 2,
    lastActorId: attendee.user.localId, attendeeCount: 1, registrationUserIds: [attendee.user.localId],
    registrations: { [attendee.user.localId]: { status: 'CONFIRMED', registeredAt: Date.now(), queueOrder: 1 } }
  }, attendee.user.idToken));
});
