import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const project = 'demo-share-you-rules';
const registrationDate = new Date();
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.equal(authHost, '127.0.0.1:19099', 'Solo se permite el emulador local de Auth.');
assert.equal(firestoreHost, '127.0.0.1:18080', 'Solo se permite el emulador local de Firestore.');
const root = `http://${firestoreHost}/v1/projects/${project}/databases/(default)/documents`;

async function account(email) {
  const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-test`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Local-test-1234', returnSecureToken: true })
  });
  assert.equal(response.status, 200, await response.clone().text());
  return { ...await response.json(), email };
}

function value(input) {
  if (input === null) return { nullValue: null };
  if (input instanceof Date) return { timestampValue: input.toISOString() };
  if (typeof input === 'boolean') return { booleanValue: input };
  if (typeof input === 'number') return Number.isInteger(input) ? { integerValue: String(input) } : { doubleValue: input };
  if (Array.isArray(input)) return { arrayValue: { values: input.map(value) } };
  if (typeof input === 'object') return { mapValue: { fields: Object.fromEntries(Object.entries(input).map(([key, v]) => [key, value(v)])) } };
  return { stringValue: input };
}

function profile(user, changes = {}) {
  return {
    id: user.localId, name: 'Usuario de prueba', email: user.email,
    role: 'PARTICIPANT', registrationDate, profilePictureUrl: null,
    phoneNumber: null, accountStatus: 'ACTIVE', pendingEmail: null, ...changes
  };
}

async function request(path, user, method = 'GET', data, admin = false) {
  const response = await fetch(`${root}/${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(admin ? { Authorization: 'Bearer owner' } : user ? { Authorization: `Bearer ${user.idToken}` } : {})
    },
    ...(data ? { body: JSON.stringify({ fields: Object.fromEntries(Object.entries(data).map(([key, v]) => [key, value(v)])) }) } : {})
  });
  return response;
}

let passed = 0;
async function check(label, response, expected) {
  const result = await response;
  assert.equal(result.status, expected, `${label}: ${await result.text()}`);
  passed++;
  console.log(`OK ${label}`);
}

