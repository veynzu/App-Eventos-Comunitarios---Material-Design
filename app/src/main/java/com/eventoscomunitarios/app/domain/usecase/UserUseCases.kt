package com.eventoscomunitarios.app.domain.usecase

import com.eventoscomunitarios.app.core.util.firebaseResult
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.eventoscomunitarios.app.domain.repository.MediaRepository
import com.eventoscomunitarios.app.domain.repository.UserRepository
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class UserUseCases @Inject constructor(
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val communityRepository: CommunityRepository,
    private val eventUseCases: EventUseCases,
    private val mediaRepository: MediaRepository? = null
) {
    val users = userRepository.users
    val syncState = userRepository.syncState
    val currentUserId = userRepository.currentUserId
    val sessionReady = userRepository.sessionReady
    fun findUser(id: String): User? = users.value.firstOrNull { it.id == id }
    fun signOut() = userRepository.signOut()
    suspend fun refreshSession(): Result<User?> = firebaseResult { userRepository.refreshSession() }

    suspend fun login(email: String, password: String): Result<User> = firebaseResult {
        val user = userRepository.login(email.trim(), password)
        try { user.requireActiveAccount() }
        catch (error: IllegalStateException) { userRepository.signOut(); throw error }
        user
    }

    suspend fun register(name: String, email: String, password: String, phoneNumber: String?, role: UserRole, identityPhotoUri: String? = null): Result<User> = firebaseResult {
        validateProfile(name, email, phoneNumber)
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres" }
        require(role != UserRole.ADMINISTRATOR) { "No es posible registrar moderadores desde este formulario" }
        val draft = User(UUID.randomUUID().toString(), name.trim(), email.trim().lowercase(), role, Date(),
            phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotBlank() })
        require(role != UserRole.ORGANIZER || identityPhotoUri?.startsWith("content://") == true) {
            "Adjunta una foto de tu cédula para solicitar la verificación"
        }
        val created = userRepository.register(draft, password, identityPhotoUri)
        // La cuenta ya fue creada; un fallo de un aviso secundario no debe presentarla como fallida.
        try { communityRepository.notify(created.id, "Bienvenido a Share You", "Publica, comenta y participa en los eventos de tu comunidad.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        created
    }

    suspend fun recoverPassword(email: String): Result<Unit> = firebaseResult {
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())) { "Ingresa un correo válido" }
        userRepository.recoverPassword(email.trim())
    }

    suspend fun updateProfile(
        userId: String, name: String, email: String, phoneNumber: String?,
        photoUri: String? = null, password: String = "", removePhoto: Boolean = false
    ): Result<User> = firebaseResult {
        val user = requireActiveUser(userId)
        validateProfile(name, email, phoneNumber)
        val changeEmail = !email.trim().equals(user.email, true)
        require(users.value.none { it.id != userId && it.email.equals(email.trim(), true) }) { "Ya existe una cuenta con este correo" }
        if (changeEmail) {
            userRepository.reauthenticate(userId, password)
            userRepository.requestEmailChange(userId, email.trim())
        }
        var uploadedUrl: String? = null
        val photo = when {
            removePhoto -> null
            photoUri == null || photoUri == user.profilePictureUrl -> user.profilePictureUrl
            photoUri.startsWith("content://") -> {
                uploadedUrl = requireNotNull(mediaRepository) { "El almacenamiento de imágenes no está disponible" }
                    .uploadImage(userId, photoUri, "profiles")
                uploadedUrl
            }
            else -> error("Selecciona una imagen desde el dispositivo")
        }
        val updated = user.copy(name = name.trim(), phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotBlank() },
            profilePictureUrl = photo, pendingEmail = if (changeEmail) email.trim().lowercase() else user.pendingEmail)
        try { userRepository.save(updated) }
        catch (error: Exception) {
            uploadedUrl?.let { try { mediaRepository?.deleteImage(it) } catch (_: Exception) { } }
            throw error
        }
        // El perfil es la fuente del nombre: no se reescriben publicaciones o comentarios ajenos.
        if (user.profilePictureUrl != null && user.profilePictureUrl != photo) {
            try { mediaRepository?.deleteImage(user.profilePictureUrl) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
        updated
    }

    suspend fun deleteAccount(userId: String, password: String = ""): Result<Unit> = firebaseResult {
        val user = requireActiveUser(userId)
        // Verificar identidad antes de borrar publicaciones, datos o imágenes.
        userRepository.reauthenticate(userId, password)
        eventUseCases.removeAccountData(userId)
        communityRepository.removeForUser(userId)
        user.profilePictureUrl?.let { mediaRepository?.deleteImage(it) }
        if (user.role == UserRole.ORGANIZER) {
            userRepository.organizerIdentityPath(userId)?.let { mediaRepository?.deleteImage(it) }
        }
        userRepository.delete(userId)
    }

    suspend fun updateAccountStatus(actorId: String, userId: String, status: AccountStatus): Result<User> = firebaseResult {
        require(requireActiveUser(actorId).role == UserRole.ADMINISTRATOR) { "Solo un moderador puede administrar usuarios" }
        val user = requireNotNull(findUser(userId)) { "Usuario no encontrado" }
        require(user.role != UserRole.ADMINISTRATOR) { "No se puede suspender a un moderador" }
        val updated = user.copy(accountStatus = status,
            organizerVerificationStatus = if (status == AccountStatus.ACTIVE && user.organizerVerificationStatus == OrganizerVerificationStatus.REJECTED)
                OrganizerVerificationStatus.PENDING else user.organizerVerificationStatus)
        userRepository.save(updated)
        try { communityRepository.notify(userId, "Estado de cuenta actualizado",
            if (status == AccountStatus.ACTIVE) "Tu cuenta está activa." else "Un moderador suspendió tu cuenta.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        updated
    }

    suspend fun submitOrganizerIdentity(userId: String, photoUri: String): Result<User> = firebaseResult {
        val user = requireActiveUser(userId)
        require(user.role == UserRole.ORGANIZER && user.organizerVerificationStatus != OrganizerVerificationStatus.VERIFIED) { "Esta cuenta no necesita enviar documentos" }
        require(photoUri.startsWith("content://")) { "Selecciona una foto de tu cédula" }
        val media = requireNotNull(mediaRepository) { "El almacenamiento de imágenes no está disponible" }
        val previous = userRepository.organizerIdentityPath(userId)
        val uploaded = media.uploadImage(userId, photoUri, "identities")
        val updated = try { userRepository.saveOrganizerIdentity(userId, uploaded) }
        catch (error: Exception) { try { media.deleteImage(uploaded) } catch (_: Exception) { }; throw error }
        if (previous != null && previous != uploaded) try { media.deleteImage(previous) } catch (_: Exception) { }
        updated
    }

    suspend fun loadOrganizerIdentity(actorId: String, userId: String): Result<OrganizerIdentity> = firebaseResult {
        require(requireActiveUser(actorId).role == UserRole.ADMINISTRATOR) { "Solo un administrador puede revisar documentos" }
        require(findUser(userId)?.role == UserRole.ORGANIZER) { "La cuenta no es de un organizador" }
        val path = requireNotNull(userRepository.organizerIdentityPath(userId)) { "El organizador aún no ha enviado su cédula" }
        OrganizerIdentity(path, requireNotNull(mediaRepository).readPrivateImage(path))
    }

    suspend fun reviewOrganizerIdentity(actorId: String, userId: String, expectedPhotoPath: String, approve: Boolean): Result<User> = firebaseResult {
        require(requireActiveUser(actorId).role == UserRole.ADMINISTRATOR) { "Solo un administrador puede verificar organizadores" }
        val updated = userRepository.reviewOrganizerIdentity(userId, expectedPhotoPath, approve)
        try { communityRepository.notify(userId, "Verificación de identidad",
            if (approve) "Tu identidad fue verificada. Ya puedes crear eventos." else "Tu cuenta fue bloqueada después de revisar el documento de identidad.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        updated
    }

    fun statistics(userId: String): UserStatistics {
        val publications = eventRepository.events.value.filter { it.organizerId == userId }
        val publishedIds = publications.filter { it.moderationStatus == EventModerationStatus.APPROVED }.map { it.id }.toSet()
        val receivedVotes = communityRepository.votes.value.filter { it.eventId in publishedIds }.sumOf { it.value }
        val completed = publications.count { it.isFinished && it.moderationStatus == EventModerationStatus.APPROVED }
        val registrations = eventRepository.registrations.value.filter { it.userId == userId }
        return UserStatistics(publications.size, publishedIds.size, completed,
            registrations.count { it.status == EventRegistrationStatus.CONFIRMED },
            registrations.count { it.status == EventRegistrationStatus.WAITLIST },
            communityRepository.comments.value.count { it.userId == userId }, receivedVotes,
            (receivedVotes + completed * 5).coerceAtLeast(0))
    }

    private fun requireActiveUser(userId: String): User = requireNotNull(findUser(userId)?.takeIf { it.accountStatus == AccountStatus.ACTIVE }) { "La cuenta no está disponible" }
    private fun validateProfile(name: String, email: String, phone: String?) {
        require(name.trim().length >= 3) { "El nombre debe tener al menos 3 caracteres" }
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())) { "Ingresa un correo válido" }
        require(phone.isNullOrBlank() || (phone.count(Char::isDigit) in 7..15 &&
            Regex("^\\+?\\d[\\d -]*\\d$").matches(phone.trim()))) { "Ingresa un teléfono válido de 7 a 15 dígitos" }
    }
}
