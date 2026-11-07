# Share You — aplicación Android con Firebase

Aplicación académica de eventos comunitarios. Conserva Kotlin, Compose, Material 3, MVVM con `UiState`/`StateFlow`, rutas `@Serializable`, casos de uso, interfaces de repositorio e inyección con Hilt.

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

**Las reglas del proyecto quedan para la siguiente etapa, por solicitud del usuario.** Este cambio no publica reglas, no habilita acceso público ni modifica facturación. Si las reglas actuales deniegan una operación, la aplicación muestra el error correspondiente. La configuración local del SDK no demuestra que los servicios o sus permisos estén habilitados en la nube.

Las cuentas de la versión anterior eran simuladas y no existen automáticamente en Firebase. Hay que registrarse desde la aplicación. El registro público permite Participante u Organizador. Para probar moderación, el responsable del proyecto puede asignar `role: "ADMINISTRATOR"` al documento `users/{uid}` de una cuenta ya registrada desde la consola. El formulario de registro y editar perfil no permiten elevar el rol.

## Datos en Firestore y Storage

| Ruta | Contenido |
| --- | --- |
| `users/{uid}` | Perfil vinculado a Authentication: nombre, correo, rol, estado, teléfono, foto, fecha y correo pendiente de verificación. |
| `events/{id}` | Fechas de inicio/fin en milisegundos, categoría, cupo, autor, foto, moderación y estado. Incluye el mapa `registrations` por UID y `registrationUserIds`. |
| `comments/{id}` | Evento, autor, texto y fecha. |
| `votes/{id}` | Un voto por combinación usuario/evento. |
| `favorites/{id}` | Un favorito por combinación usuario/evento. |
| `following/{id}` | Un seguimiento por combinación usuario/organizador. |
| `notifications/{id}` | Destinatario, título, mensaje, evento opcional y estado de lectura. El listener consulta solo el destinatario actual. |
| `metadata/counters` | Contadores transaccionales `events`, `comments` y `notifications`; conserva los identificadores enteros usados por las rutas. |
| Storage `events/{uid}/{uuid}` / `profiles/{uid}/{uuid}` | Imágenes; los documentos guardan su URL. |

Los votos, favoritos y seguimientos usan identificadores deterministas y transacciones para no duplicarse. Cupo, inscripciones y orden de espera se modifican juntos dentro de una transacción del evento; una inscripción concurrente no sobrescribe otra. Este esquema es adecuado para el alcance académico y queda sujeto al límite de tamaño de los documentos de Firestore.

## Funciones preservadas

- Crear y editar eventos con inicio/fin reales, dirección, cupo, precio, foto opcional y siete categorías: Deportes, Cultura, Comunidad, Educación, Medio ambiente, Salud y Tecnología.
- Moderar con aprobación o rechazo y motivo obligatorio. Una edición vuelve a revisión. Solo autor/moderador pueden editar, eliminar o finalizar; un evento futuro no se puede finalizar anticipadamente.
- Confirmar asistencia, cancelar y promover participantes de la lista de espera en orden de llegada.
- Comentar, eliminar comentarios propios o como moderador, votar positivo/negativo, guardar favoritos y seguir organizadores.
- Notificaciones internas, lectura individual/todas y navegación al evento; no son notificaciones push.
- Editar perfil, foto y teléfono. Un cambio de correo solicita contraseña y envía verificación al correo nuevo; mantiene el anterior hasta verificar. Actualizar sesión sincroniza el correo confirmado de Auth.
- Eliminar cuenta con contraseña actual: primero reautentica y después limpia datos relacionados y elimina la cuenta Auth. Auth, Firestore y Storage no comparten una transacción; los errores se pueden reintentar y la limpieza de imágenes/notificaciones auxiliares es de mejor esfuerzo.
- Estadísticas y reputación: `máximo(0, votos netos recibidos en eventos aprobados + 5 × eventos finalizados)`.
- Mapas existentes conservados. Ninguna selección de foto requiere permiso general de acceso a toda la galería.

## Compilar y comprobar

Abrir en Android Studio, sincronizar Gradle y ejecutar `app`. Se mantienen SDK/Gradle del proyecto. El criterio JVM solicita JDK 25; `JAVA_HOME` debe apuntar a un **JDK completo**, con `jlink`. Usar el mismo JDK para Gradle en Android Studio.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Las pruebas de negocio/ViewModel e interfaz usan repositorios en memoria aislados. `TestRepositoryModule` reemplaza exclusivamente los bindings de pruebas; la APK normal usa Firebase.

Las pruebas `FirebaseIntegrationTest` usan los SDK y repositorios reales contra **Firebase Emulator Suite**, proyecto ficticio `demo-share-you`. Nunca usan los servicios de producción. Son opcionales en la suite habitual y necesitan un emulador Android para acceder al equipo anfitrión por `10.0.2.2`.

En una terminal, con Firebase CLI y Java instalados:

```powershell
.\scripts\start-firebase-emulators.ps1
```

En otra terminal, con el emulador Android conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true --no-daemon
```

El script genera configuración y reglas mínimas **solo de pruebas** bajo `build/firebase-emulators/`, inicia Auth (9099), Firestore (8080) y Storage (9199) y no despliega nada. La configuración HTTP local solo se incluye en debug; release mantiene HTTPS. Las pruebas cubren autenticación, persistencia entre clientes, rechazo de contraseña incorrecta antes de borrar, cupos concurrentes, promoción de espera, interacciones, avisos e imágenes.

Informes: `app/build/reports/`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Verificación del 7 de noviembre de 2026: **22 pruebas JVM y 36 pruebas Android aprobadas**, incluyendo los tres escenarios de integración con Firebase Emulator Suite; **Android Lint: 0 errores y 31 avisos** (principalmente versiones y recursos existentes). El APK debug se generó correctamente. No se ejecutaron pruebas contra el proyecto real; su acceso queda condicionado a los servicios y reglas de la consola.
