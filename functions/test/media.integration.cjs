const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const bucket = 'demo-share-you-ai.appspot.com';
const root = `http://127.0.0.1:9199/v0/b/${bucket}/o`;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=', 'base64');
async function account() {
  assert.equal(process.env.FIREBASE_STORAGE_EMULATOR_HOST, '127.0.0.1:9199');
  const r = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-test', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: `image-${randomUUID()}@example.test`, password: 'LocalTest123!', returnSecureToken: true }) });
  assert.equal(r.status, 200); return r.json();
}
async function upload(path, user, type = 'image/png', admin = false) {
  const boundary = 'share-you-image-boundary';
  const metadata = { name: path, contentType: type, metadata: { ownerId: user.localId } };
  return fetch(`${root}?name=${encodeURIComponent(path)}`, { method: 'POST', headers: {
    Authorization: admin ? 'Bearer owner' : `Firebase ${user.idToken}`, 'Content-Type': `multipart/related; boundary=${boundary}`, 'X-Goog-Upload-Protocol': 'multipart' },
    body: Buffer.concat([Buffer.from(`--${boundary}\r\nContent-Type: application/json; charset=utf-8\r\n\r\n${JSON.stringify(metadata)}\r\n--${boundary}\r\nContent-Type: ${type}\r\n\r\n`), png, Buffer.from(`\r\n--${boundary}--\r\n`)]) });
}
async function object(path, user, method = 'GET') {
  return fetch(`${root}/${encodeURIComponent(path)}`, { method, headers: { Authorization: `Firebase ${user.idToken}` } });
}
test('los borradores son privados, admiten formatos soportados y no permiten sobrescritura', async () => {
  const owner = await account(), other = await account();
  const path = `event-drafts/${owner.localId}/${randomUUID()}`;
  assert.equal((await upload(path, owner)).status, 200);
  assert.equal((await object(path, owner)).status, 200);
  assert.equal((await object(path, other)).status, 403);
  assert.equal((await upload(path, owner)).status, 403);
  assert.equal((await upload(`event-drafts/${other.localId}/${randomUUID()}`, owner)).status, 403);
  assert.equal((await upload(`event-drafts/${owner.localId}/${randomUUID()}`, owner, 'image/gif')).status, 403);
  assert.equal((await object(path, owner, 'DELETE')).status, 204);
});
test('solo el backend puede crear o reemplazar una imagen ya validada', async () => {
  const owner = await account(), viewer = await account();
  const path = `validated-events/${owner.localId}/${randomUUID()}`;
  assert.equal((await upload(path, owner)).status, 403);
  assert.equal((await upload(path, owner, 'image/png', true)).status, 200);
  assert.equal((await object(path, viewer)).status, 200);
  assert.equal((await upload(path, owner)).status, 403);
  assert.equal((await object(path, owner, 'DELETE')).status, 204);
  assert.equal((await upload(path, owner)).status, 403);
});
