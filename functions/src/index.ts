import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { defineSecret } from "firebase-functions/params";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { distanceKm, parseEventDraft, parseTextInput, requireActiveProfile, type EventDraft, type NearbyEvent } from "./analysis";
import { GeminiAnalysis } from "./gemini";

initializeApp();
setGlobalOptions({ region: "us-central1", minInstances: 0, maxInstances: 2, memory: "256MiB", timeoutSeconds: 60, concurrency: 8,
  serviceAccount: "share-you-functions@share-you-6a706.iam.gserviceaccount.com" });
const db = getFirestore();
const enabled = () => process.env.AI_ENABLED === "true";
const model = () => (process.env.GEMINI_MODEL ?? "").trim();
// La preparación no declara un secreto obligatorio ni crea credenciales ficticias.
// Al activar AI_ENABLED en el despliegue, las funciones de análisis lo vinculan a Secret Manager.
const geminiKey = enabled() ? defineSecret("GEMINI_API_KEY") : null;

async function activeUid(request: CallableRequest, publishing = false): Promise<string> {
  if (!request.auth) throw new HttpsError("unauthenticated", "Inicia sesión para utilizar este servicio.");
  const profile = await db.collection("users").doc(request.auth.uid).get();
  requireActiveProfile(profile.data(), publishing);
  return request.auth.uid;
}
function provider(): GeminiAnalysis {
  if (!enabled() || !model()) throw new HttpsError("failed-precondition", "El análisis con IA todavía no está configurado.");
  const key = geminiKey?.value();
  if (!key || key === "REEMPLAZAR_LOCALMENTE") throw new HttpsError("failed-precondition", "Falta configurar el proveedor de IA.");
  return new GeminiAnalysis(key, model());
}
async function reserveQuota(uid: string): Promise<void> {
  const rawLimit = Number(process.env.AI_DAILY_LIMIT ?? "20");
  if (!Number.isInteger(rawLimit) || rawLimit < 1 || rawLimit > 100) throw new HttpsError("failed-precondition", "Configuración de cuota inválida.");
  const day = new Date().toISOString().slice(0, 10);
  const ref = db.collection("aiUsage").doc(uid);
  await db.runTransaction(async tx => {
    const before = (await tx.get(ref)).data();
    const count = before?.day === day ? Number(before.count) : 0;
    if (!Number.isInteger(count) || count >= rawLimit) throw new HttpsError("resource-exhausted", "Alcanzaste el límite diario de análisis.");
    tx.set(ref, { day, count: count + 1 });
  });
}
async function analyze<T>(work: () => Promise<T>): Promise<T> {
  try { return await work(); }
  catch (error) {
    if (error instanceof HttpsError) throw error;
    // No incluir prompts, documentos, claves ni el cuerpo de error del proveedor en logs o respuestas.
    throw new HttpsError("unavailable", "No se pudo completar el análisis. Conserva el borrador e inténtalo de nuevo.");
  }
}

/** Única función del despliegue inicial. No consulta Gemini ni necesita su clave. */
export const aiStatus = onCall({ enforceAppCheck: false }, async request => {
  await activeUid(request);
  return { provider: "gemini", enabled: enabled(), modelConfigured: Boolean(model()),
    appCheckEnforced: false, preparationOnly: true, region: "us-central1", contractVersion: 1 };
});

/** Prevalidaciones: no son autorizaciones de publicación y no guardan eventos ni comentarios. */
export const analyzeText = onCall({ enforceAppCheck: false, secrets: geminiKey ? [geminiKey] : [] }, request => analyze(async () => {
  const uid = await activeUid(request);
  const input = parseTextInput(request.data);
  const client = provider();
  await reserveQuota(uid);
  return client.text(input);
}));

async function nearbyEvents(draft: EventDraft): Promise<{ events: NearbyEvent[]; truncated: boolean }> {
  // Usa la latitude ya existente: no modifica ni migra documentos de eventos.
  // Límite de lectura explícito. Una búsqueda truncada JAMÁS demuestra ausencia de duplicados.
  const radius = 1;
  const delta = radius / 110.5;
  const snapshot = await db.collection("events").where("latitude", ">=", Math.max(-90, draft.latitude - delta))
    .where("latitude", "<=", Math.min(90, draft.latitude + delta)).orderBy("latitude").limit(251).get();
  const candidates: NearbyEvent[] = [];
  for (const doc of snapshot.docs.slice(0, 250)) {
    const e = doc.data();
    if (e.id === draft.sourceEventId || e.isDeleted || e.moderationStatus !== "APPROVED" || !e.locationConfirmed ||
        e.isFinished || e.endsAt <= Date.now() || !Number.isInteger(e.id) || typeof e.title !== "string" ||
        typeof e.description !== "string" || typeof e.category !== "string" ||
        !Number.isFinite(e.latitude) || !Number.isFinite(e.longitude) || !Number.isFinite(e.startsAt) || !Number.isFinite(e.endsAt)) continue;
    if (distanceKm(draft, e as NearbyEvent) > radius) continue;
    candidates.push({ eventId: e.id, title: e.title, description: e.description.slice(0, 1500), category: e.category,
      startsAt: e.startsAt, endsAt: e.endsAt, latitude: e.latitude, longitude: e.longitude });
  }
  candidates.sort((a, b) => distanceKm(draft, a) - distanceKm(draft, b));
  return { events: candidates.slice(0, 20), truncated: snapshot.size > 250 || candidates.length > 20 };
}
async function imageFor(draft: EventDraft): Promise<{ bytes: Buffer; mimeType: string } | undefined> {
  if (!draft.imagePath) return undefined;
  const file = getStorage().bucket().file(draft.imagePath);
  const [metadata] = await file.getMetadata();
  const size = Number(metadata.size);
  if (!Number.isFinite(size) || size <= 0 || size > 10 * 1024 * 1024 ||
      !["image/jpeg", "image/png", "image/webp"].includes(metadata.contentType ?? "")) throw new HttpsError("invalid-argument", "Selecciona una imagen JPEG, PNG o WebP de hasta 10 MB.");
  // Leer la generación verificada evita sustituir el archivo entre metadatos y descarga.
  const [bytes] = await getStorage().bucket().file(draft.imagePath, { generation: metadata.generation }).download({ validation: "crc32c" });
  return { bytes, mimeType: metadata.contentType! };
}
export const analyzeEventDraft = onCall({ enforceAppCheck: false, secrets: geminiKey ? [geminiKey] : [] }, request => analyze(async () => {
  const uid = await activeUid(request, true);
  const draft = parseEventDraft(request.data, uid);
  const client = provider();
  if (draft.sourceEventId !== null) {
    const source = (await db.collection("events").doc(String(draft.sourceEventId)).get()).data();
    if (!source || source.organizerId !== uid) throw new HttpsError("permission-denied", "El evento de origen no te pertenece.");
  }
  await reserveQuota(uid);
  const [nearby, image] = await Promise.all([nearbyEvents(draft), imageFor(draft)]);
  const assessment = await client.event(draft, nearby.events, image);
  return { ...assessment, candidatesTruncated: nearby.truncated, candidates: nearby.events.map(e => ({
    eventId: e.eventId, title: e.title, startsAt: e.startsAt, distanceKm: distanceKm(draft, e),
  })) };
}));
