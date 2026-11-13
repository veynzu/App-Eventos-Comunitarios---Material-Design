# Share You — aplicación Android con Firebase

Aplicación académica de eventos comunitarios. Conserva Kotlin, Compose, Material 3, MVVM con `UiState`/`StateFlow`, rutas `@Serializable`, casos de uso, interfaces de repositorio e inyección con Hilt.

## Identidad de organizadores, idioma y búsqueda

- El registro de un organizador exige una foto de su cédula y deja `organizerVerificationStatus: "PENDING"`. Puede consultar eventos mientras espera, pero solo un organizador `VERIFIED` o un administrador activo puede crear y editar publicaciones. Los participantes no ven el botón de crear; los casos de uso, repositorios y reglas también protegen la operación.
- En **Admin → Usuarios → Por verificar**, el administrador abre la cédula y elige **Verificar** o **Bloquear**. Bloquear deja la cuenta suspendida y rechaza nuevos accesos. Reactivar una cuenta rechazada la devuelve a revisión pendiente. La revisión comprueba que se está aprobando la misma imagen que se abrió, para evitar aprobar un documento reemplazado durante la revisión.
- La cédula se conserva separada del perfil público: `organizerVerifications/{uid}` guarda una referencia privada `gs://…/identities/{uid}/{archivo}`. Solo el propietario y administradores activos pueden consultar el documento o descargar su foto mediante el SDK autenticado. No se genera una URL pública de descarga para la interfaz. Al eliminar la cuenta se eliminan también estos datos.
- Los organizadores anteriores sin estado de verificación se consideran pendientes y pueden enviar su cédula desde **Perfil**. Las cuentas de demostración verificadas existen únicamente en los repositorios de pruebas.
- **Perfil → Idioma** permite elegir español, inglés o el idioma del dispositivo. La elección se conserva y actualiza las pantallas. Android 13+ usa el idioma por aplicación del sistema; Android 7–12 guarda la preferencia local y recrea la actividad. Los recursos de interfaz tienen versiones `values` y `values-en`; los nombres, descripciones y comentarios escritos por usuarios conservan su contenido.
- Inicio y Mapa comparten filtros combinables de distancia (1–100 km desde el origen elegido), fechas inicial/final, categoría, gratuitos, cupos disponibles y aire libre. Se puede ordenar por fecha o cercanía y restablecer la búsqueda. El último día del rango está incluido; «Con cupos» solo muestra eventos que todavía admiten nuevas inscripciones.

El acceso usa las credenciales existentes de Firebase, sin imponer al iniciar sesión la política de longitud del registro. Una cuenta existente con contraseña corta puede enviarla a Authentication para validación; el registro conserva sus requisitos. No se asignan permisos por la dirección de correo: el rol viene del documento de perfil.

Después de validar las credenciales, el acceso comprueba el estado antes de publicar la sesión. Una cuenta suspendida ve «Tu cuenta está suspendida. Contacta al administrador.»; un organizador suspendido por rechazo de su cédula ve «Tu cuenta está bloqueada. Contacta al administrador.». El aviso permanece visible en el formulario hasta editar las credenciales o reintentar, y tiene traducción al inglés. Las credenciales incorrectas conservan el mensaje genérico.

El 10 de noviembre de 2026 se publicaron las reglas de este flujo en Firestore y Storage del proyecto `share-you-6a706`. Cambiar los archivos locales o recompilar después de esta publicación no despliega nuevas versiones de reglas.

