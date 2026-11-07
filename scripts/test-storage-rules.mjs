import assert from 'node:assert/strict';

const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const storageHost = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
assert.equal(authHost, '127.0.0.1:19099', 'Solo se permite Auth local.');
assert.equal(storageHost, '127.0.0.1:19199', 'Solo se permite Storage local.');
const bucket = 'demo-share-you-storage-rules.appspot.com';
const root = `http://${storageHost}/v0/b/${bucket}/o`;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=', 'base64');

async function account(email) {
  const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-test`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Local-test-1234', returnSecureToken: true })
  });
  assert.equal(response.status, 200, await response.clone().text());
  return response.json();
}

function headers(user) {
  return user ? { Authorization: `Firebase ${user.idToken}` } : {};
}

async function upload(path, user, { bytes = png, type = 'image/png', ownerId = user?.localId, metadata = true } = {}) {
  const boundary = 'share-you-rules-test-boundary';
  const description = { name: path, contentType: type, ...(metadata ? { metadata: { ownerId } } : {}) };
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Type: application/json; charset=utf-8\r\n\r\n${JSON.stringify(description)}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Type: ${type}\r\n\r\n`), bytes,
    Buffer.from(`\r\n--${boundary}--\r\n`)
  ]);
  return fetch(`${root}?name=${encodeURIComponent(path)}`, {
    method: 'POST', body, headers: {
      ...headers(user), 'Content-Type': `multipart/related; boundary=${boundary}`,
      'X-Goog-Upload-Protocol': 'multipart'
    }
  });
}

function object(path, user, method = 'GET', media = false) {
  return fetch(`${root}/${encodeURIComponent(path)}${media ? '?alt=media' : ''}`, { method, headers: headers(user) });
}

let passed = 0;
async function check(label, pending, expected) {
  const response = await pending;
  const detail = response.status === expected ? '' : await response.text();
  assert.equal(response.status, expected, `${label}: ${detail}`);
  await response.body?.cancel();
  passed++;
  console.log(`OK ${label}`);
}

const alice = await account('alice@example.test');
const bob = await account('bob@example.test');
const profile = `profiles/${alice.localId}/photo`;
const event = `events/${alice.localId}/photo`;
await check('Subir foto de perfil propia', upload(profile, alice), 200);
await check('Subir foto de evento propia', upload(event, alice), 200);
await check('Consultar metadatos para obtener downloadUrl', object(profile, alice), 200);
await check('Leer foto de otro usuario desde la app', object(profile, bob, 'GET', true), 200);
await check('No leer sin sesion mediante SDK', object(profile, null), 403);
await check('No subir sin sesion', upload(`profiles/${alice.localId}/anonymous`, null, { ownerId: alice.localId }), 403);
await check('No subir a carpeta ajena', upload(`profiles/${bob.localId}/forged`, alice), 403);
await check('No falsificar ownerId', upload(`profiles/${alice.localId}/wrong-owner`, alice, { ownerId: bob.localId }), 403);
await check('Exigir metadatos de propietario', upload(`profiles/${alice.localId}/no-owner`, alice, { metadata: false }), 403);
await check('No sobrescribir archivos existentes', upload(profile, alice), 403);
await check('No sobrescribir imagen ajena', upload(profile, bob), 403);
await check('No subir documentos', upload(`profiles/${alice.localId}/document`, alice, { type: 'application/pdf' }), 403);
await check('No subir archivos vacios', upload(`profiles/${alice.localId}/empty`, alice, { bytes: Buffer.alloc(0) }), 403);
await check('No subir a carpetas no permitidas', upload(`private/${alice.localId}/photo`, alice), 403);
await check('No subir en subcarpetas arbitrarias', upload(`profiles/${alice.localId}/nested/photo`, alice), 403);
const limit = 10 * 1024 * 1024;
await check('Aceptar el limite de 10 MB', upload(`events/${alice.localId}/limit`, alice, { bytes: Buffer.alloc(limit, 1) }), 200);
await check('Rechazar mas de 10 MB', upload(`events/${alice.localId}/too-large`, alice, { bytes: Buffer.alloc(limit + 1, 1) }), 403);
await check('No enumerar el bucket', fetch(root, { headers: headers(alice) }), 403);
await check('No eliminar imagen ajena', object(profile, bob, 'DELETE'), 403);
await check('No eliminar sin sesion', object(profile, null, 'DELETE'), 403);
await check('Eliminar imagen propia para limpieza', object(profile, alice, 'DELETE'), 204);
await check('Eliminar imagen de evento propia', object(event, alice, 'DELETE'), 204);
console.log(`${passed} comprobaciones de reglas de Storage aprobadas.`);
