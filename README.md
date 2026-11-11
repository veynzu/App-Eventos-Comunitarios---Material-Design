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
- Notificaciones internas, lectura individual/todas y navegación al evento; no son notificaciones push. Al aprobar una nueva edición se avisa a quienes la tienen en favoritos y a seguidores del organizador, sin duplicar el aviso si cumplen ambos criterios. El destinatario decide inscribirse nuevamente.
- Editar perfil, foto y teléfono. Un cambio de correo solicita contraseña y envía verificación al correo nuevo; mantiene el anterior hasta verificar. Actualizar sesión sincroniza el correo confirmado de Auth.
- Eliminar cuenta con contraseña actual: primero reautentica y después limpia datos relacionados y elimina la cuenta Auth. Auth, Firestore y Storage no comparten una transacción; los errores se pueden reintentar y la limpieza de imágenes/notificaciones auxiliares es de mejor esfuerzo.
- Estadísticas y reputación: `máximo(0, votos netos recibidos en eventos aprobados + 5 × eventos finalizados)`.
- Mapbox centra el mapa en una ubicación reciente del dispositivo si hay permiso y se logra obtenerla; usa Armenia como respaldo. Inicio y Mapa permiten buscar en otro punto, conservan sus filtros y calculan la distancia desde ese punto; el campo histórico `distanceKm` guardado en Firestore no se usa como distancia común para todos.

## Elegir ubicación y volver a lanzar

En crear/editar, **Elegir en el mapa** abre el selector. El GPS centra la cámara, pero no confirma automáticamente el lugar: es necesario tocar el sitio y pulsar **Confirmar ubicación**. La dirección se sugiere con `Geocoder` de Android; si el servicio no está disponible o no encuentra dirección, se completa una referencia manual manteniendo las coordenadas. La selección manual funciona sin permiso de ubicación. Las respuestas atrasadas de GPS/geocodificación no reemplazan un punto o referencia más recientes. No se añadieron dependencias.

Los eventos anteriores sin `locationConfirmed` se consideran sin ubicación confirmada y se excluyen de filtros geográficos. El creador puede confirmar el punto al editar un evento futuro o al preparar su nueva edición; no se dan por válidas las antiguas coordenadas predeterminadas.

**Volver a lanzar** aparece en Mis eventos → Creados y en el detalle del evento terminado para su creador. Copia información, sitio, foto y horas, y exige elegir nuevas fechas. Crea otra publicación pendiente de aprobación; no copia inscripciones ni reseñas. Las fotos reutilizadas se conservan mientras otra edición las use. La repetición es manual: cada fecha pasa por aprobación y cada persona confirma nuevamente su asistencia.

## Compilar y comprobar

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
