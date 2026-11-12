# Cloud Functions e IA — Share You

## Estado actual

Proyecto Firebase: `share-you-6a706`. Región: `us-central1`. Backend: Functions de segunda generación, TypeScript y Node.js 22. Modelo: `gemini-3.1-flash-lite`, desde el proyecto gratuito independiente `gen-lang-client-0426384814`. Firebase conserva Blaze; sus costos son independientes de la cuota gratuita del modelo.

Las tres funciones están desplegadas: `aiStatus`, `analyzeText` y `analyzeEventDraft`. **Ya están conectadas a crear, editar y volver a lanzar eventos y publicar comentarios/aportes.** La revisión administrativa de las publicaciones continúa después del análisis. App Check permanece desactivado; Authentication, Firestore y Storage están en modo `UNENFORCED`.

`aiStatus` devuelve `enabled=true`, `modelConfigured=true`, `appCheckEnforced=false`, `preparationOnly=false` y `contractVersion=1`.

El detalle del antes/después, las rutas Android, interfaces gráficas y pruebas está en [IMPLEMENTACION_IA_INFORME.md](../IMPLEMENTACION_IA_INFORME.md).

## Flujo de validación y guardado

1. El ViewModel valida el formulario y llama a `ContentAnalysisUseCases`.
2. Si hay foto, se sube privadamente a `event-drafts/{uid}/{uuid}`. Para editar/reutilizar una foto de Firebase, se prepara también un borrador privado.
3. La función valida sesión, perfil activo, rol, entrada y cuota. Los eventos requieren administrador u organizador verificado.
4. Gemini evalúa texto, relevancia de imagen y posibles duplicados cercanos. El servidor valida el JSON devuelto.
5. Si el contenido se permite, Functions crea `aiReviews/{reviewId}`, válido durante 15 minutos. Si había imagen, guarda una copia de los bytes analizados en `validated-events/{uid}/{reviewId}` y devuelve su URL. Los clientes no pueden crear ni reemplazar esa copia.
6. Los rechazos muestran motivos y alternativa; las coincidencias muestran eventos existentes y la opción de aportar información. Publicar un evento diferente requiere confirmación explícita.
7. El repositorio escribe evento/comentario y `aiReviewUses/{reviewId}` en una misma transacción. Las reglas comprueban usuario, contenido, vigencia, destino y primer uso. Una validación no sirve para otra versión del texto o de la foto.

El SDK Android sigue escribiendo el documento final, **bajo reglas que exigen la validación emitida por el servidor**. Functions no guarda directamente el evento. No es una comprobación exclusivamente visual.

Los borradores se eliminan al terminar el análisis, incluso cuando falla. Cambiar un formulario invalida su resultado y solicita retirar la copia aprobada pendiente. Las copias aprobadas abandonadas al cerrar la app y el histórico de comprobantes necesitan una política posterior de limpieza automática; su caducidad ya impide usarlos para publicar.

Los aportes usan `comments` con `isContribution=true` en eventos aprobados que aún no terminan. Las reseñas normales conservan el requisito de evento terminado e inscripción confirmada. Ambos textos pasan por IA.

## Archivos principales

- `src/index.ts`: funciones invocables, autenticación, cuota, candidatos, imágenes y comprobantes.
- `src/analysis.ts`: contratos, validadores, distancias y política de contenido.
- `src/gemini.ts`: adaptador Google Gen AI, solicitudes multimodales y salida JSON Schema.
- `../firestore.rules`: exige comprobantes y consumo atómico.
- `../storage.rules`: borradores privados y copias validadas que solo crea el backend.
- `../scripts/deploy-functions.ps1`: despliegue conjunto y configuración Cloud Run.
- `../scripts/test-functions.ps1`: pruebas locales aisladas.

## Infraestructura y permisos

Cuenta de ejecución: `share-you-functions@share-you-6a706.iam.gserviceaccount.com`.

- `roles/datastore.user` en el proyecto: perfiles, candidatos, cuota y comprobantes.
- `roles/storage.objectUser` en `share-you-6a706.firebasestorage.app`: lectura del borrador y creación/eliminación de copias validadas. La lectura anterior con `objectViewer` era insuficiente para guardar la copia. El código restringe las rutas; no procesa cédulas.
- `roles/secretmanager.secretAccessor` para `GEMINI_API_KEY`, vinculado por el despliegue. No hay claves JSON de cuenta de servicio en Android.

APIs: Cloud Functions, Cloud Build, Artifact Registry, Cloud Run, Eventarc, Pub/Sub, Storage y Secret Manager. Gemini usa Generative Language API en su proyecto independiente. Firebase CLI comprueba las APIs durante el despliegue.

Límites: `minInstances=0`, `maxInstances=2`, concurrencia 8, memoria 256 MiB y timeout 60 segundos. El cliente Gemini espera hasta 45 segundos y Android hasta 70. `AI_DAILY_LIMIT=20` cuenta intentos por UID y día UTC, incluso si falla una llamada al proveedor; el rango admitido es 1–100. No es un presupuesto monetario global.

