# Preparación de Cloud Functions e IA — Share You

## Estado y alcance

Proyecto: `share-you-6a706`. Región: `us-central1`. Backend: Cloud Functions de segunda generación, TypeScript y Node.js 22. La app Android utiliza el SDK `firebase-functions` incluido en el Firebase BoM existente y Hilt proporciona `FirebaseFunctions.getInstance("us-central1")`.

Están desplegadas `aiStatus`, `analyzeText` y `analyzeEventDraft`. Exigen sesión Firebase y perfil activo; el análisis de eventos exige además organizador verificado o administrador. App Check está desactivado para esta entrega académica y no requiere registrar dispositivos. `aiStatus` devuelve `enabled=true`, `modelConfigured=true` y `appCheckEnforced=false`. Se conserva ese campo del contrato para compatibilidad.

Son **prevalidaciones**, todavía no están conectadas a los formularios ni autorizan escrituras. Por eso el contrato conserva `preparationOnly=true`. No equivalen a implementar por completo los tres requisitos académicos: la publicación actual y la aprobación administrativa conservan su comportamiento.

Gemini se consume desde el proyecto independiente **Share You IA**, ID `gen-lang-client-0426384814`, sin cuenta de facturación vinculada, con `gemini-3.1-flash-lite`. Firebase y Cloud Functions permanecen en `share-you-6a706` con Blaze. La clave de Gemini se guarda exclusivamente en Secret Manager de este último proyecto. El 12 de noviembre de 2026 se comprobaron generación permitida, rechazo con alternativa, comparación semántica y relación imagen/texto usando la versión 3 del secreto. El uso gratuito está sujeto a las cuotas del proyecto/modelo; los costos propios de Firebase siguen siendo independientes.

## Archivos

- `firebase.json`: asociación de Functions, reglas existentes y puertos de emuladores.
- `.firebaserc`: proyecto predeterminado de despliegue.
- `functions/src/index.ts`: funciones invocables, permisos, cuota diaria y lectura de candidatos/imágenes.
- `functions/src/analysis.ts`: contratos, validaciones y política inicial de moderación.
- `functions/src/gemini.ts`: adaptador preparado con Google Gen AI SDK; imagen/texto y respuesta JSON validada.
- `functions/package-lock.json`: versiones reproducibles de dependencias.
- `app/src/main/java/com/eventoscomunitarios/app/domain/model/ContentAnalysis.kt`: modelos de resultados.
- `app/src/main/java/com/eventoscomunitarios/app/domain/repository/ContentAnalysisRepository.kt`: contrato Android.
- `app/src/main/java/com/eventoscomunitarios/app/domain/usecase/ContentAnalysisUseCases.kt`: casos de uso.
- `app/src/main/java/com/eventoscomunitarios/app/core/data/FirebaseContentAnalysisRepository.kt`: llamadas autenticadas a Functions.
- `app/src/main/java/com/eventoscomunitarios/app/core/data/InMemoryContentAnalysisRepository.kt`: fixtures explícitas para pruebas; no aprueba automáticamente.

`DashboardNavigation.kt` conserva la navegación. El análisis pertenece a casos de uso, repositorios y backend.

## Configuración de infraestructura

Se utilizan `cloudfunctions.googleapis.com`, `cloudbuild.googleapis.com`, `artifactregistry.googleapis.com`, `run.googleapis.com`, `eventarc.googleapis.com`, `pubsub.googleapis.com` y `secretmanager.googleapis.com`. La API de Gemini `generativelanguage.googleapis.com` está disponible en el proyecto de IA. Cloud Billing API permite comprobar el plan Blaze. Firebase CLI puede habilitar además `firebaseextensions.googleapis.com` durante el análisis del despliegue. Las APIs de App Check/Play Integrity habilitadas anteriormente no son un requisito de esta versión.

Cuenta de ejecución dedicada: `share-you-functions@share-you-6a706.iam.gserviceaccount.com`.

- `roles/datastore.user` en el proyecto: lectura de perfiles/candidatos y cuota `aiUsage/{uid}`. El Admin SDK no utiliza las reglas del cliente; cada función valida sesión y perfil.
- `roles/storage.objectViewer` en el bucket `share-you-6a706.firebasestorage.app`: lectura de imágenes por el backend. El código acepta exclusivamente `event-drafts/{uid}/{archivo}` del solicitante y nunca acepta cédulas ni URL externas.
- `roles/secretmanager.secretAccessor` sobre `GEMINI_API_KEY`, asignado durante el despliegue. No se han creado claves JSON de cuentas de servicio. Una rotación del secreto requiere volver a desplegar las funciones consumidoras.

El bucket actual no utiliza acceso uniforme. No se cambia esa configuración para introducir condiciones IAM por prefijo: la restricción de rutas está implementada en el código de análisis. La cuenta de ejecución tiene lectura del bucket completo.

