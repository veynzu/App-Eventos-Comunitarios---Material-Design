# Preparación de Cloud Functions e IA — Share You

## Estado y alcance

Proyecto: `share-you-6a706`. Región: `us-central1`. Backend: Cloud Functions de segunda generación, TypeScript y Node.js 22. La app Android utiliza el SDK `firebase-functions` incluido en el Firebase BoM existente y Hilt proporciona `FirebaseFunctions.getInstance("us-central1")`.

Están desplegadas `aiStatus`, `analyzeText` y `analyzeEventDraft`. Exigen sesión Firebase, perfil activo y App Check válido; el análisis de eventos exige además organizador verificado o administrador. `aiStatus` devuelve `enabled=true`, `modelConfigured=true` y `appCheckEnforced=true` con la configuración actual.

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
- `app/src/debug/java/com/eventoscomunitarios/app/core/util/AppCheckSetup.kt`: proveedor App Check debug, incluido solo en debug.
- `app/src/release/java/com/eventoscomunitarios/app/core/util/AppCheckSetup.kt`: proveedor Play Integrity de release.
- `scripts/register-appcheck-debug-token.py`: registra el dispositivo de desarrollo sin imprimir ni guardar su secreto.

`DashboardNavigation.kt` conserva la navegación. El análisis pertenece a casos de uso, repositorios y backend.

## Configuración de infraestructura

Se habilitan `cloudfunctions.googleapis.com`, `cloudbuild.googleapis.com`, `artifactregistry.googleapis.com`, `run.googleapis.com`, `eventarc.googleapis.com`, `pubsub.googleapis.com`, `secretmanager.googleapis.com`, `firebaseappcheck.googleapis.com` y `playintegrity.googleapis.com`. La API de Gemini `generativelanguage.googleapis.com` está disponible en el proyecto de IA. Cloud Billing API permite comprobar el plan Blaze. Firebase CLI puede habilitar además `firebaseextensions.googleapis.com` durante el análisis del despliegue.

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

El script de despliegue utiliza por defecto `--only functions:share-you-ai:aiStatus --project share-you-6a706`. Con `-Stage Analysis` despliega las tres funciones y comprueba que IA, modelo y App Check estén configurados. No despliega reglas ni elimina funciones. `firebase init` no es necesario: la configuración ya está creada. También utiliza Google Cloud CLI, instalado en este equipo, para desactivar la comprobación IAM de invocación de los servicios Cloud Run correspondientes: el token del usuario lo verifica Firebase Auth dentro de cada función. Así se evita rechazar un JWT Firebase por no ser una identidad IAM de Google Cloud. Sin sesión, sin App Check o con una cuenta suspendida, las funciones rechazan el acceso.

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

El archivo local `functions/.env.share-you-6a706` contiene `AI_ENABLED=true`, `GEMINI_MODEL=gemini-3.1-flash-lite`, `AI_ENFORCE_APP_CHECK=true` y `AI_DAILY_LIMIT=20`. Está excluido de Git y no contiene la clave. La plantilla `.env.example` conserva la IA desactivada hasta configurar el entorno. Reiniciar/recompilar no sustituye un nuevo despliegue:

```powershell
.\scripts\deploy-functions.ps1 -Stage Analysis
```

Al activar `AI_ENABLED`, las funciones de análisis vinculan el secreto; Firebase CLI requiere que exista y configura el acceso de la cuenta de ejecución.

### App Check durante desarrollo

`ShareYouApplication` instala App Check antes de usar los repositorios Firebase. Gradle incluye el proveedor debug solo en debug y Play Integrity solo en release, coordinados por el mismo BoM Firebase. Se registró la huella SHA-256 del certificado de depuración local y se verificó un token válido desde un emulador Android.

Cada nuevo dispositivo o instalación de desarrollo debe registrar su secreto de App Check. Tras instalar y abrir la nueva APK debug, ejecutar desde la raíz, reemplazando el identificador por el que muestre `adb devices`:

