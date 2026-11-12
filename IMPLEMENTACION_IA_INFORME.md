# Share You: implementación e integración de inteligencia artificial

Documento técnico para elaborar el informe académico. Fecha de actualización: **12 de noviembre de 2026**.

**Estado actual:** los formularios de crear, editar y volver a lanzar eventos y publicar comentarios/aportes ya llaman a los servicios de IA. Las Functions y las reglas correspondientes están desplegadas. La revisión administrativa posterior se conserva. App Check está desactivado por decisión del equipo.

La primera etapa había dejado preparados el backend y los contratos Android. La actualización descrita en este documento completa su conexión con los formularios y protege el guardado mediante comprobantes emitidos por el servidor. Esta versión reemplaza la descripción anterior que indicaba que los formularios seguían pendientes.

Las rutas son relativas a la raíz del repositorio. Para las rutas abreviadas Kotlin, el directorio base es `app/src/main/java/com/eventoscomunitarios/app/`. No se incluyen contraseñas, claves Gemini ni tokens reales.

## 1. Requisitos implementados

| Requisito escogido | Implementación actual | Respuesta visible para el usuario |
| --- | --- | --- |
| 2. Validación de imágenes relevantes | La imagen adjunta se analiza junto con título, descripción y categoría antes del guardado. | Si no corresponde al evento, se muestra el motivo y se solicita cambiarla. No se publica el contenido rechazado. |
| 3. Detección de contenido inapropiado | Se analiza el texto de eventos, reseñas y aportes. Un rechazo incluye motivos y una alternativa. | Se muestra la propuesta y se puede aplicarla al formulario. El contenido corregido vuelve a analizarse al enviar. |
| 5. Detección de publicaciones duplicadas | El servidor busca eventos cercanos y Gemini compara semánticamente el borrador con ellos. | Se muestra la lista de posibles duplicados. Se puede abrir un evento existente y aportar información, o confirmar expresamente que se trata de otro evento. |

La IA no sustituye la verificación manual del organizador ni la aprobación administrativa de publicaciones. Un evento que supera el análisis se guarda como `PENDING`; el administrador conserva su proceso de revisión.

## 2. Comparación entre el antes y el ahora

### 2.1 Referencias de Git

| Referencia | Contenido |
| --- | --- |
| `e068564`, 10/11/2026 | Referencia anterior al backend de IA. Ya incluía el arreglo del botón de regreso del registro. |
| `db056e5`, 12/11/2026 | Incorporación inicial de Functions, Gemini, contratos Android y pruebas de preparación; también agrupó correcciones de acceso. |
| `4e01a9e`, 12/11/2026 | Retiro de App Check para facilitar pruebas del equipo y la sustentación. |
| Cambios actuales del directorio de trabajo | Conexión de formularios, interfaz de resultados, aportes, comprobantes de un solo uso y reglas de escritura. Deben agregarse a Git para compartir esta integración. |

Comandos de consulta:

```powershell
git log --oneline
git show --stat db056e5
git show --stat 4e01a9e
git diff --name-status e068564
git diff
git status --short
```

`git diff` muestra cambios de archivos ya versionados; `git status` también muestra los archivos nuevos. Esta documentación no crea un commit ni hace un push automáticamente.

### 2.2 Comparación funcional

| Aspecto | Antes de incorporar IA | Primera preparación | Estado actual integrado |
| --- | --- | --- | --- |
| Crear/editar eventos | Validación de campos y reglas del negocio. | Existían servicios remotos independientes del formulario. | El envío espera el análisis de contenido e imagen. |
| Imágenes | Comprobación técnica de formato y tamaño. | El backend podía comprobar relevancia con imágenes de prueba. | Android prepara la imagen privada; el servidor analiza y conserva una copia de los bytes permitidos. |
| Lenguaje ofensivo | Sin análisis con LLM. | `analyzeText` devolvía evaluación y alternativa. | Eventos, reseñas y aportes muestran los rechazos y propuestas. |
| Duplicados | Sin comparación semántica. | Se devolvían coincidencias desde el backend. | Se muestran coincidencias y se permite abrir el evento para aportar información. |
| Guardado | Los repositorios escribían con las validaciones anteriores. | La respuesta del modelo no autorizaba el guardado. | Las reglas exigen un comprobante vigente para el contenido exacto y su consumo en la misma transacción. |
| Comentarios | Reseñas después de participar en un evento finalizado. | Mismo comportamiento. | Se conservan las reseñas y se añaden aportes de información para eventos aprobados que aún no terminan. Ambos se moderan. |
| Interfaz | Formularios anteriores. | No había interfaz de IA. | Estados de análisis, motivos, alternativa, imagen rechazada y diálogo de posibles duplicados. |
| Navegación | Rutas globales y del dashboard existentes. | Sin cambios de IA. | Se agregaron callbacks para abrir el detalle del duplicado desde crear/editar; se conservan las rutas existentes. |
| App Check | Sin este requisito en los flujos existentes. | Se incorporó temporalmente. | Retirado del cliente y no exigido en servicios. Se conservan sesión, roles, reglas y cuotas. |

## 3. Arquitectura y recorrido completo

Se conservan Kotlin, Jetpack Compose, Material Design 3, MVVM, `UiState` inmutable, `StateFlow`, casos de uso, interfaces de repositorio e inyección con Hilt. Se mantienen `core`, `domain`, `features` y `navigation`. No se agregaron nuevas dependencias para completar la conexión de formularios.

### 3.1 Eventos

```text
Crear / Editar / Volver a lanzar
             ↓
ViewModel valida campos y bloquea envíos simultáneos
             ↓
ContentAnalysisUseCases.reviewEvent(...)
             ↓
MediaRepository → borrador de imagen privado en Storage, si hay foto
             ↓
FirebaseContentAnalysisRepository → SDK Firebase Functions
             ↓
analyzeEventDraft: sesión, perfil, permisos, entrada y cuota
             ↓
Firestore: candidatos cercanos / Storage: bytes de imagen
             ↓
Gemini: texto + imagen + comparación de candidatos
             ↓
Servidor valida la respuesta JSON
             ↓
Rechazo → motivos/alternativa/imagen rechazada en la pantalla
Permitido → comprobante temporal y copia de imagen validada
             ↓
Si hay duplicados → el usuario revisa, abre un evento o confirma que es distinto
             ↓
EventUseCases.create / update / relaunch
             ↓
FirebaseEventRepository: evento y consumo del comprobante en una transacción
             ↓
Reglas Firestore comprueban contenido, usuario, destino, vigencia y primer uso
             ↓
Evento PENDING → revisión administrativa existente
```