## Configuración y despliegue

Desde la raíz del repositorio, con Node 22, npm, Firebase CLI y Google Cloud CLI:

```powershell
npm install -g firebase-tools
firebase.cmd login
gcloud.cmd auth login
npm --prefix functions ci
```

El proyecto ya está inicializado: no volver a ejecutar `firebase init`. Para un equipo administrador nuevo, copiar `.env.example` a `.env.share-you-6a706` solo si todavía no existe. La configuración operativa es:

```properties
AI_ENABLED=true
GEMINI_MODEL=gemini-3.1-flash-lite
AI_DAILY_LIMIT=20
```

La clave no va en ese archivo, en `local.properties` ni en Git. Si se necesita crear/rotar el secreto:

```powershell
firebase.cmd functions:secrets:set GEMINI_API_KEY --project share-you-6a706
```

Se introduce en el prompt de la CLI. Rotar el secreto requiere volver a desplegar sus consumidores. La credencial actual se validó usando la versión 3 del secreto; el HTTP 402 de una credencial anterior se resolvió con el proyecto gratuito independiente, sin una recarga de prepago.

Comprobar y desplegar:

```powershell
.\scripts\test-functions.ps1
.\scripts\deploy-functions.ps1 -Stage Analysis
firebase.cmd functions:list --project share-you-6a706
```

**`Analysis` es ahora la etapa predeterminada.** Publica `functions:share-you-ai,firestore:rules,storage`, comprueba modelo/configuración y configura los servicios Cloud Run `aistatus`, `analyzetext` y `analyzeeventdraft` con `--no-invoker-iam-check`. La sesión Firebase se valida dentro de cada función. La etapa explícita `Preparation` publica únicamente `aiStatus` y no actualiza el resto del flujo.

`firebase.json` ejecuta `npm --prefix "$RESOURCE_DIR" run check` antes de publicar las funciones. Se compila `src/` a `lib/`; no se edita `lib/` manualmente. Una subida a Git no despliega funciones ni instala una APK nueva.

Para una función nueva, exportarla desde `src/index.ts`, validar permisos/entradas, añadir pruebas, desplegar y configurar el servicio Cloud Run generado. Si se utiliza el script, agregar el nombre del servicio a su lista `$services`. Ejemplo de filtro individual:

```powershell
firebase.cmd deploy --only functions:share-you-ai:nombreDeLaFuncion --project share-you-6a706
```

Artifact Registry conserva la política de siete días para imágenes antiguas de compilación de `gcf-artifacts`; esa limpieza no elimina fotos de usuarios.

## Pruebas

```powershell
.\scripts\test-functions.ps1 -UnitOnly
.\scripts\test-functions.ps1
.\scripts\test-firestore-rules.ps1
```

El primer comando ejecuta TypeScript y 12 pruebas unitarias. El segundo añade 17 pruebas de integración locales: acceso al servicio, publicación con comprobante, alteraciones, caducidad, usuario ajeno, duplicados, aportes, compatibilidad con inscripciones antiguas y Storage privado/inalterable. El tercero conserva 81 comprobaciones de permisos sociales.

`test-functions.ps1` usa únicamente `demo-share-you-ai`: Functions 5001, Auth 9099, Firestore 8080 y Storage 9199. Requiere un JDK completo 21 o superior y coloca `JAVA_HOME/bin` en PATH. Comprueba puertos, rechaza un `.secret.local` real y crea/retira `.env.local` con la IA desactivada. No consume Gemini ni necesita su clave. No ejecutarlo simultáneamente con `start-firebase-emulators.ps1`.

Validación del 12/11/2026: 58 pruebas JVM sin fallos; compilación debug, APK de pruebas y Kotlin release correctas; siete pruebas nuevas de los formularios en Android, seis de SDK Firebase local y pruebas de regresión de eventos. Se verificaron diez grupos de casos en producción con Gemini y las reglas publicadas, sin App Check. Se limpiaron las 13 operaciones de datos/archivos temporales de esa prueba.

Evidencias locales: `build/ai-connection-android.log`, `build/ai-connection-device-tests.log`, `build/ai-connection-sdk-integration.log`, `build/ai-connection-functions.log`, `build/ai-connection-firestore-regression.log`, `build/ai-connection-deploy.log` y `build/ai-connection-production-smoke.json`. Las salidas `build/` no se comparten por Git; el código de las suites sí.

## Equipo y ejecución de Android

El compañero necesita la misma versión del código, su `local.properties` y `app/google-services.json`, sincronizar Gradle e instalar la APK actual. No necesita una clave Gemini, permisos para desplegar ni registrar App Check. El rol de su usuario dentro de la app es distinto de sus permisos como visualizador de Firebase Console.

**La APK anterior debe actualizarse:** las reglas nuevas rechazan las escrituras de contenido que no incluyan un comprobante de IA válido y su consumo atómico.