La navegación sigue la [guía 11 del profesor](https://caflorezvi.github.io/guias-apps-moviles/11.navegacion-2.html): `navigation/AppNavigation.kt` contiene el acceso público y la entrada al dashboard. `features/dashboard/MainScreen.kt` mantiene un único `Scaffold`, la barra inferior y los mensajes compartidos; `features/dashboard/navigation/DashboardNavigation.kt` contiene su `NavHost` interno. Las pantallas conservan sus features y ViewModels, y usan contenedores de contenido sin repetir el `Scaffold` del dashboard. El selector de ubicación es un diálogo de pantalla completa independiente.

Inicio, Mapa, Mis eventos y Perfil mantienen los mismos accesos y el elemento seleccionado. Los administradores tienen además **Admin**, disponible también desde crear/editar y explorar eventos para volver al panel. Panel, Moderación y Usuarios son pestañas internas del área administrativa. Cambiar de sección conserva su estado sin apilar copias; crear/editar eventos, el detalle y las notificaciones se cierran al elegir otra sección. Cerrar sesión elimina la navegación autenticada.

## Arquitectura y servicios activos

`Screen → ViewModel → UseCases → Repository (interfaz) → Firebase`

- `core/di/RepositoryModule.kt` enlaza las implementaciones Firebase. Los repositorios en memoria se conservan como dobles aislados para pruebas; la aplicación no siembra cuentas o eventos de demostración.
- Authentication gestiona registro, acceso, restauración de sesión, cierre de sesión, recuperación de contraseña y eliminación de cuenta. Las contraseñas no se almacenan en Firestore.
- Firestore conserva perfiles, eventos, inscripciones, comentarios, votos, favoritos, seguimientos y notificaciones internas. Los listeners actualizan las pantallas cuando cambian los datos.
- Storage guarda las fotos opcionales de eventos y perfiles. Se eligen con el selector de Android, se validan como imágenes de hasta 10 MB y se guarda su URL remota en Firestore. Las pantallas muestran imágenes con Coil.
- Las operaciones remotas son `suspend`; los ViewModels usan `viewModelScope`, estados de carga y mensajes de error. Un fallo de autorización o conexión no se presenta como un guardado exitoso.

Se conservan `core`, `domain`, `features` y `navigation`. Las versiones se administran mediante `gradle/libs.versions.toml`. Google Services 4.5.0 y Firebase BoM 34.11.0 mantienen compatibilidad con Kotlin 2.2.10 del proyecto. Coroutines Play Services permite esperar las tareas de Firebase sin bloquear la interfaz; Coil carga las imágenes locales/remotas. Las dependencias de pruebas Hilt solo afectan `androidTest`.

## Configuración del proyecto

### Ejecutar la misma versión en el equipo de un compañero

El repositorio contiene código y dependencias; dos archivos de configuración local están excluidos de Git. Para utilizar los mismos servicios desplegados, conservar `applicationId=com.eventoscomunitarios.app` y el proyecto Firebase `share-you-6a706`.

1. Subir todos los cambios de la versión, incluidos los archivos nuevos que muestra `git status --short`: código Android, recursos ES/EN, pruebas, `functions/src`, `functions/package.json`, `functions/package-lock.json`, `firebase.json`, `.firebaserc`, scripts, Gradle y documentación. No se necesitan `build`, `functions/lib` ni `node_modules`; se generan localmente.
2. El compañero clona el repositorio o hace `git pull` de la misma rama/commit y abre la carpeta raíz `appMovil` en Android Studio.
3. Instala/configura un Android Studio compatible con AGP 9.4.1, Android SDK Platform 37 y Microsoft OpenJDK 25 completo para Gradle. El wrapper incluido descarga Gradle 9.6.0. No debe sustituir versiones del catálogo para resolver la configuración de su equipo.
4. Coloca en `app/google-services.json` el archivo de la aplicación Android ya registrada en **Share You**. Puede recibir una copia del archivo actual por el canal del equipo o descargarlo desde [Firebase Console](https://console.firebase.google.com/project/share-you-6a706/settings/general) → Configuración del proyecto → Tus apps → `com.eventoscomunitarios.app` → Descargar `google-services.json`. No necesita crear otra app Firebase ni otro proyecto. Ese archivo no contiene la credencial de Gemini.
5. Conserva el `sdk.dir` de `local.properties` que genere Android Studio en su máquina y añade `MAPBOX_ACCESS_TOKEN` con el token público Mapbox del equipo o uno propio. Consultar `local.properties.example`; las rutas deben corresponder a su equipo. No copiar el `sdk.dir` de otro desarrollador.
6. Sincroniza Gradle y ejecuta la variante **debug** con internet en un emulador `x86_64` con Google APIs o un teléfono `arm64`, Android 7/API 24 o posterior. La compilación utiliza API 37; el dispositivo no necesita ser API 37. Para las capturas, inicia sesión o registra una cuenta con el rol correspondiente; los roles administrativos se asignan desde el proyecto, no desde el registro público.

Ejemplo de la configuración local; reemplazar ruta y token:

```properties
sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk
MAPBOX_ACCESS_TOKEN=pk.REEMPLAZAR_CON_TOKEN_PUBLICO
```

| Elemento | Cómo llega al compañero |
| --- | --- |
| Código Kotlin, XML, Gradle, scripts y fuentes de Functions | Git; sincronizar la misma rama y commit. |
| `app/google-services.json` | Copia del equipo o descarga desde el proyecto Firebase existente. Está excluido de Git. |
| `local.properties` | Lo configura en su PC con su ruta SDK y un token público Mapbox. Está excluido de Git. |
| `GEMINI_API_KEY` | Permanece en Secret Manager. La app llama a las funciones desplegadas; no necesita esta clave en su PC ni APK. |
| App Check | Desactivado en Android y en las funciones de IA; no requiere registrar dispositivos. |
| `functions/.env.share-you-6a706` | Solo para quien administre/despliegue el backend. No hace falta para ejecutar la app Android. |

### Acceso del equipo

App Check está desactivado para esta entrega académica. No se instalan sus proveedores debug/Play Integrity en Android ni se exige su token en `aiStatus`, `analyzeText` o `analyzeEventDraft`. El compañero y el docente pueden ejecutar la APK sin registrar el emulador/teléfono ni recibir permisos de administración de App Check.

Las funciones de IA conservan Firebase Authentication, la comprobación de cuenta activa, los roles necesarios para analizar eventos y la cuota diaria. Firestore y Storage conservan sus reglas de acceso. La cuenta para acceder a la app se administra por Firebase Authentication, independientemente de los permisos de desarrollador en Google Cloud.

Dentro de la aplicación, las vistas y acciones siguen dependiendo del perfil `users/{uid}` de su cuenta Firebase Authentication: `PARTICIPANT`, `ORGANIZER` verificado o `ADMINISTRATOR`. Invitar su correo como visualizador del proyecto no crea ni convierte automáticamente su cuenta de la aplicación en administradora.

### Compilar o trabajar en el backend

Para compilar Android y ejecutar las pruebas JVM desde la raíz:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Para trabajar además en las fuentes de Cloud Functions, instalar Node.js 22 y ejecutar:

```powershell
npm --prefix functions ci
npm --prefix functions run check
```

Node, Firebase CLI y Google Cloud CLI no son necesarios para ejecutar la app Android. No necesita desplegar de nuevo las funciones ni las reglas para usar el backend actual. El despliegue es una tarea del responsable del backend, con su configuración local y permisos; está documentado en [functions/README.md](functions/README.md).

### Capturas y alcance del informe

Para solo instalar y hacer capturas, se puede compartir `app/build/outputs/apk/debug/app-debug.apk`, ya compilada con Firebase y Mapbox. En ese caso no necesita Android Studio ni configurar los archivos anteriores. La APK se comparte por el canal de entrega del equipo, no se genera al hacer `git pull`.

La versión actual tiene prevalidaciones de IA desplegadas y probadas, pero los formularios todavía no las llaman. El informe debe distinguir el backend validado de la integración de UI, guardado validado en servidor y “Aportar información”, que siguen pendientes. Los resultados de pruebas locales bajo `build/` y `app/build/` tampoco viajan por Git; se pueden reproducir con los comandos documentados o compartir sus informes por el canal de entrega.

La aplicación Android `com.eventoscomunitarios.app` usa `share-you-6a706`, definido en `app/google-services.json`. El plugin Google Services lo procesa y los tres SDK comparten la aplicación Firebase predeterminada. No requiere `.env` ni claves de cuentas de servicio.

En la consola deben estar disponibles:

1. Authentication con Correo electrónico/contraseña habilitado.
2. Firestore en la base predeterminada `(default)`.
3. El bucket `share-you-6a706.firebasestorage.app` de Storage; el servicio requiere el plan Blaze.

`firestore.rules` contiene los permisos de perfiles, verificación privada de organizadores, eventos, inscripciones, comentarios, favoritos, votos, seguimientos, contadores y notificaciones. Exige ubicación confirmada y autor autorizado para crear/editar eventos, protege la aprobación del administrador y restringe las reseñas a participantes confirmados cuando el evento ha finalizado. Para publicar cambios posteriores, abrir **Firebase Console → Firestore Database → Reglas**, reemplazar el contenido con el archivo completo y pulsar **Publicar**. Guardar el archivo local o compilar la APK no publica las reglas.

No usar `allow read, write: if true` ni dar escritura global a cualquier usuario autenticado. Los listeners actuales consultan las colecciones completas de perfiles y eventos: usuarios autenticados pueden leer correo/teléfono y publicaciones pendientes aunque la interfaz filtre lo que muestra. Separar datos privados y limitar esas consultas requiere ajustar el esquema y los listeners. La lista de notificaciones de la app consulta solo al destinatario; creador/moderador también tienen acceso a avisos ligados a su evento para la limpieza al eliminarlo.

Para comprobar estas reglas sin tocar Firebase real, ejecutar `./scripts/test-firestore-rules.ps1` con Firebase CLI, Node y un JDK completo instalados. Usa exclusivamente el proyecto ficticio `demo-share-you-rules` y los puertos locales 19099 (Auth) y 18080 (Firestore). Incluye pruebas de permisos concedidos/denegados, inscripciones, lista de espera, reseñas, relanzamientos y favoritos.

`storage.rules` se publica por separado en **Firebase Console → Storage → Reglas → Publicar**. Autoriza consultar fotos públicas a usuarios autenticados y crear imágenes de hasta 10 MB en `profiles/{uid}/{archivo}`, `events/{uid}/{archivo}` e `identities/{uid}/{archivo}`, solo cuando el UID y el metadato `ownerId` corresponden al usuario conectado. Las fotos de identidad solo son consultables por su dueño o por un administrador activo, cuyo rol se comprueba en Firestore. Solo el dueño de la carpeta puede borrar sus imágenes. Cada subida usa un UUID nuevo: los listados, las sobrescrituras y las demás rutas están denegados. La limpieza de imágenes ajenas por un moderador requiere un proceso con permisos propios; el cliente actual trata ese borrado como mejor esfuerzo.

Las consultas `firestore.get()` desde Storage requieren además el permiso entre servicios descrito en la [documentación de Firebase](https://firebase.google.com/docs/rules/manage-deploy#manage_permissions_for_cross-service_cloud_storage_security_rules). El agente de Storage `service-381076241260@gcp-sa-firebasestorage.iam.gserviceaccount.com` debe tener el rol IAM `roles/firebaserules.firestoreServiceAgent` en `share-you-6a706`. Sin él, la subida del propietario funciona, pero el administrador recibe un error de autorización al consultar una cédula ajena. Este permiso se habilitó en el proyecto el 10 de noviembre de 2026.

Para configurar otro proyecto, publicar desde la consola y aceptar la conexión con Firestore, o asignar ese rol al agente de Storage del proyecto. La versión instalada de Firebase CLI omite esa comprobación al desplegar con `--non-interactive`; un despliegue exitoso no garantiza que el permiso IAM exista. Las pruebas de los emuladores verifican las reglas, pero no los permisos IAM de producción.

Ejecutar `./scripts/test-storage-rules.ps1` para validar Storage con Auth (19099) y Storage (19199) locales, proyecto ficticio `demo-share-you-storage-rules`. Las pruebas no publican reglas ni acceden al bucket real. Las URL de descarga con token que guarda la app se pueden compartir; quien tenga esa URL podrá acceder a la imagen.

Las cuentas de la versión anterior eran simuladas y no existen automáticamente en Firebase. Hay que registrarse desde la aplicación. El registro público permite Participante u Organizador. Para probar moderación, el responsable del proyecto puede asignar `role: "ADMINISTRATOR"` al documento `users/{uid}` de una cuenta ya registrada desde la consola. El formulario de registro y editar perfil no permiten elevar el rol.

## Datos en Firestore y Storage

| Ruta | Contenido |
| --- | --- |
| `users/{uid}` | Perfil vinculado a Authentication: nombre, correo, rol, estado, teléfono, foto, fecha y correo pendiente de verificación. |
| `events/{id}` | Fechas de inicio/fin, categoría, cupo, autor, foto y moderación. `latitude`, `longitude`, `locationConfirmed` e `isOutdoor` describen el sitio. `seriesId` vincula ediciones y `previousEventId` indica la anterior. Las inscripciones pertenecen a cada edición (`registrations` / `registrationUserIds`). |
| `comments/{id}` | Evento, autor, texto y fecha. |
| `votes/{id}` | Un voto por combinación usuario/evento. |
| `favorites/{id}` | Un favorito por combinación usuario/serie. `eventId` conserva el ID raíz de la serie; `sourceEventId` indica dónde se marcó y `organizerId` permite limpiar la suscripción al retirar la última edición. Los favoritos anteriores de eventos originales siguen siendo válidos. |
| `following/{id}` | Un seguimiento por combinación usuario/organizador. |
| `notifications/{id}` | Destinatario, título, mensaje, evento opcional y estado de lectura. El listener consulta solo el destinatario actual. |
| `metadata/counters` | Contadores transaccionales `events`, `comments` y `notifications`; conserva los identificadores enteros usados por las rutas. |
| Storage `events/{uid}/{uuid}` / `profiles/{uid}/{uuid}` | Imágenes; los documentos guardan su URL. |

Los votos, favoritos y seguimientos usan identificadores deterministas y transacciones para no duplicarse. Cupo, inscripciones y orden de espera se modifican juntos dentro de una transacción del evento; una inscripción concurrente no sobrescribe otra. Este esquema es adecuado para el alcance académico y queda sujeto al límite de tamaño de los documentos de Firestore.

## Funciones preservadas

- Crear y editar eventos con inicio/fin reales, ubicación confirmada en el mapa, opción de aire libre, cupo, precio, foto opcional y siete categorías: Deportes, Cultura, Comunidad, Educación, Medio ambiente, Salud y Tecnología.
- Moderar con aprobación o rechazo y motivo obligatorio. Una edición vuelve a revisión. Solo autor/moderador pueden editar, eliminar o finalizar; un evento futuro no se puede finalizar anticipadamente.
- Confirmar asistencia, cancelar y promover participantes de la lista de espera en orden de llegada.
- Comentar solo después de finalizar el evento y con inscripción confirmada; estar en espera o cancelar no habilita reseñas. La inscripción confirmada representa participación en este modelo; no existe control de ingreso físico/QR. Se conservan eliminación de comentarios propios/moderación y votos positivo/negativo.
- Guardar un evento en favoritos conserva el interés en sus siguientes ediciones. El creador puede volver a lanzar un evento aprobado que terminó, escoger fechas nuevas y enviarlo a aprobación. Cada edición tiene ID, cupo, inscripciones, comentarios y votos propios; el historial anterior permanece separado.
- Notificaciones internas, lectura individual/todas y navegación al evento; no son notificaciones push. El organizador recibe avisos de likes, dislikes y nuevos comentarios/aportes de otras personas, con el nombre del autor y el evento. Cambiar de like a dislike (o viceversa) genera el aviso de la nueva reacción; retirarla no genera un aviso. Al aprobar una nueva edición se avisa a quienes la tienen en favoritos y a seguidores del organizador, sin duplicar el aviso si cumplen ambos criterios. El destinatario decide inscribirse nuevamente.
- Editar perfil, foto y teléfono. Un cambio de correo solicita contraseña y envía verificación al correo nuevo; mantiene el anterior hasta verificar. Actualizar sesión sincroniza el correo confirmado de Auth.
- Eliminar cuenta con contraseña actual: primero reautentica y después limpia datos relacionados y elimina la cuenta Auth. Auth, Firestore y Storage no comparten una transacción; los errores se pueden reintentar y la limpieza de imágenes/notificaciones auxiliares es de mejor esfuerzo.
- Estadísticas y reputación: `máximo(0, votos netos recibidos en eventos aprobados + 5 × eventos finalizados)`.
- Mapbox centra el mapa en una ubicación reciente del dispositivo si hay permiso y se logra obtenerla; usa Armenia como respaldo. Inicio y Mapa permiten buscar en otro punto, conservan sus filtros y calculan la distancia desde ese punto; el campo histórico `distanceKm` guardado en Firestore no se usa como distancia común para todos.

## Elegir ubicación y volver a lanzar

En crear/editar, **Elegir en el mapa** abre el selector. El GPS centra la cámara, pero no confirma automáticamente el lugar: es necesario tocar el sitio y pulsar **Confirmar ubicación**. La dirección se sugiere con `Geocoder` de Android; si el servicio no está disponible o no encuentra dirección, se completa una referencia manual manteniendo las coordenadas. La selección manual funciona sin permiso de ubicación. Las respuestas atrasadas de GPS/geocodificación no reemplazan un punto o referencia más recientes. No se añadieron dependencias.

Los eventos anteriores sin `locationConfirmed` se consideran sin ubicación confirmada y se excluyen de filtros geográficos. El creador puede confirmar el punto al editar un evento futuro o al preparar su nueva edición; no se dan por válidas las antiguas coordenadas predeterminadas.

**Volver a lanzar** aparece en Mis eventos → Creados y en el detalle del evento terminado para su creador. Copia información, sitio, foto y horas, y exige elegir nuevas fechas. Crea otra publicación pendiente de aprobación; no copia inscripciones ni reseñas. Las fotos reutilizadas se conservan mientras otra edición las use. La repetición es manual: cada fecha pasa por aprobación y cada persona confirma nuevamente su asistencia.

## Compilar y comprobar

La configuración de Cloud Functions y sus pruebas está en [functions/README.md](functions/README.md). El informe detallado de cambios está en [IMPLEMENTACION_IA_INFORME.md](IMPLEMENTACION_IA_INFORME.md). Crear, editar y volver a lanzar eventos y publicar comentarios/aportes ya invocan Gemini mediante `analyzeEventDraft`/`analyzeText`. Se muestran rechazos, sugerencias y posibles duplicados con acceso al evento existente. Firestore exige un comprobante del servidor, vigente y de un solo uso, vinculado al contenido exacto. La revisión administrativa posterior se conserva. Requieren sesión y los permisos correspondientes, sin App Check; Gemini usa un proyecto independiente sin facturación. **Instalar la APK actualizada:** los clientes anteriores no envían el comprobante exigido por las reglas nuevas.

Abrir en Android Studio, sincronizar Gradle y ejecutar `app`. Se mantienen SDK/Gradle del proyecto. El criterio JVM solicita **Microsoft OpenJDK 25** (`toolchainVendor=MICROSOFT`); `JAVA_HOME` debe apuntar a un **JDK completo**, con `jlink`. Usar el mismo JDK para Gradle en Android Studio. El proveedor se fija en `gradle/gradle-daemon-jvm.properties` porque esos criterios tienen prioridad sobre el JDK seleccionado en el IDE y evitan reutilizar el Java incompleto de una extensión.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon
.\scripts\start-firebase-emulators.ps1 -RunTests
```

Las pruebas de negocio/ViewModel e interfaz usan repositorios en memoria aislados. `TestRepositoryModule` reemplaza exclusivamente los bindings de pruebas; la APK normal usa Firebase.

Las pruebas `FirebaseIntegrationTest` usan los SDK y repositorios reales contra **Firebase Emulator Suite**, proyecto ficticio `demo-share-you`. Nunca usan los servicios de producción. Necesitan un emulador Android para acceder al equipo anfitrión por `10.0.2.2`. El comando `start-firebase-emulators.ps1 -RunTests` inicia los servicios, ejecuta toda la suite Android con integración habilitada y apaga los servicios al terminar. Sin ese argumento, el script permite mantenerlos abiertos y ejecutar las pruebas desde otra terminal.

En una terminal, con Firebase CLI y Java instalados:

```powershell
.\scripts\start-firebase-emulators.ps1
```

En otra terminal, con el emulador Android conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true --no-daemon
```

El script genera configuración bajo `build/firebase-emulators/`, copia las reglas reales del repositorio e inicia Auth (9099), Firestore (8080) y Storage (9199). No despliega nada. Las fixtures administrativas (rol y fechas del pasado) usan exclusivamente la API del emulador, no permisos abiertos en las reglas. La configuración HTTP local solo se incluye en debug; release mantiene HTTPS. Las pruebas cubren autenticación, persistencia entre clientes, cupos concurrentes, promoción de espera, reseñas, nuevas ediciones, favoritos, avisos e imágenes.

Informes: `app/build/reports/`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Las capturas de las nuevas pantallas se exportan como salida adicional de las pruebas Android. Las comprobaciones locales no publican reglas; el acceso al proyecto real depende de aplicar sus archivos desde Firebase Console.

## Ejecutar desde otro equipo y generar una APK

Actualizar la misma rama de Git y conservar en el equipo `local.properties` y `app/google-services.json`; están excluidos del repositorio. El JSON debe corresponder al proyecto `share-you-6a706` y al paquete `com.eventoscomunitarios.app`.

Para ejecutar desde el código, sincronizar Gradle, seleccionar la configuración `app` y un dispositivo y pulsar Run. Android Studio compila e instala la APK en ese dispositivo automáticamente.

Para generar el archivo APK manualmente, abrir la terminal en la raíz del proyecto y ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
```

La salida es `app/build/outputs/apk/debug/app-debug.apk`. Cada compañero puede generar ese archivo desde su copia actualizada. Comparar instalaciones requiere el mismo código y configuración Firebase; generar una APK manualmente utiliza el mismo flujo de compilación de debug que ejecutar `app`.

Para diagnosticar login en una compilación debug, abrir Logcat y filtrar `tag:ShareYouAuth`. Después de un intento fallido aparece una línea con `stage`, `code`, `project` y `appId`. `AUTHENTICATION` identifica la llamada de autenticación y `PROFILE` la consulta/validación posterior del perfil. El registro no incluye correo, contraseña, claves, tokens ni el cuerpo del error y está desactivado en release. Estos cambios de diagnóstico deben estar incluidos en el commit compartido para que aparezcan al compilar en otro equipo.

Cuando falla una cuenta existente, se puede comparar el acceso desde Windows con el del emulador. En la Terminal de Android Studio, situada en la raíz del proyecto, ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-login.ps1
```

El script pide el correo de una cuenta existente y su contraseña de forma oculta, y realiza un inicio de sesión mediante la [API oficial de Firebase Authentication](https://firebase.google.com/docs/reference/rest/auth#section-sign-in-email-password). No registra cuentas, restablece contraseñas ni modifica permisos o reglas. No requiere Firebase CLI, permisos administrativos ni dependencias adicionales. No imprime ni guarda contraseñas, claves o tokens; compartir únicamente el bloque entre `INICIO DIAGNOSTICO SHARE YOU` y `FIN DIAGNOSTICO SHARE YOU`.

Comprueba el proyecto al que apunta realmente la clave, compara el JSON con los recursos debug generados por Gradle y muestra `login`, `loginHttp` y `loginCode`. `keyId` es una huella SHA-256 truncada de la clave de configuración, también incluida en el log debug de un login fallido; permite comparar equipos e instalaciones sin compartir la clave. No se calcula ni registra ninguna huella de la contraseña. La comprobación del proyecto usa la configuración pública de Identity Toolkit. Si detecta otro proyecto, omite el login. Una restricción que impida solicitudes desde Windows puede rechazar esta prueba aunque permita Android; el resultado se debe comparar con Logcat, no usarlo como sustituto del SDK.

Si el acceso directo devuelve `login: OK` y en ese mismo equipo Android falla con la misma cuenta, el rechazo no se reproduce fuera del emulador: revisar la configuración de la aplicación instalada y la entrada del formulario. Si devuelve `INVALID_LOGIN_CREDENTIALS` o `INVALID_PASSWORD`, Firebase también rechaza esa combinación fuera de Android; comparar los informes de ambos equipos con la misma cuenta, sin publicar sus credenciales. Un resultado correcto de la prueba directa no demuestra por sí solo que el login del emulador esté corregido.

Para comprobar únicamente configuración, sin pedir credenciales ni iniciar sesión:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-login.ps1 -SoloConfiguracion
```