```powershell
python scripts/register-appcheck-debug-token.py --serial emulator-5556 --name Mi-dispositivo-desarrollo
```

El script requiere Python, ADB y una sesión de Google Cloud CLI con permisos de App Check. Lee el secreto de los logs del proceso de la app y lo registra directamente; no muestra ni guarda su valor. El secreto queda en el almacenamiento privado de esa instalación. Reinstalar borrando datos o cambiar de dispositivo puede generar uno nuevo. También se puede registrar desde Firebase Console → Security → App Check → aplicación Android → Manage debug tokens.

La exigencia de App Check se limita a estas funciones. No se habilitó la exigencia global para Authentication, Firestore ni Storage, para conservar el acceso de las APK anteriores.

### Antes de distribuir release

La variante release compila con Play Integrity. Aún debe registrarse la huella SHA-256 del certificado final de distribución y configurar/verificar Play Integrity para la forma de distribución elegida; si se usa Google Play, vincular el proyecto desde Play Console y utilizar el certificado de firma de Google Play. La prueba realizada corresponde al proveedor debug, no certifica una distribución release real. [Guía oficial](https://firebase.google.com/docs/app-check/android/play-integrity-provider).

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
- `AppCheckConfigurationTest.kt`: prueba Android real optativa (`appCheckSmoke=true`) que comprueba la obtención de un token desde un dispositivo debug registrado, sin crear publicaciones ni cuentas.

Se probaron directamente contra Gemini un texto permitido, un rechazo con alternativa, un duplicado redactado de otra manera, una edición en otra fecha y una imagen sintética relacionada/no relacionada con la categoría. Estos ejemplos no sustituyen una evaluación amplia de calidad. Los emuladores no certifican permisos IAM ni facturación de producción. El recorrido completo de publicar desde los formularios sigue pendiente.

Verificación de esta preparación: compilación de APK y APK de pruebas correcta; 58 pruebas JVM sin fallos (cuatro nuevas), 12 pruebas unitarias de Functions y siete de integración local aprobadas. También se comprobó `aiStatus` en producción con sesión válida, sin sesión y con perfil suspendido; la cuenta y perfil temporales usados para esa comprobación se eliminaron. Registros locales: `build/cloud-functions-android-build.log`, `build/cloud-functions-unit-tests.log`, `build/cloud-functions-emulator-tests.log`, `build/cloud-functions-production-smoke.json` y `build/cloud-functions-artifact-policy.log`.

Verificación al activar el proyecto gratuito: debug/APK de pruebas compilados y Kotlin de release compilado; las 58 pruebas JVM y las 19 pruebas locales del backend siguieron pasando. `AppCheckConfigurationTest` pasó en el emulador con App Check real. Se probaron nueve casos contra las funciones desplegadas: rechazo sin App Check, sin Auth, de participante al analizar eventos y de perfil suspendido; consulta protegida de estado; texto permitido; texto rechazado con alternativa; imagen privada relacionada y no relacionada. La cuenta, el perfil, la cuota y la imagen temporales de esta prueba se eliminaron. No se crearon publicaciones ni comentarios de prueba en producción.

Registros de esta activación: `build/gemini-key-validation.json`, `build/gemini-provider-smoke.json`, `build/appcheck-android-build.log`, `build/appcheck-sdk-runtime-tests.log`, `build/gemini-free-emulator-tests.log`, `build/gemini-free-analysis-deploy.log` y `build/gemini-callable-smoke.json`. Los informes no contienen claves ni tokens.

Referencias: [Functions](https://firebase.google.com/docs/functions/get-started), [funciones invocables](https://firebase.google.com/docs/functions/callable), [secretos](https://firebase.google.com/docs/functions/config-env#secret_parameters), [Gemini](https://ai.google.dev/gemini-api/docs/image-understanding).