Límites iniciales: cero instancias mínimas, dos máximas por función, concurrencia ocho y timeout de 60 segundos. Cada cuenta dispone de 20 análisis diarios por defecto, configurables entre 1 y 100. Un intento al proveedor consume cuota aunque este falle. Estos límites **no son un límite monetario global** ni sustituyen las cuotas del proveedor. El proyecto de Gemini actual permanece sin facturación; Firebase conserva su facturación propia.

## Comprobar y desplegar la preparación

Desde la raíz, con Firebase CLI, Node y un JDK completo:

```powershell
npm install -g firebase-tools
npm --prefix functions ci
.\scripts\test-functions.ps1
.\scripts\deploy-functions.ps1
```

El script de despliegue utiliza por defecto `--only functions:share-you-ai:aiStatus --project share-you-6a706`. Con `-Stage Analysis` despliega las tres funciones y comprueba que IA y modelo estén configurados. No despliega reglas ni elimina funciones. `firebase init` no es necesario: la configuración ya está creada. También utiliza Google Cloud CLI, instalado en este equipo, para desactivar la comprobación IAM de invocación de los servicios Cloud Run correspondientes: el token del usuario lo verifica Firebase Auth dentro de cada función. Así se evita rechazar un JWT Firebase por no ser una identidad IAM de Google Cloud. Sin sesión o con una cuenta suspendida, las funciones rechazan el acceso.

Se configuró limpieza de imágenes de compilación del repositorio `gcf-artifacts` de `us-central1` a los siete días. No elimina imágenes de publicaciones en Firebase Storage ni afecta los contenedores en ejecución. Para repetir esa configuración:

```powershell
firebase functions:artifacts:setpolicy --days 7 --location us-central1 --project share-you-6a706 --non-interactive --force
```

El código se compila para Node.js 22; `.nvmrc` y `.node-version` registran esa versión. La máquina también puede tener Node.js 24 instalado: Firebase Emulator Suite advertirá si lo utiliza en lugar de 22. Las pruebas unitarias pueden verificarse con la versión de producción sin reemplazar el Node global:

```powershell
Set-Location functions
npm exec --yes --package=node@22 -- node --test test/analysis.test.cjs
Set-Location ..
```

`test-functions.ps1` usa exclusivamente `demo-share-you-ai`: Functions 5001, Auth 9099 y Firestore 8080, todos locales. Comprueba los puertos y no detiene servicios ajenos. No ejecutarlo junto a `start-firebase-emulators.ps1`. Crea y retira un `.env.local` que mantiene la IA desactivada; no consulta Gemini ni Secret Manager. Detiene los emuladores que inició al terminar. Con `-UnitOnly` ejecuta compilación y pruebas unitarias sin emuladores.

## Credenciales, configuración y dispositivos

La versión 3 de `GEMINI_API_KEY` fue validada con una generación real. El bloqueo HTTP 402 de la credencial anterior se resolvió usando el proyecto independiente gratuito; no se realizó una recarga de prepago.

Para reemplazar la clave posteriormente, crearla en el proyecto de IA y guardarla en el Secret Manager del backend mediante la terminal, **sin enviarla por chat ni guardarla en el APK**:

```powershell
firebase functions:secrets:set GEMINI_API_KEY --project share-you-6a706
```

El archivo local `functions/.env.share-you-6a706` contiene `AI_ENABLED=true`, `GEMINI_MODEL=gemini-3.1-flash-lite` y `AI_DAILY_LIMIT=20`. Está excluido de Git y no contiene la clave. La plantilla `.env.example` conserva la IA desactivada hasta configurar el entorno. Reiniciar/recompilar no sustituye un nuevo despliegue:

```powershell
.\scripts\deploy-functions.ps1 -Stage Analysis
```

Al activar `AI_ENABLED`, las funciones de análisis vinculan el secreto; Firebase CLI requiere que exista y configura el acceso de la cuenta de ejecución.

### Acceso del equipo

Android no instala proveedores de App Check y las tres funciones declaran `enforceAppCheck: false` explícitamente. No hay variable de entorno que active este requisito. Se retiraron los proveedores debug/Play Integrity, la prueba y el script de registro asociados. Compañeros y docente pueden utilizar sus instalaciones con Firebase Authentication y los roles de la app, sin registrar tokens de depuración.

Authentication, Firestore y Storage tienen App Check en modo `UNENFORCED`; sus reglas y comprobaciones de autorización se mantienen. La cuota diaria de IA y el secreto del proveedor permanecen en el servidor.

## Trabajo funcional pendiente después de esta preparación

