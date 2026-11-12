const { test, before } = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const project = 'demo-share-you-ai';
const endpoint = `http://127.0.0.1:5001/${project}/us-central1`;

before(() => {
  assert.equal(process.env.GCLOUD_PROJECT, project, 'Estas pruebas solo pueden ejecutarse en un proyecto demo.');
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9099');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
});
async function account(role = 'PARTICIPANT', status = 'ACTIVE', verification = 'NOT_REQUIRED') {
  const auth = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `ai-${randomUUID()}@example.test`, password: 'LocalTest123!', returnSecureToken: true }),
  });
  assert.equal(auth.status, 200);
  const user = await auth.json();
  const fixture = await fetch(`http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents/users/${user.localId}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ fields: { accountStatus: { stringValue: status }, role: { stringValue: role }, organizerVerificationStatus: { stringValue: verification } } }),
  });
  assert.equal(fixture.status, 200);
  return user.idToken;
}
async function call(name, token, data = {}) {
  const response = await fetch(`${endpoint}/${name}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ data }),
  });
  return { status: response.status, body: await response.json() };
}
test('aiStatus rechaza acceso sin sesión', async () => {
  const response = await call('aiStatus');
  assert.equal(response.status, 401);
  assert.equal(response.body.error.status, 'UNAUTHENTICATED');
});
test('una cuenta activa puede consultar el estado sin App Check y la IA local aparece desactivada', async () => {
  const response = await call('aiStatus', await account());
  assert.equal(response.status, 200);
  assert.equal(response.body.result.enabled, false);
  assert.equal(response.body.result.modelConfigured, false);
  assert.equal(response.body.result.preparationOnly, true);
  assert.equal(response.body.result.appCheckEnforced, false);
  assert.equal(response.body.result.contractVersion, 1);
});
test('aiStatus rechaza una cuenta suspendida', async () => {
  const response = await call('aiStatus', await account('ADMINISTRATOR', 'SUSPENDED'));
  assert.equal(response.status, 403);
});
test('no simula moderación aprobada mientras Gemini no está configurado', async () => {
  const response = await call('analyzeText', await account(), { text: 'Invitación al evento', language: 'es' });
  assert.equal(response.status, 400);
  assert.equal(response.body.error.status, 'FAILED_PRECONDITION');
});
test('un participante no puede analizar eventos', async () => {
  const response = await call('analyzeEventDraft', await account(), {});
  assert.equal(response.status, 403);
});
test('un organizador pendiente tampoco puede analizar eventos', async () => {
  const response = await call('analyzeEventDraft', await account('ORGANIZER', 'ACTIVE', 'PENDING'), {});
  assert.equal(response.status, 403);
});
test('un organizador verificado recibe estado pendiente de configuración, no una falsa aprobación', async () => {
  const now = Date.now();
  const response = await call('analyzeEventDraft', await account('ORGANIZER', 'ACTIVE', 'VERIFIED'), {
    title: 'Jornada comunitaria', description: 'Una actividad con los vecinos', category: 'COMMUNITY',
    latitude: 4.5339, longitude: -75.6811, startsAt: now + 60000, endsAt: now + 120000,
  });
  assert.equal(response.status, 400);
  assert.equal(response.body.error.status, 'FAILED_PRECONDITION');
});