const alice = await account('alice@example.test');
const bob = await account('bob@example.test');
const moderator = await account('moderator@example.test');
await check('Sin sesion no se leen perfiles', request('users', null), 403);
await check('No se puede crear el perfil de otra persona', request(`users/${bob.localId}`, alice, 'PATCH', profile(bob)), 403);
await check('No se puede registrar un administrador', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice, { role: 'ADMINISTRATOR' })), 403);
await check('No se pueden agregar campos arbitrarios', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice, { password: 'no-almacenar' })), 403);
await check('Registro de participante', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice)), 200);
await check('Registro de organizador', request(`users/${bob.localId}`, bob, 'PATCH', profile(bob, { role: 'ORGANIZER' })), 200);
await check('Cargar perfil al iniciar sesion', request(`users/${alice.localId}`, alice), 200);
await check('Listener actual de todos los perfiles', request('users', alice), 200);
await check('Editar nombre propio', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice, { name: 'Nombre actualizado' })), 200);
await check('No cambiar el rol propio', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice, { role: 'ADMINISTRATOR' })), 403);
await check('No cambiar correo a uno no autenticado', request(`users/${alice.localId}`, alice, 'PATCH', profile(alice, { email: 'other@example.test' })), 403);
await check('No editar perfil ajeno', request(`users/${bob.localId}`, alice, 'PATCH', profile(bob)), 403);
await check('Fixture local de moderador', request(`users/${moderator.localId}`, null, 'PATCH', profile(moderator, { role: 'ADMINISTRATOR' }), true), 200);
const aliceDocument = await (await request(`users/${alice.localId}`, alice)).json();
const aliceData = { ...profile(alice, { name: 'Nombre actualizado' }), registrationDate: new Date(aliceDocument.fields.registrationDate.timestampValue) };
await check('Moderador puede suspender', request(`users/${alice.localId}`, moderator, 'PATCH', { ...aliceData, accountStatus: 'SUSPENDED' }), 200);
await check('No reactivar la cuenta propia', request(`users/${alice.localId}`, alice, 'PATCH', aliceData), 403);
await check('Moderador puede reactivar', request(`users/${alice.localId}`, moderator, 'PATCH', aliceData), 200);
await check('No se permite leer una ruta desconocida', request('unknown', alice), 403);
for (const collection of ['events', 'comments', 'votes', 'favorites', 'following']) {
  await check(`Listener de ${collection}`, request(collection, alice), 200);
  await check(`Escrituras de ${collection} requieren reglas especificas`, request(`${collection}/1`, alice, 'PATCH', { userId: alice.localId }), 403);
}
await check('Fixture de notificacion propia', request('notifications/1', null, 'PATCH', { userId: alice.localId }, true), 200);
await check('Leer aviso propio', request('notifications/1', alice), 200);
await check('No leer aviso ajeno', request('notifications/1', bob), 403);
await check('No leer todos los avisos', request('notifications', alice), 403);
const query = await fetch(`${root}:runQuery`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${alice.idToken}` },
  body: JSON.stringify({ structuredQuery: {
    from: [{ collectionId: 'notifications' }],
    where: { fieldFilter: { field: { fieldPath: 'userId' }, op: 'EQUAL', value: value(alice.localId) } }
  } })
});
await check('Listener filtrado de avisos propios', Promise.resolve(query), 200);
await check('No eliminar perfil ajeno', request(`users/${bob.localId}`, alice, 'DELETE'), 403);

function pairKey(uid, other) {
  return createHash('sha256').update(`${uid.length}:${uid}${other.length}:${other}`).digest('hex');
}
function event(id, changes = {}) {
  return {
    id, title: 'Carrera del domingo', startsAt: Date.now() + 86400000, endsAt: Date.now() + 90000000,
    location: 'Parque seleccionado', distanceKm: 0, price: 0, category: 'SPORTS', attendeeCount: 0,
    description: 'Encuentro de corredores', capacity: 1, organizerId: bob.localId, organizerName: 'Usuario de prueba',
    photoUri: null, moderationStatus: 'PENDING', submittedAtLabel: 'ahora', submittedByName: 'Usuario de prueba',
    latitude: 4.5339, longitude: -75.6811, isFinished: false, rejectionReason: null, isDeleted: false, revision: 1,
    registrationUserIds: [], registrations: {}, locationConfirmed: true, seriesId: id, previousEventId: null,
    lastActorId: bob.localId, promotedUserId: null, isOutdoor: true, ...changes
  };
}
let current = event(1);
await check('Participante no puede crear eventos propios', request('events/90', alice, 'PATCH', event(90, { organizerId: alice.localId, lastActorId: alice.localId })), 403);
await check('Organizador pendiente no puede crear eventos', request('events/1', bob, 'PATCH', current), 403);
await check('Organizador no puede verificarse a si mismo', request(`users/${bob.localId}`, bob, 'PATCH', profile(bob, { role: 'ORGANIZER', organizerVerificationStatus: 'VERIFIED' })), 403);
const identity = { userId: bob.localId, photoPath: `gs://demo-share-you-rules.appspot.com/identities/${bob.localId}/identity-photo`, submittedAt: new Date() };
await check('Enviar documento privado propio', request(`organizerVerifications/${bob.localId}`, bob, 'PATCH', identity), 200);
await check('No leer documento ajeno', request(`organizerVerifications/${bob.localId}`, alice), 403);
await check('No enumerar documentos privados', request('organizerVerifications', alice), 403);
await check('Administrador puede leer documento', request(`organizerVerifications/${bob.localId}`, moderator), 200);
await check('No enviar documento con ruta de otro usuario', request(`organizerVerifications/${bob.localId}`, bob, 'PATCH', { ...identity, photoPath: `gs://bucket/identities/${alice.localId}/forged` }), 403);
await check('Administrador verifica identidad', request(`users/${bob.localId}`, moderator, 'PATCH', profile(bob, { role: 'ORGANIZER', organizerVerificationStatus: 'VERIFIED' })), 200);
await check('Identidad verificada no puede reemplazarse', request(`organizerVerifications/${bob.localId}`, bob, 'PATCH', identity), 403);
await check('Crear evento con punto confirmado', request('events/1', bob, 'PATCH', current), 200);
await check('No publicar con coordenadas sin confirmar', request('events/99', bob, 'PATCH', event(99, { locationConfirmed: false })), 403);
await check('No publicar con coordenadas fuera de rango', request('events/99', bob, 'PATCH', event(99, { latitude: 95 })), 403);
await check('No crear una publicacion a nombre ajeno', request('events/99', alice, 'PATCH', event(99)), 403);
await check('El autor no puede autoaprobar', request('events/1', bob, 'PATCH', { ...current, moderationStatus: 'APPROVED', revision: 2 }), 403);
await check('No relanzar un evento que no ha finalizado', request('events/2', bob, 'PATCH', event(2, { seriesId: 1, previousEventId: 1 })), 403);
current = { ...current, moderationStatus: 'APPROVED', revision: 2, lastActorId: moderator.localId };
await check('Aprobacion por moderador', request('events/1', moderator, 'PATCH', current), 200);
const attendance = { status: 'CONFIRMED', registeredAt: Date.now(), queueOrder: 1 };
current = { ...current, revision: 3, lastActorId: alice.localId, registrations: { [alice.localId]: attendance }, registrationUserIds: [alice.localId], attendeeCount: 1 };
await check('Inscripcion propia confirmada', request('events/1', alice, 'PATCH', current), 200);
const waiting = { status: 'WAITLIST', registeredAt: Date.now(), queueOrder: 2 };
current = { ...current, revision: 4, lastActorId: moderator.localId, registrations: { ...current.registrations, [moderator.localId]: waiting }, registrationUserIds: [alice.localId, moderator.localId] };
await check('Inscripcion en espera cuando se agota el cupo', request('events/1', moderator, 'PATCH', current), 200);
await check('No modificar la inscripcion de otra persona', request('events/1', alice, 'PATCH', { ...current, revision: 5, lastActorId: alice.localId, registrations: { ...current.registrations, [moderator.localId]: { ...waiting, status: 'CONFIRMED' } } }), 403);
const canceled = { ...current, revision: 5, lastActorId: alice.localId, promotedUserId: moderator.localId,
  registrations: { [moderator.localId]: { ...waiting, status: 'CONFIRMED' } }, registrationUserIds: [moderator.localId] };
await check('Cancelar promueve una inscripcion existente de la lista de espera', request('events/1', alice, 'PATCH', canceled), 200);
function comment(id, user, eventId = 1) {
  return { id, eventId, userId: user.localId, authorName: user.localId === alice.localId ? 'Nombre actualizado' : 'Usuario de prueba', text: 'Buena experiencia', createdAt: Date.now() };
}
await check('No comentar antes de finalizar', request('comments/1', moderator, 'PATCH', comment(1, moderator)), 403);
const favoriteId = pairKey(alice.localId, '1');
const favorite = { userId: alice.localId, eventId: 1, sourceEventId: 1, organizerId: bob.localId };
await check('Guardar favorito del evento', request(`favorites/${favoriteId}`, alice, 'PATCH', favorite), 200);
await check('No duplicar favoritos cambiando el ID', request('favorites/arbitrary', alice, 'PATCH', favorite), 403);
const voteId = pairKey(alice.localId, '1');
await check('Voto con clave determinista', request(`votes/${voteId}`, alice, 'PATCH', { userId: alice.localId, eventId: 1, value: 1 }), 200);
await check('No duplicar votos', request('votes/arbitrary', alice, 'PATCH', { userId: alice.localId, eventId: 1, value: 1 }), 403);
await check('Crear contador', request('metadata/counters', bob, 'PATCH', { events: 1 }), 200);
await check('Incrementar contador exactamente en uno', request('metadata/counters', bob, 'PATCH', { events: 2 }), 200);
await check('No manipular contador arbitrariamente', request('metadata/counters', bob, 'PATCH', { events: 100 }), 403);
current = { ...current, startsAt: Date.now() - 7200000, endsAt: Date.now() - 1000, isFinished: true };
await check('Fixture local de evento terminado con participante confirmado y otro en espera', request('events/1', null, 'PATCH', current, true), 200);
await check('Resena de participante confirmado despues del evento', request('comments/1', alice, 'PATCH', comment(1, alice)), 200);
await check('Autor sin inscripcion no puede resenar', request('comments/2', bob, 'PATCH', comment(2, bob)), 403);
await check('Lista de espera no equivale a participacion', request('comments/2', moderator, 'PATCH', comment(2, moderator)), 403);
await check('No falsificar autor del comentario', request('comments/2', bob, 'PATCH', comment(2, alice)), 403);
await check('No agregar inscripcion despues de finalizar', request('events/1', bob, 'PATCH', { ...current, revision: 5, lastActorId: bob.localId,
  registrations: { ...current.registrations, [bob.localId]: attendance }, registrationUserIds: [...current.registrationUserIds, bob.localId] }), 403);
const next = event(2, { seriesId: 1, previousEventId: 1 });
await check('Relanzamiento crea otra edicion pendiente', request('events/2', bob, 'PATCH', next), 200);
await check('No relanzar evento de otro autor', request('events/3', alice, 'PATCH', event(3, { organizerId: alice.localId, lastActorId: alice.localId, seriesId: 1, previousEventId: 1 })), 403);
await check('No comentar nueva edicion con asistencia antigua', request('comments/2', alice, 'PATCH', comment(2, alice, 2)), 403);
const approvedNext = { ...next, moderationStatus: 'APPROVED', revision: 2, lastActorId: moderator.localId };
await check('La nueva edicion tambien requiere aprobacion', request('events/2', moderator, 'PATCH', approvedNext), 200);
const notification = { id: 2, userId: alice.localId, title: 'Nueva edicion de tu evento favorito', message: 'Elige si participaras',
  eventId: 2, contextEventId: 2, senderId: moderator.localId, createdAt: Date.now(), isRead: false };
await check('Notificacion del moderador enlazada a la nueva edicion', request('notifications/2', moderator, 'PATCH', notification), 200);
await check('No crear avisos ajenos sin contexto autorizado', request('notifications/3', alice, 'PATCH', { ...notification, id: 3, userId: bob.localId, eventId: null, contextEventId: null, senderId: alice.localId }), 403);
await check('Destinatario marca su aviso como leido', request('notifications/2', alice, 'PATCH', { ...notification, isRead: true }), 200);
await check('No alterar el contenido de un aviso recibido', request('notifications/2', alice, 'PATCH', { ...notification, isRead: true, message: 'Alterado' }), 403);
const removed = { ...current, isDeleted: true, revision: 5, lastActorId: bob.localId, registrations: {}, registrationUserIds: [], attendeeCount: 0 };
await check('Retirar edicion antigua como autor', request('events/1', bob, 'PATCH', removed), 200);
await check('Purgar la edicion retirada', request('events/1', bob, 'DELETE'), 200);
await check('Favorito conserva propietario aunque se elimine la primera edicion', request(`favorites/${favoriteId}`, alice), 200);
await check('El autor puede limpiar favorito de una serie retirada', request(`favorites/${favoriteId}`, bob, 'DELETE'), 200);
await check('Eliminar perfil propio', request(`users/${alice.localId}`, alice, 'DELETE'), 200);
console.log(`${passed} comprobaciones de reglas aprobadas.`);
