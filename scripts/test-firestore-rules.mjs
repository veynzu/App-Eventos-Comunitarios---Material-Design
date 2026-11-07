import assert from 'node:assert/strict';

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
  if (typeof input === 'number') return { integerValue: String(input) };
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
await check('Eliminar perfil propio', request(`users/${alice.localId}`, alice, 'DELETE'), 200);
console.log(`${passed} comprobaciones de reglas aprobadas.`);