- Conectar los resultados a crear/editar eventos y comentar: estados de carga, errores persistentes, sugerencias y lista de duplicados, con textos ES/EN.
- Preparar subida temporal privada y limpieza en `event-drafts/{uid}/{archivo}`. Esa carpeta **todavía no está habilitada en las reglas ni en FirebaseMediaRepository**; no se suben imágenes desde las pantallas actuales a esa ruta.
- Trasladar la escritura final de contenido al backend y protegerla en las reglas. Debe validar exactamente el texto y generación de imagen que guarda, y repetir el análisis al editar. Las prevalidaciones no impiden por sí solas saltarse una comprobación del cliente.
- Añadir “Aportar información” al evento existente y su almacenamiento/permisos. Las reseñas actuales siguen limitadas a participantes confirmados después de finalizar el evento.
- Evaluar calidad de moderación, relación imagen/categoría/descripción y comparación semántica con un conjunto de ejemplos reales, incluidos falsos positivos y ediciones en fechas distintas.

La búsqueda preparada utiliza el índice simple existente de `latitude`, distancia Haversine de 1 km y eventos aprobados no terminados con ubicación confirmada. Lee hasta 250 documentos de la franja de latitud y compara hasta 20 candidatos cercanos. Si se supera un límite, devuelve `candidatesTruncated=true`: no debe interpretarse como ausencia de duplicados. Para mayor volumen, sustituir el prefiltro por geohash/índices y considerar embeddings. No se ha migrado ninguna publicación existente.

## Pruebas

- `functions/test/analysis.test.cjs`: validación de entradas, fechas, rutas privadas, roles y respuestas del modelo, IDs de duplicados y distancia. No evalúa la calidad real de Gemini.
- `functions/test/emulator.integration.cjs`: acceso sin sesión, perfil activo/suspendido, organizador pendiente/verificado, participante y respuesta de IA sin configurar. Usa Auth/Firestore/Functions locales.
- `ContentAnalysisPreparationTest.kt`: estado desactivado, ausencia de aprobaciones ficticias, rechazo con alternativa, validación anterior al proveedor y cancelación de coroutines.

Se probaron directamente contra Gemini un texto permitido, un rechazo con alternativa, un duplicado redactado de otra manera, una edición en otra fecha y una imagen sintética relacionada/no relacionada con la categoría. Estos ejemplos no sustituyen una evaluación amplia de calidad. Los emuladores no certifican permisos IAM ni facturación de producción. El recorrido completo de publicar desde los formularios sigue pendiente.

Verificación de esta preparación: compilación de APK y APK de pruebas correcta; 58 pruebas JVM sin fallos (cuatro nuevas), 12 pruebas unitarias de Functions y siete de integración local aprobadas. También se comprobó `aiStatus` en producción con sesión válida, sin sesión y con perfil suspendido; la cuenta y perfil temporales usados para esa comprobación se eliminaron. Registros locales: `build/cloud-functions-android-build.log`, `build/cloud-functions-unit-tests.log`, `build/cloud-functions-emulator-tests.log`, `build/cloud-functions-production-smoke.json` y `build/cloud-functions-artifact-policy.log`.

Historial de activación del proyecto gratuito: debug/APK de pruebas y Kotlin de release compilados; 58 pruebas JVM y 19 pruebas locales del backend aprobadas. Se verificaron los servicios de análisis y sus permisos en producción con fixtures temporales, posteriormente eliminadas. La comprobación de App Check de esa versión fue retirada al simplificar la entrega académica; no corresponde al estado actual.

Registros de esta activación: `build/gemini-key-validation.json`, `build/gemini-provider-smoke.json`, `build/appcheck-android-build.log`, `build/appcheck-sdk-runtime-tests.log`, `build/gemini-free-emulator-tests.log`, `build/gemini-free-analysis-deploy.log` y `build/gemini-callable-smoke.json`. Los informes no contienen claves ni tokens.

Verificación posterior al retiro de App Check: 58 pruebas JVM, 12 pruebas unitarias de Functions y siete pruebas de integración local aprobadas. Se compiló la APK debug, la APK de pruebas y Kotlin de release, y se redesplegaron las tres funciones. Nueve casos en producción, todos sin enviar token App Check, comprobaron acceso con sesión activa, análisis de texto/borrador, moderación con alternativa, rechazo sin sesión, permisos de participante/organizador pendiente, rechazo de cuenta suspendida y cuota diaria. Las fixtures temporales de Auth, perfil y cuota se eliminaron. Registros actuales: `build/disable-appcheck-android-build.log`, `build/disable-appcheck-functions-tests.log`, `build/disable-appcheck-deploy.log` y `build/disable-appcheck-production-smoke.json`.

Referencias: [Functions](https://firebase.google.com/docs/functions/get-started), [funciones invocables](https://firebase.google.com/docs/functions/callable), [secretos](https://firebase.google.com/docs/functions/config-env#secret_parameters), [Gemini](https://ai.google.dev/gemini-api/docs/image-understanding).
