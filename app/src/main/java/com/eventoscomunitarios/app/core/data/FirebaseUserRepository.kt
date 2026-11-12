package com.eventoscomunitarios.app.core.data

import android.content.pm.ApplicationInfo
import android.util.Log
import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.RepositorySyncState
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.requireActiveAccount
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.domain.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/** Auth conserva las credenciales; Firestore guarda únicamente datos del perfil. */
@Singleton
class FirebaseUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val mediaRepository: MediaRepository? = null
) : UserRepository {
    private val mutableUsers = MutableStateFlow<List<User>>(emptyList())
    private val mutableSyncState = MutableStateFlow(RepositorySyncState(isLoading = true))
    private val mutableCurrentUserId = MutableStateFlow(auth.currentUser?.uid)
    private val mutableSessionReady = MutableStateFlow(false)
    override val users = mutableUsers.asStateFlow()
    override val syncState = mutableSyncState.asStateFlow()
    override val currentUserId = mutableCurrentUserId.asStateFlow()
    override val sessionReady = mutableSessionReady.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var profilesListener: ListenerRegistration? = null
    private var restoreJob: Job? = null
    private var listenerGeneration = 0
    private var creatingAccount = false
    private var signingIn = false
    private var pendingProfiles: List<User>? = null
    private var deletingAccount = false
    private val authListener = FirebaseAuth.AuthStateListener { bindSession(it.currentUser) }

    init { auth.setLanguageCode("es"); auth.addAuthStateListener(authListener) }

    override suspend fun login(email: String, password: String): User {
        check(!signingIn) { "Ya hay un inicio de sesión en curso." }
        signingIn = true
        var stage = "AUTHENTICATION"
        return try {
            val credential = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = requireNotNull(credential.user) { "No se pudo iniciar sesión." }
            stage = "PROFILE"
            val profile = loadProfile(firebaseUser, publish = false)
            profile.requireActiveAccount()
            signingIn = false
            bindSession(firebaseUser)
            publishProfile(profile)
            profile
        } catch (error: Exception) {
            // Diagnóstico exclusivo de debug: nunca registrar credenciales, tokens ni el cuerpo del error.
            if (error !is CancellationException &&
                auth.app.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                val code = when (error) {
                    is FirebaseAuthException -> error.errorCode
                    is FirebaseFirestoreException -> error.code.name
                    else -> error.javaClass.simpleName
                }
                Log.e("ShareYouAuth", "login_failed stage=$stage code=$code project=${auth.app.options.projectId} appId=${auth.app.options.applicationId}")
            }
            signOut()
            throw error
        } finally {
            signingIn = false
        }
    }

    override suspend fun register(user: User, password: String, identityPhotoUri: String?): User {
        require(user.role != UserRole.ORGANIZER || identityPhotoUri?.startsWith("content://") == true) {
            "Adjunta una foto de tu cédula para solicitar la verificación"
        }
        require(user.role != UserRole.ADMINISTRATOR) { "No es posible registrar moderadores desde la aplicación." }
        creatingAccount = true
        var createdAuthUser: FirebaseUser? = null
        var uploadedIdentity: String? = null
        try {
            createdAuthUser = requireNotNull(auth.createUserWithEmailAndPassword(user.email, password).await().user)
            val profile = user.copy(id = createdAuthUser.uid, email = createdAuthUser.email ?: user.email,
                registrationDate = Date(), accountStatus = AccountStatus.ACTIVE, pendingEmail = null,
                organizerVerificationStatus = if (user.role == UserRole.ORGANIZER) OrganizerVerificationStatus.PENDING
                    else OrganizerVerificationStatus.NOT_REQUIRED)
            val batch = firestore.batch()
            batch.set(firestore.collection("users").document(profile.id), profile.toDocument())
            if (profile.role == UserRole.ORGANIZER) {
                uploadedIdentity = requireNotNull(mediaRepository).uploadImage(profile.id, requireNotNull(identityPhotoUri), "identities")
                batch.set(firestore.collection("organizerVerifications").document(profile.id),
                    mapOf("userId" to profile.id, "photoPath" to uploadedIdentity, "submittedAt" to Timestamp.now()))
            }
            batch.commit().await()
            // Conserva los demás perfiles recibidos mientras esperábamos la confirmación del registro.
            mutableUsers.value = pendingProfiles.orEmpty()
            pendingProfiles = null
            publishProfile(profile)
            return profile
        } catch (error: Exception) {
            // Si el perfil no pudo guardarse, intenta deshacer únicamente la cuenta recién creada.
            createdAuthUser?.let {
                withContext(NonCancellable) {
                    try { withTimeout(5_000) {
                        try { uploadedIdentity?.let { path -> mediaRepository?.deleteImage(path) } } catch (_: Exception) { }
                        try { firestore.collection("organizerVerifications").document(it.uid).delete().await() } catch (_: Exception) { }
                        try { firestore.collection("users").document(it.uid).delete().await() } catch (_: Exception) { }
                        it.delete().await()
                    } } catch (_: Exception) { /* El inicio de sesión explicará si falta el perfil. */ }
                }
                signOut()
            }
            throw error
        } finally {
            creatingAccount = false
            pendingProfiles = null
        }
    }

    override suspend fun organizerIdentityPath(userId: String): String? =
        firestore.collection("organizerVerifications").document(userId).get(Source.SERVER).await().getString("photoPath")

    override suspend fun saveOrganizerIdentity(userId: String, photoPath: String): User {
        requireCurrentUser(userId)
        val userRef = firestore.collection("users").document(userId)
        val identityRef = firestore.collection("organizerVerifications").document(userId)
        val updated = firestore.runTransaction { transaction ->
            val current = requireNotNull(transaction.get(userRef).toUser()) { "Usuario no encontrado" }
            require(current.role == UserRole.ORGANIZER && current.accountStatus == AccountStatus.ACTIVE &&
                current.organizerVerificationStatus != OrganizerVerificationStatus.VERIFIED) { "Esta cuenta no puede enviar documentos" }
            val next = current.copy(organizerVerificationStatus = OrganizerVerificationStatus.PENDING)
            transaction.set(identityRef, mapOf("userId" to userId, "photoPath" to photoPath, "submittedAt" to Timestamp.now()))
            transaction.update(userRef, "organizerVerificationStatus", next.organizerVerificationStatus.name)
            next
        }.await()
        publishProfile(updated)
        return updated
    }

    override suspend fun reviewOrganizerIdentity(userId: String, expectedPhotoPath: String, approve: Boolean): User {
        val actorId = requireNotNull(auth.currentUser?.uid) { "Inicia sesión" }
        val userRef = firestore.collection("users").document(userId)
        val updated = firestore.runTransaction { transaction ->
            val actor = requireNotNull(transaction.get(firestore.collection("users").document(actorId)).toUser())
            val target = requireNotNull(transaction.get(userRef).toUser()) { "Usuario no encontrado" }
            val identity = transaction.get(firestore.collection("organizerVerifications").document(userId))
            require(actor.role == UserRole.ADMINISTRATOR && actor.accountStatus == AccountStatus.ACTIVE) { "Solo un administrador puede verificar organizadores" }
            require(target.role == UserRole.ORGANIZER && target.organizerVerificationStatus == OrganizerVerificationStatus.PENDING) { "La cuenta ya fue revisada" }
            require(identity.getString("photoPath") == expectedPhotoPath) { "El documento cambió; vuelve a abrir la revisión" }
            val next = target.copy(organizerVerificationStatus = if (approve) OrganizerVerificationStatus.VERIFIED else OrganizerVerificationStatus.REJECTED,
                accountStatus = if (approve) AccountStatus.ACTIVE else AccountStatus.SUSPENDED)
            transaction.update(userRef, mapOf("organizerVerificationStatus" to next.organizerVerificationStatus.name, "accountStatus" to next.accountStatus.name))
            next
        }.await()
        mutableUsers.value = users.value.filterNot { it.id == userId } + updated
        return updated
    }

    override suspend fun recoverPassword(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }

    override suspend fun save(user: User) {
        val firebaseUser = requireNotNull(auth.currentUser) { "Inicia sesión para guardar el perfil." }
        val reference = firestore.collection("users").document(user.id)
        if (firebaseUser.uid == user.id) {
            // El cliente no puede elevar su rol ni reactivar su propia cuenta al editar el perfil.
            reference.update(mapOf(
                "name" to user.name, "email" to (firebaseUser.email ?: user.email),
                "phoneNumber" to user.phoneNumber, "profilePictureUrl" to user.profilePictureUrl,
                "pendingEmail" to user.pendingEmail
            )).await()
        } else {
            require(users.value.any { it.id == firebaseUser.uid && it.role == UserRole.ADMINISTRATOR && it.accountStatus == AccountStatus.ACTIVE }) {
                "Solo un moderador puede administrar usuarios."
            }
            val changes = mutableMapOf<String, Any>("accountStatus" to user.accountStatus.name)
            if (user.role == UserRole.ORGANIZER) changes["organizerVerificationStatus"] = user.organizerVerificationStatus.name
            reference.update(changes).await()
        }
        mutableUsers.value = users.value.filterNot { it.id == user.id } + user
    }

    override suspend fun reauthenticate(userId: String, password: String) {
        val current = requireCurrentUser(userId)
        require(password.isNotBlank()) { "Ingresa tu contraseña actual para confirmar." }
        val email = requireNotNull(current.email) { "Esta cuenta no tiene un correo asociado." }
        current.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    override suspend fun requestEmailChange(userId: String, newEmail: String) {
        requireCurrentUser(userId).verifyBeforeUpdateEmail(newEmail.trim()).await()
    }

    override suspend fun refreshSession(): User? {
        val current = auth.currentUser ?: return null.also { bindSession(null) }
        bindSession(current)
        restoreJob?.cancel()
        mutableSyncState.value = RepositorySyncState(isLoading = true)
        try {
            return withTimeout(15_000) {
                current.reload().await()
                val refreshed = requireNotNull(auth.currentUser) { "La sesión ya no está disponible." }
                loadProfile(refreshed)
            }
        } catch (timeout: TimeoutCancellationException) {
            val message = "No se pudo actualizar la cuenta. Revisa tu conexión e intenta de nuevo."
            mutableSyncState.value = RepositorySyncState(error = message)
            mutableSessionReady.value = true
            throw IllegalStateException(message, timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableSyncState.value = RepositorySyncState(error = error.toFirebaseMessage())
            mutableSessionReady.value = true
            throw error
        }
    }

    override suspend fun delete(userId: String) {
        val current = requireCurrentUser(userId)
        val profile = users.value.firstOrNull { it.id == userId }
        val identityReference = firestore.collection("organizerVerifications").document(userId)
        val identity = identityReference.get(Source.SERVER).await().data
        deletingAccount = true
        try {
            firestore.batch().apply {
                delete(identityReference)
                delete(firestore.collection("users").document(userId))
            }.commit().await()
            try {
                current.delete().await()
            } catch (error: Exception) {
                // Auth y Firestore no comparten transacción: conserva el perfil para reintentar.
                if (profile != null) {
                    try {
                        firestore.batch().apply {
                            set(firestore.collection("users").document(userId), profile.toDocument())
                            if (identity != null) set(identityReference, identity)
                        }.commit().await()
                    } catch (_: Exception) { }
                }
                throw error
            }
            signOut()
        } finally { deletingAccount = false }
    }

    override fun signOut() {
        auth.signOut()
        bindSession(null)
    }

    private fun bindSession(firebaseUser: FirebaseUser?) {
        // Authentication avisa antes de que Firestore confirme si esta cuenta puede entrar.
        // El login publica y conecta la sesión únicamente después de validar su estado.
        if (signingIn && firebaseUser != null) return
        profilesListener?.remove()
        profilesListener = null
        restoreJob?.cancel()
        val generation = ++listenerGeneration
        mutableCurrentUserId.value = firebaseUser?.uid
        mutableUsers.value = emptyList()
        pendingProfiles = null
        if (firebaseUser == null) {
            mutableSyncState.value = RepositorySyncState()
            mutableSessionReady.value = true
            return
        }
        mutableSessionReady.value = false
        mutableSyncState.value = RepositorySyncState(isLoading = true)
        profilesListener = firestore.collection("users").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (generation != listenerGeneration || auth.currentUser?.uid != firebaseUser.uid) return@addSnapshotListener
            if (error != null) {
                mutableSyncState.value = RepositorySyncState(error = error.toFirebaseMessage())
                mutableSessionReady.value = true
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            // No navegar desde la escritura local antes de que el registro termine en el servidor.
            val profiles = snapshot.documents.mapNotNull { it.toUser() }
            if (creatingAccount) {
                pendingProfiles = profiles
                return@addSnapshotListener
            }
            if (deletingAccount) return@addSnapshotListener
            val own = profiles.firstOrNull { it.id == firebaseUser.uid }
            if (own != null) {
                mutableUsers.value = profiles
                if (!snapshot.metadata.isFromCache) {
                    mutableSyncState.value = RepositorySyncState()
                    mutableSessionReady.value = true
                }
            } else if (!creatingAccount && !deletingAccount && !snapshot.metadata.isFromCache) {
                mutableSyncState.value = RepositorySyncState(error = "No se encontró el perfil de esta cuenta. Reintenta o cierra sesión.")
                mutableSessionReady.value = true
            }
        }
        if (!creatingAccount) restoreJob = scope.launch {
            try { withTimeout(15_000) { loadProfile(firebaseUser) } }
            catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException && generation == listenerGeneration && auth.currentUser?.uid == firebaseUser.uid && !sessionReady.value) {
                    mutableSyncState.value = RepositorySyncState(error = "No se pudo cargar el perfil. Revisa tu conexión y reintenta.")
                    mutableSessionReady.value = true
                }
                throw cancelled
            } catch (error: Exception) {
                if (generation == listenerGeneration) {
                    mutableSyncState.value = RepositorySyncState(error = error.toFirebaseMessage())
                    mutableSessionReady.value = true
                }
            }
        }
    }

    private suspend fun loadProfile(firebaseUser: FirebaseUser, publish: Boolean = true): User {
        val reference = firestore.collection("users").document(firebaseUser.uid)
        var profile = requireNotNull(reference.get(Source.SERVER).await().toUser()) { "No se encontró el perfil de esta cuenta. Revisa la configuración o intenta de nuevo." }
        require(auth.currentUser?.uid == firebaseUser.uid) { "La sesión cambió. Inicia sesión de nuevo." }
        val authEmail = firebaseUser.email
        if (profile.accountStatus == AccountStatus.ACTIVE && authEmail != null && !profile.email.equals(authEmail, true)) {
            reference.update(mapOf("email" to authEmail, "pendingEmail" to null)).await()
            profile = profile.copy(email = authEmail, pendingEmail = null)
        }
        if (publish) publishProfile(profile)
        return profile
    }

    private fun publishProfile(user: User) {
        mutableUsers.value = users.value.filterNot { it.id == user.id } + user
        mutableCurrentUserId.value = user.id
        mutableSyncState.value = RepositorySyncState()
        mutableSessionReady.value = true
    }

    private fun requireCurrentUser(userId: String): FirebaseUser = requireNotNull(auth.currentUser?.takeIf { it.uid == userId }) {
        "Solo puedes modificar o eliminar tu propia cuenta."
    }

    private fun User.toDocument(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "email" to email, "role" to role.name,
        "registrationDate" to Timestamp(registrationDate), "profilePictureUrl" to profilePictureUrl,
        "phoneNumber" to phoneNumber, "accountStatus" to accountStatus.name, "pendingEmail" to pendingEmail,
        "organizerVerificationStatus" to organizerVerificationStatus.name
    )

    private fun DocumentSnapshot.toUser(): User? {
        if (!exists()) return null
        val name = getString("name") ?: return null
        val email = getString("email") ?: return null
        return User(id, name, email,
            UserRole.entries.firstOrNull { it.name == getString("role") } ?: UserRole.PARTICIPANT,
            getTimestamp("registrationDate")?.toDate() ?: Date(0), getString("profilePictureUrl"),
            getString("phoneNumber"),
            AccountStatus.entries.firstOrNull { it.name == getString("accountStatus") } ?: AccountStatus.SUSPENDED,
            getString("pendingEmail"),
            OrganizerVerificationStatus.entries.firstOrNull { it.name == getString("organizerVerificationStatus") }
                ?: if (getString("role") == UserRole.ORGANIZER.name) OrganizerVerificationStatus.PENDING
                else OrganizerVerificationStatus.NOT_REQUIRED)
    }
}