La función emite el comprobante; el SDK Android sigue escribiendo el documento final. El control de escritura se ejecuta en el servidor mediante las reglas. No se trasladó toda la lógica transaccional de cupos e inscripciones a una nueva API.

### 3.2 Reseñas y aportes

```text
Detalle del evento → texto del usuario
             ↓
EventDetailViewModel → ContentAnalysisUseCases.analyzeText(...)
             ↓
analyzeText → Gemini → rechazo con alternativa o comprobante de texto
             ↓
CommunityUseCases.addComment(..., reviewId, isContribution)
             ↓
FirebaseCommunityRepository: comentario y consumo atómico del comprobante
             ↓
Reglas: texto exacto y permiso para reseñar o aportar en ese evento
```

La alternativa nunca se publica automáticamente. Aplicarla modifica el formulario; el usuario vuelve a enviar y el texto definitivo se analiza de nuevo.

## 4. Cambios Android: archivos y responsabilidades

### 4.1 Dominio y casos de uso

| Archivo, relativo al paquete base Kotlin | Cambio y responsabilidad |
| --- | --- |
| `domain/model/ContentAnalysis.kt` | Nuevo contrato de solicitudes y resultados: `AiPreparationStatus`, `TextAssessment`, `EventAnalysisRequest`, `ImageAssessment`, `PossibleDuplicate`, `DuplicateCandidate` y `EventAssessment`. Se incorporan `reviewId` y `approvedPhotoUri` para enlazar análisis y guardado. |
| `domain/repository/ContentAnalysisRepository.kt` | Interfaz con `status()`, `analyzeText(...)` y `analyzeEventDraft(...)`. No contiene Composables ni credenciales. |
| `domain/usecase/ContentAnalysisUseCases.kt` | Valida idioma, texto, título, descripción, coordenadas y fechas. `reviewEvent(...)` prepara la foto, llama al análisis y elimina el borrador temporal en `finally`. `discard(...)` solicita retirar una copia aprobada que se invalida al editar. |
| `domain/repository/MediaRepository.kt` | Incorpora `uploadAnalysisImage(...)` para preparar una imagen del evento sin mezclarla con documentos de identidad. |
| `domain/model/Event.kt` | Añade `aiReviewId` y `aiDuplicateConfirmed`, con valores por defecto para mantener lectura de eventos anteriores. |
| `domain/model/Community.kt` | `EventComment` añade `isContribution` y `aiReviewId`. Las reseñas anteriores se interpretan con `isContribution=false`. |
| `domain/usecase/EventUseCases.kt` | Conserva validaciones, revisión administrativa, notificaciones y transacciones. La edición propaga el comprobante y la confirmación de duplicados a los datos guardados. |
| `domain/usecase/CommunityUseCases.kt` | Acepta el comprobante de texto y distingue aportes de reseñas. `canContribute(...)` permite información útil en un evento aprobado que todavía no termina. |

`TextAssessment` conserva `allowed`, `reasons` y `suggestedText`; un rechazo no emite permiso de escritura. `EventAssessment` contiene además relevancia de imagen, coincidencias, metadatos de candidatos y señal de búsqueda truncada.

### 4.2 Repositorios e inyección

| Archivo | Cambio y responsabilidad |
| --- | --- |
| `core/data/FirebaseContentAnalysisRepository.kt` | Implementa las tres llamadas invocables y convierte mapas JSON a modelos Kotlin. Lee el comprobante y la URL aprobada. Usa un timeout de 70 segundos. |
| `core/data/InMemoryContentAnalysisRepository.kt` | Permite respuestas explícitas para pruebas. Su valor por defecto falla si no se configuró proveedor; no aprueba contenido ficticiamente en producción. |
| `core/data/FirebaseMediaRepository.kt` | Sube borradores JPEG/PNG/WebP privados; admite preparar una foto existente de Firebase para edición o relanzamiento; conserva lectura privada de cédulas y permite retirar borradores/copias propias. |
| `core/data/FirebaseEventRepository.kt` | Guarda y recupera los campos de IA. Escribe evento y `aiReviewUses` en una sola transacción. Las operaciones normales sobre documentos antiguos no agregan campos de IA nulos que alteren las reglas de inscripciones/moderación. |
| `core/data/FirebaseCommunityRepository.kt` | Guarda el indicador de aporte, conserva permisos de reseña y consume el comprobante de texto junto al comentario. |
| `core/di/FirebaseModule.kt` | Proporciona `FirebaseFunctions.getInstance("us-central1")` como dependencia compartida. |
| `core/di/RepositoryModule.kt` | Enlaza la interfaz de análisis con la implementación Firebase usando Hilt. |
| `app/build.gradle.kts` y `gradle/libs.versions.toml` | Incorporaron el SDK Android `firebase-functions` utilizando el Firebase BoM existente. |

En pruebas instrumentadas, `TestRepositoryModule.kt` sustituye los repositorios y `TestDependencies.kt` configura evaluaciones positivas explícitas. Esas fixtures pertenecen a `androidTest`; la APK normal utiliza Firebase.

## 5. Cambios en la interfaz gráfica

No se creó una pantalla global de chat ni un asistente adicional. La IA se integra en las pantallas donde se envía contenido.

### 5.1 Crear y volver a lanzar un evento

Archivos:

- `features/create_event/CreateEventScreen.kt`.
- `features/create_event/CreateEventViewModel.kt`.

Al pulsar Publicar se validan campos y se muestra “Analizando contenido e imagen…”. Mientras se analiza/guarda se bloquea otro envío y se evita cerrar el formulario mediante el botón de regreso. Los callbacks de campos no cambian el borrador enviado durante esa operación.

Si se rechaza texto, se muestran motivos y propuesta. El botón “Usar propuesta” la copia a la descripción; el título sigue siendo editable y debe corregirse si el motivo también lo afecta. No se publica la propuesta sin revisión del usuario.

Si se rechaza la imagen, se muestra la explicación y se puede seleccionar otra foto. Si falla la conexión o el proveedor, se conserva el contenido del formulario abierto y no se convierte el fallo en aprobación.

El relanzamiento conserva su obligación de seleccionar nuevas fechas y pasa por el mismo análisis que una creación.

### 5.2 Editar evento

Archivos:

- `features/edit_event/EditEventScreen.kt`.
- `features/edit_event/EditEventViewModel.kt`.

Guardar cambios utiliza el mismo flujo, con `sourceEventId` igual al evento editado. La foto existente también se comprueba contra el nuevo texto/categoría. La propia publicación se excluye de candidatos a duplicado. Un cambio admitido vuelve a quedar pendiente de revisión administrativa.

Cambiar un campo invalida la evaluación anterior. No se utiliza el resultado de una versión del formulario para guardar otra distinta.

### 5.3 Posibles duplicados y navegación

Archivo compartido: `core/components/ContentAnalysisFeedback.kt`.

`DuplicateEventsDialog` muestra el título y motivo de cada coincidencia. “Ver evento y aportar información” abre el detalle del evento existente. “Es un evento diferente, continuar” es una decisión explícita antes del guardado; no se crean duplicados automáticamente al recibir la respuesta.

`features/dashboard/navigation/DashboardNavigation.kt` conecta `onOpenSimilarEvent` desde crear/editar con `DashboardRoutes.EventDetail`. No se mueve la navegación a `MainActivity` ni se agregan rutas de IA innecesarias. El formulario queda en la pila para permitir regresar.

Si se cierra el diálogo y se vuelve a pulsar Publicar sin cambios, se muestra nuevamente la revisión pendiente, sin consumir otro análisis innecesario. Si el comprobante caduca antes de guardar, las reglas lo rechazan y se requiere analizar otra vez.

### 5.4 Comentarios y aportes

Archivos:

- `features/event_detail/EventDetailScreen.kt`.
- `features/event_detail/EventDetailViewModel.kt`.

Antes de terminar el evento, los usuarios activos pueden publicar un **aporte de información** en un evento aprobado. Se muestra una explicación de esta finalidad. Después de terminar, las reseñas conservan el requisito de inscripción confirmada.

Al enviar se muestra “Analizando texto…”. Si hay rechazo, se presentan motivos y alternativa. Los aportes guardados se distinguen con la etiqueta “Aporte de información”. No se concede permiso de crear eventos a los participantes por permitirles aportar.

### 5.5 Internacionalización

Los nuevos textos de botones, ayuda y estados están en:

- `app/src/main/res/values/ai_strings.xml`, español.
- `app/src/main/res/values-en/ai_strings.xml`, inglés.

Las pantallas envían `es` o `en` al modelo según la configuración actual de idioma, para que sus motivos y propuestas se produzcan en ese idioma. Se conserva el mecanismo de idioma del perfil. Los mensajes técnicos heredados de excepciones siguen utilizando el mecanismo existente de la aplicación.

## 6. Ubicación del backend

```text
functions/
├── src/
│   ├── index.ts                    Functions, permisos, cuota, imágenes y comprobantes
│   ├── analysis.ts                 validación de entradas/salidas y política
│   └── gemini.ts                   adaptador Google Gen AI
├── test/
│   ├── analysis.test.cjs           pruebas unitarias
│   ├── emulator.integration.cjs    acceso, perfiles y servicio sin configurar
│   ├── publishing.integration.cjs  reglas de publicación, comprobantes y aportes
│   └── media.integration.cjs       borradores privados y copias validadas
├── package.json
├── package-lock.json
├── tsconfig.json
├── .env.example
├── .secret.local.example
├── .nvmrc
├── .node-version
└── README.md
```

`firebase.json` asocia ese directorio con el codebase `share-you-ai`, runtime `nodejs22`, reglas y puertos de emuladores. `.firebaserc` identifica el proyecto predeterminado. `functions/lib/` es JavaScript generado por TypeScript; no es el código que debe editarse manualmente ni una carpeta que sea necesario subir a Git.

El backend se ejecuta en Google Cloud, no en el computador de quien mantiene abierto Android Studio. Functions de segunda generación utiliza infraestructura de Cloud Run, Cloud Build y Artifact Registry. [Guía oficial de Functions](https://firebase.google.com/docs/functions/get-started).

## 7. Servicios externos, herramientas y versiones

### 7.1 Proyectos separados

| Servicio/proyecto | Uso |
| --- | --- |
| Firebase `share-you-6a706`, número `381076241260` | Authentication, Firestore, Storage, Functions y Secret Manager del backend. Mantiene Blaze. |
| Storage `share-you-6a706.firebasestorage.app` | Fotos, documentos privados de identidad, borradores y copias de imágenes validadas. |
| Proyecto Gemini `gen-lang-client-0426384814`, Share You IA | Credencial del modelo en proyecto independiente sin facturación vinculada en la configuración verificada. |
| Mapbox | Selección/presentación de ubicación de eventos y mapas. Sus coordenadas permiten limitar la búsqueda de duplicados. No realiza la comparación semántica. |

La primera credencial Gemini respondió HTTP 402 por prepago agotado. Se resolvió usando el proyecto gratuito independiente y actualizando el secreto; no fue necesario recargar esa credencial. El nivel gratuito tiene cuotas y condiciones del proveedor. Firebase conserva sus costos propios; los límites de concurrencia/cuota no son un presupuesto monetario global.

### 7.2 Componentes de IA y herramientas

| Componente | Versión/configuración revisada | Finalidad |
| --- | --- | --- |
| Android Firebase BoM | `34.11.0` | Alinea versiones de SDK Firebase, incluido Functions. |
| Google Gen AI, `@google/genai` | `3.2.0` | Cliente Gemini en el servidor. |
| `firebase-admin` | `14.5.1` | Acceso servidor a Firestore y Storage. |
| `firebase-functions` de Node | `7.4.0` | Funciones invocables de segunda generación. |
| TypeScript | `5.9.3` | Compilación y tipos del backend. |
| Node.js de producción | `22` | Runtime de Functions; registrado en `.nvmrc`, `.node-version` y `package.json`. |
| Firebase CLI | `15.36.0` en el entorno revisado | Despliegue, emuladores y secretos. |
| Google Cloud CLI | Instalado en el equipo administrador | IAM, Cloud Run y consultas de infraestructura. |
| Gradle / JDK del proyecto | Gradle `9.6`, Microsoft OpenJDK `25` | Compilación Android y herramientas Java. El proyecto ya define su toolchain. |
| Pruebas | JUnit/AndroidX y `node:test` | Verificación de dominio, ViewModels, SDK, contratos y reglas. |

No se agregó una clave Gemini al cliente, un modelo local, una base de vectores ni una dependencia nueva para resolver la conexión final de formularios. Hilt, Compose y Navigation ya formaban parte del proyecto.

## 8. Functions y contratos de comunicación

Las tres operaciones son funciones invocables `onCall`, con `enforceAppCheck: false` explícito.

| Función | Permiso | Operación |
| --- | --- | --- |
| `aiStatus` | Sesión Firebase y perfil activo. | Consulta la configuración; no llama al modelo. |
| `analyzeText` | Sesión y perfil activo. | Analiza texto y emite comprobante si se permite. |
| `analyzeEventDraft` | Administrador activo u organizador activo verificado. | Analiza evento, imagen y candidatos; emite comprobante y copia de imagen si se permite. |

Los endpoints tienen el formato `https://us-central1-share-you-6a706.cloudfunctions.net/nombreDeLaFuncion`. El cliente normal utiliza `getHttpsCallable(...)`; abrir esa URL en un navegador no equivale a una llamada autenticada correcta.

El SDK maneja el protocolo y adjunta la identidad Firebase disponible. El backend comprueba después el perfil y las reglas de negocio. [Funciones invocables](https://firebase.google.com/docs/functions/callable).

### 8.1 Estado actual

```json
{
  "provider": "gemini",
  "enabled": true,
  "modelConfigured": true,
  "appCheckEnforced": false,
  "preparationOnly": false,
  "region": "us-central1",
  "contractVersion": 1
}
```

`enabled` y `modelConfigured` describen configuración. No garantizan disponibilidad o cuota ilimitada del proveedor.

### 8.2 Texto

Entrada representativa:

```json
{ "text": "Invitamos a la comunidad a participar.", "language": "es" }
```

Salida permitida:

```json
{ "allowed": true, "reasons": [], "suggestedText": null, "reviewId": "identificador-emitido-por-el-servidor" }
```

Salida rechazada:

```json
{
  "allowed": false,
  "reasons": ["El texto contiene una agresión dirigida."],
  "suggestedText": "Conversemos y participemos con respeto.",
  "reviewId": null
}
```

Son ejemplos del formato, no claves o comprobantes utilizables. La entrada admite hasta 5.000 caracteres; el formulario de comentarios conserva su límite de 1.000. La alternativa está limitada a 1.000 caracteres.

### 8.3 Evento

La solicitud lleva `title`, `description`, `category`, `latitude`, `longitude`, `startsAt`, `endsAt`, `imagePath`, `sourceEventId` y `language`.

Las fechas son milisegundos enteros; el inicio debe ser futuro y el fin posterior al inicio. La categoría es uno de los siete valores del dominio. `imagePath` puede ser nulo o una ruta privada propia. `sourceEventId` identifica la edición/origen cuando corresponde; se comprueba pertenencia o permiso administrativo.

La respuesta contiene:

- `text`: evaluación de título/descripción.
- `image`: `relevant` y motivo; sin imagen, `relevant=null`.
- `possibleDuplicates`: IDs y motivos de las coincidencias semánticas.
- `candidates`: título, ID, fecha y distancia de los candidatos.
- `candidatesTruncated`: informa que no se examinó exhaustivamente toda la zona.
- `reviewId`: comprobante si texto e imagen se permiten; nulo si hay rechazo.
- `approvedPhotoUri`: copia de los bytes analizados, cuando había imagen permitida; nulo si no había foto.

Para una prueba HTTP manual del protocolo invocable, el cuerpo debe envolver la entrada en `{"data": ...}` y utilizar un ID token Firebase en `Authorization: Bearer ...`. No se solicita token App Check en la configuración actual. No deben pegarse tokens o claves en el informe ni en el chat.

## 9. Modelo, política y validación de respuestas

`functions/src/gemini.ts` utiliza `models.generateContent(...)` con `gemini-3.1-flash-lite`, contenido textual y datos de imagen cuando corresponde.

Configuración: respuesta `application/json`, JSON Schema, temperatura 0, máximo 4.096 tokens de salida y timeout de 45 segundos. El adaptador valida nuevamente el resultado mediante los parsers de `analysis.ts`. [Salidas estructuradas](https://ai.google.dev/gemini-api/docs/structured-output).

La política considera insultos dirigidos, acoso, amenazas, discriminación y contenido sexual explícito. Exige evaluar contexto para distinguir una agresión de una mención educativa. Pide una versión respetuosa que preserve la intención legítima, o una invitación a dialogar si la intención original era dañina. El contenido del usuario se trata como datos, no como instrucciones para cambiar la política.

Se comprueban tipos, longitudes, consistencia de presencia de imagen e identificadores de duplicados. El modelo no puede devolver una coincidencia inventada que no pertenezca a los candidatos. Una respuesta vacía, inválida o un fallo del proveedor no se convierten en aprobación.

No se entrenó un modelo propio. No se generan embeddings ni se indexa una base vectorial. Temperatura cero y JSON estructurado no equivalen a precisión perfecta; se necesitan ejemplos de evaluación para medir falsos positivos y negativos.

## 10. Imágenes: subida, análisis y copia validada

### 10.1 Borrador privado

Android sube a `event-drafts/{uid}/{uuid}` mediante `uploadAnalysisImage(...)`. Se admiten JPEG, PNG y WebP, tamaño positivo y hasta 10 MiB. El nombre y UID se verifican en servidor; no se admiten URL arbitrarias, documentos de identidad o archivos de otro usuario.

Si el formulario conserva una foto remota, Android la lee desde el Storage de la aplicación y prepara un nuevo borrador privado. Esto permite comprobar otra vez su relación con el texto/categoría al editar o volver a lanzar.

Las reglas de Storage limitan lectura/eliminación del borrador a su dueño y no permiten sobrescribirlo. La subida contiene metadato `ownerId` coherente con el UID.

### 10.2 Bytes analizados y destino final

`imageFor(...)` verifica metadatos y descarga la generación de objeto comprobada con validación CRC32C. Gemini analiza esos bytes. Si la evaluación permite la publicación, Functions crea `validated-events/{uid}/{reviewId}` con ese contenido y devuelve su URL para la foto del evento.

Los clientes pueden leer la foto final autenticados y retirarla conforme a permisos de propietario/moderador, pero **no pueden crear ni reemplazar objetos en `validated-events`**. Esto evita analizar una imagen y publicar luego otra bajo la misma referencia.

El borrador temporal se retira al terminar la llamada, incluso si se produce un error. Cambiar el formulario invalida el resultado y solicita retirar la copia aprobada pendiente. La limpieza automática de copias abandonadas por cierre de la app o fallos de red es una mejora de mantenimiento posterior.

La cédula conserva su almacenamiento privado en `identities` y su revisión manual por administrador. No se envía a Gemini. [Comprensión de imágenes](https://ai.google.dev/gemini-api/docs/image-understanding).

## 11. Duplicados: filtro geográfico y comparación semántica

El backend utiliza las coordenadas del evento para consultar por latitud y calcular distancia Haversine. El radio inicial es **1 km**.

La consulta obtiene hasta 251 documentos para detectar truncamiento y examina como máximo 250. Descarta eventos eliminados, no aprobados, sin ubicación confirmada, terminados o con datos inválidos; también descarta el propio evento de origen. Conserva los que están dentro del radio, ordena por cercanía y envía como máximo 20 candidatos al modelo, con descripción de hasta 1.500 caracteres.

Gemini compara el significado del título/descripción, categoría y fechas. La fecha permite distinguir otra edición de una actividad recurrente. Los resultados incluyen razones, no solo una coincidencia literal de palabras.

Cuando hay posibles duplicados, el usuario los revisa antes de guardar. Puede aportar al evento existente. Si declara que es un evento distinto, el documento guarda `aiDuplicateConfirmed=true`; las reglas exigen esa confirmación cuando el comprobante contiene coincidencias.

La búsqueda tiene límites explícitos y no certifica que no exista ningún duplicado en toda la base. `candidatesTruncated` conserva esa información en el contrato y el diálogo la muestra cuando presenta coincidencias. No se implementó un índice geoespacial exhaustivo ni una base de embeddings.

## 12. Protección del guardado: comprobantes y reglas

### 12.1 Datos nuevos

| Ruta/campo | Finalidad y control |
| --- | --- |
| `aiUsage/{uid}` | Contador por usuario y día UTC. Solo lo utiliza el servidor. |
| `aiReviews/{reviewId}` | Resultado permitido y contenido exacto, UID, clase, evento de origen, coincidencias y vencimiento. Lo crea Functions; los clientes no pueden fabricarlo ni modificarlo. |
| `aiReviewUses/{reviewId}` | Marca el primer uso y su destino. Se crea junto al evento/comentario en la misma transacción; no se permite actualizarla ni borrarla desde el cliente. |
| Evento: `aiReviewId` | Identifica el análisis usado para esa versión de contenido. |
| Evento: `aiDuplicateConfirmed` | Guarda la decisión explícita frente a coincidencias. |
| Comentario: `aiReviewId` | Vincula el texto publicado con el análisis permitido. |
| Comentario: `isContribution` | Separa aportes informativos de reseñas por asistencia. |

### 12.2 Verificación en el servidor

El comprobante vence a los **15 minutos**. Para eventos, las reglas comparan UID, título, descripción, categoría, coordenadas, inicio, fin, foto y destino/origen. Para comentarios, comparan UID y texto exacto, además de las reglas de acceso al evento.

`consumesReview(...)` exige que la marca no exista antes de escribir y que exista después de la transacción con el destino correcto. `getAfter(...)` permite validar este estado final de una escritura atómica. [Condiciones y acceso a documentos en reglas Firestore](https://firebase.google.com/docs/firestore/security/rules-conditions#access_other_documents).

Por eso no basta con cambiar un indicador local a “permitido”. Se rechazan comprobantes inexistentes, vencidos, de otra cuenta, ya usados o aplicados a contenido diferente. El guardado y consumo no se confirman por separado.

Los eventos anteriores siguen siendo legibles; no necesitan reanalizarse para consultar, inscribirse, votar o moderar según sus permisos anteriores. Crear una publicación nueva o editar su contenido sí utiliza el flujo actualizado.

### 12.3 Cuotas y permisos de ejecución

Cada UID dispone de 20 intentos de análisis por día UTC de forma predeterminada. `reserveQuota(...)` lo comprueba en una transacción y permite configurar entre 1 y 100. Una llamada al proveedor que falla también consume su intento. `aiStatus` no consume inferencia.

Cuenta dedicada: `share-you-functions@share-you-6a706.iam.gserviceaccount.com`.

- `roles/datastore.user` en el proyecto: perfiles, candidatos, cuota y comprobantes.
- `roles/storage.objectUser` en el bucket: lectura de borradores y creación/eliminación de copias validadas. La preparación inicial solo requería lectura con `objectViewer`; la copia final necesita escritura de objetos.
- `roles/secretmanager.secretAccessor` para `GEMINI_API_KEY`: acceso al secreto vinculado a las funciones consumidoras.

El Admin SDK no utiliza reglas del cliente. Por eso las Functions comprueban sesión, perfil, roles y pertenencia del origen por su cuenta. No se generó un archivo JSON de cuenta de servicio para Android.

## 13. App Check y correcciones de acceso incluidas

### 13.1 App Check desactivado

Se retiraron las dependencias/proveedores debug y Play Integrity, los archivos `AppCheckSetup.kt`, la prueba específica y el script de registro de dispositivos. `ShareYouApplication.kt` conserva su punto de entrada Hilt sin inicialización App Check.

Las tres funciones declaran `enforceAppCheck: false`; no hay una variable local que lo reactive. Authentication, Firestore y Storage se verificaron como `UNENFORCED`. Los compañeros y el docente no necesitan registrar tokens de depuración.

Se mantienen autenticación Firebase, perfil activo, roles, verificación de organizadores, reglas, cuotas y secretos en servidor. La cuenta de visualizador de Firebase Console no determina por sí misma el rol de un usuario dentro de la app.

### 13.2 Mensajes de cuenta suspendida o bloqueada

La versión de incorporación de IA también agrupó cambios independientes en acceso:

| Archivo | Resultado |
| --- | --- |
| `domain/model/User.kt` | Comprobación compartida de cuenta activa. |
| `core/data/FirebaseUserRepository.kt` | No publica una sesión válida a la UI antes de comprobar el perfil; cierra sesión si la cuenta está restringida. |
| `core/data/InMemoryUserRepository.kt` | Aplica la misma regla en las pruebas. |
| `domain/usecase/UserUseCases.kt` | Mantiene una comprobación adicional al iniciar sesión. |
| `features/login/LoginViewModel.kt` | Conserva el error visible hasta corregir datos o reintentar. |
| `features/login/LoginScreen.kt` | Muestra el error persistente de acceso. |
| `core/util/LocalizedMessage.kt` y `res/values*/login_strings.xml` | Mensajes localizados para cuenta suspendida/bloqueada. |

El dominio usa `ACTIVE` y `SUSPENDED`. El bloqueo de un organizador rechazado se representa con cuenta suspendida y verificación `REJECTED`; no se agregó un enum `BLOCKED` distinto.

La verificación manual de organizadores, internacionalización general y filtros de eventos venían de cambios anteriores a la referencia inicial de IA. No deben atribuirse al modelo Gemini.

## 14. Configuración relevante del despliegue

| Parámetro | Valor |
| --- | --- |
| Proyecto del backend | `share-you-6a706` |
| Codebase | `share-you-ai` |
| Región | `us-central1` |
| Generación/runtime | Segunda generación, Node.js 22 |
| Instancias | Mínimo 0, máximo 2 por función |
| Concurrencia | 8 por instancia |
| Memoria | 256 MiB |
| Timeout servidor / Gemini / Android | 60 / 45 / 70 segundos |
| Modelo | `gemini-3.1-flash-lite` |
| Configuración local | `AI_ENABLED=true`, `AI_DAILY_LIMIT=20` |
| Secreto | `GEMINI_API_KEY`, en Secret Manager; se validó la versión 3 |

Se utilizan Cloud Functions, Cloud Build, Artifact Registry, Cloud Run, Eventarc, Pub/Sub, Storage y Secret Manager. La API Generative Language pertenece al proyecto de la credencial Gemini. Firebase CLI comprueba/habilita APIs de infraestructura durante el despliegue.

`functions/.env.share-you-6a706` está excluido de Git y no contiene la clave. `.env.example` conserva IA desactivada para evitar que una copia nueva simule un servicio operativo sin configurarlo.

## 15. Proceso para subir o actualizar Cloud Functions

### 15.1 Diferencias entre acciones

| Acción | Efecto |
| --- | --- |
| Commit y push | Comparte fuentes y configuración versionada. No despliega servicios. |
| Compilar/instalar APK | Actualiza el código Android que ejecuta el dispositivo. |
| Desplegar Functions | Publica código TypeScript compilado y configuración del servidor. |
| Desplegar reglas | Actualiza los permisos y requisitos de escritura de Firestore/Storage. |

### 15.2 Requisitos del responsable del backend

Necesita Node 22, npm, Firebase CLI, Google Cloud CLI, acceso autorizado para desplegar/usar la cuenta de ejecución y Blaze en Firebase. Para los emuladores se necesita JDK completo 21 o superior; el proyecto ya utiliza Microsoft JDK 25.

El permiso de visualizador de un compañero no permite desplegar. No necesita ese permiso adicional para ejecutar la app contra las funciones existentes.

Desde la raíz:

```powershell
node --version
npm install -g firebase-tools
firebase.cmd --version
firebase.cmd login
gcloud.cmd auth login
npm --prefix functions ci
```

`npm ci` utiliza `package-lock.json`. No volver a ejecutar `firebase init` sobre el proyecto: la configuración ya existe.

### 15.3 Entorno y clave

Solo en un equipo administrador nuevo, si aún no existe el archivo propio:

```powershell
Copy-Item functions/.env.example functions/.env.share-you-6a706
```

Valores operativos del archivo local:

```properties
AI_ENABLED=true
GEMINI_MODEL=gemini-3.1-flash-lite
AI_DAILY_LIMIT=20
```

Para registrar o rotar la clave, introducirla en el prompt de la CLI:

```powershell
firebase.cmd functions:secrets:set GEMINI_API_KEY --project share-you-6a706
```

No guardarla en `local.properties`, `.env` ni el APK. Rotar el secreto no sustituye el nuevo despliegue de las funciones consumidoras. [Secretos en Functions](https://firebase.google.com/docs/functions/config-env#secret_parameters).

### 15.4 Comprobar antes de desplegar

```powershell
.\scripts\test-functions.ps1
.\scripts\test-firestore-rules.ps1
```

El primer script ejecuta compilación, 12 pruebas unitarias y 17 de integración local sin consumir Gemini real. El segundo ejecuta 81 comprobaciones de regresión de reglas y permisos sociales. No deben ejecutarse dos suites que ocupen los mismos puertos simultáneamente.

### 15.5 Desplegar la versión integrada

```powershell
.\scripts\deploy-functions.ps1 -Stage Analysis
```

**`Analysis` es ahora la etapa predeterminada**, también al ejecutar el script sin argumentos. Publica el filtro `functions:share-you-ai,firestore:rules,storage` para mantener funciones y reglas del guardado validado en la misma versión.

El proceso:

1. Comprueba el archivo local, la activación de IA y el modelo.
2. Ejecuta Firebase CLI con el proyecto explícito.
3. Firebase ejecuta `npm --prefix "$RESOURCE_DIR" run check` mediante `predeploy`.
4. TypeScript genera `functions/lib/` y pasan las pruebas unitarias.
5. Firebase prepara fuentes y vincula el secreto existente.
6. Se publican reglas y nuevas revisiones de las funciones.
7. El script configura la invocación de Cloud Run para las tres funciones.
8. Se verifican estado, permisos y una operación de contenido.

La etapa explícita `-Stage Preparation` queda disponible para publicar solo `aiStatus`; no actualiza los otros servicios ni sus reglas. No usarla para entregar cambios del flujo completo.

La cuenta de ejecución ya tiene el permiso de objetos necesario. En una infraestructura equivalente nueva, quien administre IAM debe proporcionar lectura/escritura de objetos a esa cuenta; un rol de solo lectura no permite crear la copia validada.

### 15.6 Por qué Cloud Run necesita un ajuste de invocación

El SDK envía un JWT Firebase. La sesión se comprueba dentro de la función; Cloud Run no debe rechazarla antes por esperar una identidad IAM de Google Cloud. Para cada servicio, el script aplica el equivalente a:

```powershell
gcloud.cmd run services update aistatus --no-invoker-iam-check --region us-central1 --project share-you-6a706 --quiet
```

También se aplica a `analyzetext` y `analyzeeventdraft`. Esto no elimina la comprobación `request.auth`, perfil activo y roles, y es independiente de App Check. [Configuración de invocación de Cloud Run](https://docs.cloud.google.com/run/docs/authenticating/public).

### 15.7 Verificación y APK

```powershell
firebase.cmd functions:list --project share-you-6a706
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

Consultar `aiStatus` autenticado y probar rechazo sin sesión. Verificar con una cuenta del rol correcto una operación permitida y una rechazada.

**Instalar la APK nueva es obligatorio para usar este cambio:** la versión anterior no envía los comprobantes que exigen las reglas nuevas. Archivo generado: `app/build/outputs/apk/debug/app-debug.apk`.

### 15.8 Crear otra Function

La función nueva debe exportarse desde `functions/src/index.ts`. Por ejemplo, una consulta de capacidades puede reutilizar `onCall` y `activeUid` existentes:

```typescript
export const getAiCapabilities = onCall(
  { enforceAppCheck: false },
  async request => {
    await activeUid(request);
    return { text: true, image: true, duplicates: true };
  }
);
```

Este es un ejemplo para explicar el procedimiento; no se agregó ni desplegó esa cuarta función.

Definir entradas/salidas y permisos, añadir pruebas, compilar y desplegar. Para una función específica:

```powershell
firebase.cmd deploy --only functions:share-you-ai:getAiCapabilities --project share-you-6a706 --non-interactive
```

Después, consultar/configurar el servicio Cloud Run generado. Si se usa el script general, agregar su nombre real a la lista `$services`. Añadir una función en un archivo que no se exporta desde el punto de entrada no la publica.

### 15.9 Artefactos de compilación

Se conserva una política de siete días para imágenes antiguas de `gcf-artifacts` en Artifact Registry. Se puede configurar con:

```powershell
firebase.cmd functions:artifacts:setpolicy --days 7 --location us-central1 --project share-you-6a706 --non-interactive --force
```

Esta política no elimina fotos de usuarios ni comprobantes Firestore. Es distinta del mantenimiento de borradores/copias de eventos.

## 16. Pruebas realizadas y evidencia

### 16.1 Suites locales

| Suite | Cantidad | Cobertura y resultado |
| --- | --- | --- |
| JVM Android | 58 | Reglas de negocio y preparación de análisis; cero fallos/errores. Las cuatro de `ContentAnalysisPreparationTest` están incluidas, no se suman de nuevo. |
| Functions unitarias | 12 | Entradas, roles, fechas, rutas, tipos, rechazos, duplicados y distancias; aprobadas. |
| Functions/reglas/Storage con emuladores | 17 | Siete de acceso/configuración, ocho de publicación/comprobantes y dos de imágenes; aprobadas. |
| Regresión de reglas Firestore | 81 comprobaciones | Perfiles, verificación, permisos, eventos, inscripciones, reseñas y funciones sociales; aprobadas. |
| `AiPublishingFlowTest`, Android | 7 | Rechazo de texto/imagen, conservación de borrador, sugerencia, duplicados con decisión, invalidación al editar, proveedor/proof ausente, edición y aportes moderados; aprobadas. |
| `FirebaseIntegrationTest`, Android y Firebase local | 6 | SDK y repositorios reales: perfiles, acceso restringido, imágenes, guardado con comprobantes, concurrencia, reseñas y relanzamientos; aprobadas. |
| `EventViewModelTest`, Android | 8 | Regresión del formulario, permisos, edición, inscripciones y envíos simultáneos; aprobadas. |
| Compilación | Debug, APK de pruebas y Kotlin release | Correcta. No equivale a generar/finalizar una publicación firmada de release en Play Store. |

Las pruebas de ViewModels emplean un proveedor de IA determinista para comprobar decisiones de la app sin consumir cuota. Las de SDK usan emuladores con comprobantes administrativos explícitos de prueba. No debe afirmarse que cada una de esas pruebas consultó Gemini real.

`scripts/test-functions.ps1` inicia Functions 5001, Auth 9099, Firestore 8080 y Storage 9199 con `demo-share-you-ai`. Mantiene IA desactivada durante esa suite, comprueba puertos y retira su `.env.local` al terminar. Un emulador no verifica permisos IAM o cuotas reales de producción.

### 16.2 Verificación real del servicio publicado

El 12/11/2026 se verificaron diez grupos de casos usando Gemini, Functions, Storage y Firestore publicados, sin enviar token App Check:

1. Rechazo de acceso sin sesión.
2. Estado operativo del contrato integrado con `preparationOnly=false`.
3. Subida autenticada del borrador privado usando las reglas desplegadas.
4. Imagen relacionada aceptada, copia validada y comprobante emitidos.
5. Rechazo de contenido modificado después del análisis.
6. Guardado del evento y consumo del comprobante en una sola transacción.
7. Rechazo al reutilizar ese comprobante para otra publicación.
8. Aporte permitido en un evento existente con el texto exacto moderado; un texto alterado fue rechazado.
9. Detección semántica de la publicación de prueba como duplicada y exigencia de una decisión explícita.
10. Texto ofensivo rechazado con motivos, alternativa y sin comprobante de escritura.

La prueba consumió cuatro inferencias del proveedor. Se utilizaron cuenta, documentos e imagen sintética temporales. Las 13 operaciones de limpieza terminaron correctamente: se retiraron publicaciones/comentarios de prueba, comprobantes, marcas de consumo, archivos, cuota, perfil y cuenta. No se enviaron notificaciones desde los casos de prueba que escribieron directamente los documentos.

La preparación anterior también había comprobado imagen no relacionada, comparación semántica frente a otra redacción y diferenciación de otra edición por fechas. Son verificaciones previas del proveedor; no se cuentan como casos nuevos de esta integración.

### 16.3 Evidencias y ubicación

| Ruta local | Evidencia |
| --- | --- |
| `app/build/reports/tests/testDebugUnitTest/index.html` | Informe JVM Android. |
| `app/build/test-results/testDebugUnitTest/` | XML de resultados JVM. |
| `app/build/reports/androidTests/connected/debug/index.html` | Última ejecución instrumentada; se reemplaza al ejecutar otra selección. |
| `build/ai-connection-device-tests.log` | Pruebas de los nuevos flujos de IA en Android. |
| `build/ai-connection-sdk-integration.log` | SDK Firebase contra emuladores. |
| `build/ai-connection-event-regression.log` | Regresión de ViewModels de eventos. |
| `build/ai-connection-functions.log` | 12 pruebas unitarias y 17 de integración de backend. |
| `build/ai-connection-firestore-regression.log` | 81 comprobaciones de reglas. |
| `build/ai-connection-deploy.log` | Publicación de funciones/reglas y configuración Cloud Run. |
| `build/ai-connection-production-smoke.json` | Diez grupos reales y confirmación de limpieza, sin credenciales. |
| `build/gemini-provider-smoke.json` | Casos previos del proveedor, si se conserva el directorio generado. |

`build/` y `app/build/` están excluidos de Git. El compañero recibe las suites de prueba al actualizar el código, pero debe regenerar sus reportes o recibir una copia revisada de evidencias sin credenciales. No sumar varias ejecuciones de una misma suite como casos diferentes.

## 17. Cómo reproducir pruebas y preparar al compañero

### 17.1 Comprobaciones sin producción

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
.\scripts\test-functions.ps1
.\scripts\test-firestore-rules.ps1
```

Para las pruebas nuevas de ViewModel, con un emulador Android conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.eventoscomunitarios.app.AiPublishingFlowTest --console=plain
```

Para ejecutar Android con los SDK Firebase locales, se puede utilizar el script existente:

```powershell
.\scripts\start-firebase-emulators.ps1 -RunTests
```

Este último inicia Auth, Firestore y Storage de `demo-share-you` y habilita las pruebas de integración Android. No ejecutarlo simultáneamente con `test-functions.ps1`, que utiliza los mismos puertos. Ejecutar emuladores no redirige automáticamente la APK normal de producción a ellos; las clases de prueba configuran sus clientes locales.

### 17.2 Archivos para ejecutar la versión actual

| Elemento | Por Git | Tratamiento |
| --- | --- | --- |
| Kotlin, XML, Gradle, TypeScript, reglas y scripts | Sí | Compartir todos los cambios de la integración en la misma rama/commit. |
| `package.json`, `package-lock.json`, `firebase.json`, `.firebaserc` | Sí | Configuración reproducible del backend. |
| `.env.example`, `.secret.local.example`, `local.properties.example` | Sí | Plantillas sin credenciales reales. |
| `local.properties` | No | Ruta SDK y configuración Mapbox local del compañero. |
| `app/google-services.json` | No, según el `.gitignore` actual | Debe corresponder al mismo proyecto/aplicación Firebase. |
| `GEMINI_API_KEY` real | No | Solo permanece en Secret Manager. |
| `.env.share-you-6a706` y secretos locales | No | Configuración del responsable de desplegar. |
| `node_modules`, `functions/lib`, salidas `build` | No | Se generan con las herramientas. |

Si ya tiene `local.properties` y el JSON correctos, el compañero debe actualizar el código, sincronizar Gradle e instalar/ejecutar la APK nueva. Para obtener capturas también puede instalar la APK debug actual. No necesita otra credencial Gemini, registrar App Check ni permisos de administrador de Firebase Console.

Para modificar/probar el backend sí necesita Node y herramientas de servidor; para ejecutar Android contra lo desplegado, no. Un permiso de visualizador en la consola y un rol de organizador/administrador dentro de la app son controles diferentes.

## 18. Límites y mejoras posteriores

La conexión funcional de formularios, aportes y guardado validado está implementada. Los siguientes puntos son mejoras o límites de la solución, no formularios desconectados:

- Medir precisión con un conjunto amplio de imágenes/textos y registrar falsos positivos/negativos. Los casos aprobados no certifican exactitud universal.
- Mejorar la búsqueda geográfica si crece el volumen. El radio y límites actuales hacen que el resultado no sea exhaustivo en bases grandes.
- Definir limpieza automática de comprobantes históricos y copias aprobadas abandonadas. El vencimiento ya bloquea su uso, pero no borra automáticamente todos los objetos/documentos.
- Conservar la decisión del equipo sobre App Check. Su retiro simplifica la entrega; no cambia que Authentication y reglas sigan siendo obligatorias.
- No presentar una cuota diaria de inferencias o un presupuesto de consola como un límite garantizado de todos los costos de Firebase.

## 19. Diagnóstico de incidencias

| Situación | Revisión |
| --- | --- |
| No aparecen estados o rechazos de IA en la app | Comprobar que se instaló la APK generada con esta integración. Un despliegue del backend no actualiza el APK. |
| Firestore rechaza crear/editar/comentar | Revisar sesión/rol y que el cliente envíe un comprobante vigente con su consumo atómico. La APK anterior no implementa ese flujo. |
| Se rechazó el guardado tras esperar mucho en duplicados | El comprobante dura 15 minutos. Analizar de nuevo; no ampliar permisos en las reglas. |
| Storage rechaza el borrador | Verificar reglas publicadas, UID/ownerId, formato JPEG/PNG/WebP y tamaño máximo. |
| El análisis de imagen falla al crear la copia | Comprobar acceso de la cuenta Functions para escribir objetos, además de leerlos. |
| HTTP 401 | Falta sesión Firebase válida. |
| HTTP 403 | Cuenta restringida, rol insuficiente, origen ajeno o reglas de contenido/comprobante no satisfechas. |
| HTTP 429 | Cuota diaria de la app o cuota del proveedor. Conservar el formulario y esperar el restablecimiento correspondiente. |
| Proveedor no disponible / timeout | No se publica por defecto. Se informa el fallo y se permite reintentar. |
| HTTP 402 en Gemini | Revisar el proyecto/credencial de Gemini. La solución actual usa el proyecto gratuito independiente; no desvincular Blaze del backend Firebase. |
| Un push no cambia el servidor | Ejecutar el despliegue `Analysis`; Git solo comparte fuentes. |

## 20. Redacción sugerida para el informe

> Se incorporaron servicios de análisis de contenido con Google Gemini en Cloud Functions for Firebase de segunda generación. La implementación conserva la arquitectura Android por funcionalidades y MVVM, con interfaces de repositorio, casos de uso e inyección Hilt. Los formularios de creación, edición y relanzamiento de eventos envían título, descripción, categoría, ubicación y fotografía al servicio antes de guardar. El sistema comunica rechazos y alternativas de redacción, valida la relación semántica de la imagen y presenta posibles duplicados obtenidos mediante un prefiltro geográfico y comparación con el modelo.

> El guardado se protege mediante comprobantes temporales emitidos por el servidor, asociados al usuario y al contenido exacto. Los repositorios Android escriben la publicación o comentario y el consumo de ese comprobante en una misma transacción, y las reglas Firestore rechazan alteraciones, caducidad y reutilización. Las imágenes permitidas se conservan en copias que los clientes no pueden crear ni reemplazar. La solución permite aportar información a un evento existente y conserva la aprobación administrativa y las restricciones de reseñas por asistencia. Su funcionamiento se verificó con pruebas de dominio, ViewModels Android, SDK Firebase en emuladores y solicitudes reales al proveedor y a los servicios desplegados.

No afirmar que se entrenó un modelo propio, que la detección es infalible, que la búsqueda cubre toda la base o que los límites de inferencia eliminan cualquier costo de infraestructura.

## 21. Fuentes y documentación complementaria

- [README del proyecto](README.md).
- [Guía operativa del backend](functions/README.md).
- [Functions: preparación, pruebas y despliegue](https://firebase.google.com/docs/functions/get-started).
- [Funciones invocables desde Android](https://firebase.google.com/docs/functions/callable).
- [Configuración y secretos](https://firebase.google.com/docs/functions/config-env#secret_parameters).
- [Reglas Firestore y escrituras atómicas](https://firebase.google.com/docs/firestore/security/rules-conditions#access_other_documents).
- [Gemini: comprensión de imágenes](https://ai.google.dev/gemini-api/docs/image-understanding).
- [Gemini: salidas estructuradas](https://ai.google.dev/gemini-api/docs/structured-output).
- [Gemini: claves](https://ai.google.dev/gemini-api/docs/api-key).
- [Gemini: precios y nivel gratuito](https://ai.google.dev/gemini-api/docs/pricing).
- [Cloud Run: comprobación de invocación](https://docs.cloud.google.com/run/docs/authenticating/public).

Actualizar este documento cuando cambien el contrato, las reglas, la configuración o las pruebas. El informe debe describir la versión integrada actual y distinguir los servicios reales de las fixtures de prueba.
