package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.CommunityRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Datos sociales remotos. Cada sesión escucha sus avisos y elimina la caché al salir. */
@Singleton
class FirebaseCommunityRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CommunityRepository {
    private val mutableComments = MutableStateFlow<List<EventComment>>(emptyList())
    private val mutableVotes = MutableStateFlow<List<EventVote>>(emptyList())
    private val mutableNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val mutableFavorites = MutableStateFlow<List<EventFavorite>>(emptyList())
    private val mutableFollowing = MutableStateFlow<List<UserFollow>>(emptyList())
    private val mutableSync = MutableStateFlow(RepositorySyncState(isLoading = auth.currentUser != null))
    override val comments = mutableComments.asStateFlow()
    override val votes = mutableVotes.asStateFlow()
    override val notifications = mutableNotifications.asStateFlow()
    override val favorites = mutableFavorites.asStateFlow()
    override val following = mutableFollowing.asStateFlow()
    override val syncState = mutableSync.asStateFlow()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val pending = mutableSetOf<String>()
    private val errors = mutableMapOf<String, String>()
    private var subscribedUserId: String? = null
    private var generation = 0L

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != subscribedUserId) subscribe(uid)
        }
    }

    private fun subscribe(userId: String?) {
        generation++
        subscribedUserId = userId
        listeners.forEach { it.remove() }
        listeners.clear()
        pending.clear()
        errors.clear()
        mutableComments.value = emptyList()
        mutableVotes.value = emptyList()
        mutableNotifications.value = emptyList()
        mutableFavorites.value = emptyList()
        mutableFollowing.value = emptyList()
        if (userId == null) {
            mutableSync.value = RepositorySyncState()
            return
        }
        pending.addAll(listOf("comments", "votes", "notifications", "favorites", "following"))
        mutableSync.value = RepositorySyncState(isLoading = true)
        val currentGeneration = generation
        listen("comments", firestore.collection("comments"), currentGeneration, mutableComments, ::readComment)
        listen("votes", firestore.collection("votes"), currentGeneration, mutableVotes, ::readVote)
        listen("notifications", firestore.collection("notifications").whereEqualTo("userId", userId), currentGeneration, mutableNotifications, ::readNotification)
        listen("favorites", firestore.collection("favorites"), currentGeneration, mutableFavorites, ::readFavorite)
        listen("following", firestore.collection("following"), currentGeneration, mutableFollowing, ::readFollow)
    }

    private fun <T> listen(key: String, query: Query, expectedGeneration: Long, target: MutableStateFlow<List<T>>, map: (DocumentSnapshot) -> T?) {
        listeners += query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
            if (generation != expectedGeneration || auth.currentUser?.uid != subscribedUserId) return@addSnapshotListener
            if (failure != null) {
                pending.remove(key)
                errors[key] = failure.toFirebaseMessage()
            } else if (snapshot != null) {
                if (!snapshot.metadata.isFromCache || !snapshot.isEmpty) pending.remove(key)
                errors.remove(key)
                target.value = snapshot.documents.mapNotNull(map)
            }
            mutableSync.value = RepositorySyncState(isLoading = pending.isNotEmpty(), error = errors.values.firstOrNull())
        }
    }

    override suspend fun nextCommentId(): Int {
        requireSignedIn()
        return firestore.runTransaction { transaction -> nextId(transaction, "comments") }.await()
    }

    override suspend fun saveComment(comment: EventComment) {
        val uid = requireActor(comment.userId)
        val reference = firestore.collection("comments").document(comment.id.toString())
        firestore.runTransaction { transaction ->
            val actor = transaction.get(firestore.collection("users").document(uid))
            val event = transaction.get(firestore.collection("events").document(comment.eventId.toString()))
            val existing = transaction.get(reference)
            requireActive(actor)
            require(event.exists() && event.getBoolean("isDeleted") != true) { "La publicación ya no está disponible" }
            if (!existing.exists()) require(event.getString("moderationStatus") == EventModerationStatus.APPROVED.name) { "La publicación todavía no está aprobada" }
            else require(existing.getString("userId") == uid && existing.int("eventId") == comment.eventId) { "No puedes modificar este comentario" }
            require(comment.text.isNotBlank() && comment.text.trim().length <= 1000) { "El comentario debe tener entre 1 y 1000 caracteres" }
            transaction.set(reference, mapOf(
                "id" to comment.id, "eventId" to comment.eventId, "userId" to uid,
                "authorName" to (actor.getString("name") ?: comment.authorName), "text" to comment.text.trim(), "createdAt" to comment.createdAt
            ))
        }.await()
    }

    override suspend fun deleteComment(commentId: Int) {
        val uid = requireSignedIn()
        val reference = firestore.collection("comments").document(commentId.toString())
        firestore.runTransaction { transaction ->
            val actor = transaction.get(firestore.collection("users").document(uid))
            val comment = transaction.get(reference)
            requireActive(actor)
            require(comment.exists()) { "Comentario no encontrado" }
            require(comment.getString("userId") == uid || actor.getString("role") == UserRole.ADMINISTRATOR.name) { "Solo puedes eliminar tus comentarios" }
            transaction.delete(reference)
        }.await()
    }

    override suspend fun toggleVote(userId: String, eventId: Int, value: Int) {
        val uid = requireActor(userId)
        require(value == 1 || value == -1) { "El voto debe ser positivo o negativo" }
        val reference = firestore.collection("votes").document(pairId(uid, eventId.toString()))
        firestore.runTransaction { transaction ->
            val actor = transaction.get(firestore.collection("users").document(uid))
            val event = transaction.get(firestore.collection("events").document(eventId.toString()))
            val previous = transaction.get(reference)
            requireActive(actor)
            requirePublished(event)
            require(event.getString("organizerId") != uid) { "No puedes votar tu propia publicación" }
            val nextValue = value.takeUnless { previous.int("value") == value }
            if (nextValue == null) transaction.delete(reference)
            else transaction.set(reference, mapOf("userId" to uid, "eventId" to eventId, "value" to nextValue))
            nextValue
        }.await()
    }

    override suspend fun toggleFavorite(userId: String, eventId: Int) {
        val uid = requireActor(userId)
        val reference = firestore.collection("favorites").document(pairId(uid, eventId.toString()))
        firestore.runTransaction { transaction ->
            val actor = transaction.get(firestore.collection("users").document(uid))
            val event = transaction.get(firestore.collection("events").document(eventId.toString()))
            val previous = transaction.get(reference)
            requireActive(actor)
            requirePublished(event)
            if (previous.exists()) transaction.delete(reference)
            else transaction.set(reference, mapOf("userId" to uid, "eventId" to eventId))
            !previous.exists()
        }.await()
    }

    override suspend fun toggleFollow(userId: String, organizerId: String) {
        val uid = requireActor(userId)
        require(uid != organizerId) { "No puedes seguirte a ti mismo" }
        val reference = firestore.collection("following").document(pairId(uid, organizerId))
        firestore.runTransaction { transaction ->
            val actor = transaction.get(firestore.collection("users").document(uid))
            val organizer = transaction.get(firestore.collection("users").document(organizerId))
            val previous = transaction.get(reference)
            requireActive(actor)
            requireActive(organizer)
            if (previous.exists()) transaction.delete(reference)
            else transaction.set(reference, mapOf("userId" to uid, "organizerId" to organizerId))
            !previous.exists()
        }.await()
    }

    override suspend fun notify(userId: String, title: String, message: String, eventId: Int?) {
        requireSignedIn()
        val createdAt = System.currentTimeMillis()
        firestore.runTransaction { transaction ->
            val recipient = transaction.get(firestore.collection("users").document(userId))
            val event = eventId?.let { transaction.get(firestore.collection("events").document(it.toString())) }
            require(recipient.exists()) { "No se encontró el destinatario del aviso" }
            // La lectura participa en la transacción: si el evento se retira mientras se notifica,
            // el reintento conserva el aviso histórico sin reintroducir un enlace eliminado.
            val linkedEventId = eventId.takeIf { event?.exists() == true && event.getBoolean("isDeleted") != true }
            val id = nextId(transaction, "notifications")
            val notification = AppNotification(id, userId, title, message, linkedEventId, createdAt)
            transaction.set(firestore.collection("notifications").document(id.toString()), mapOf(
                "id" to id, "userId" to userId, "title" to title, "message" to message,
                "eventId" to linkedEventId, "createdAt" to createdAt, "isRead" to false
            ))
            notification
        }.await()
    }

    override suspend fun markRead(userId: String, notificationId: Int?) {
        requireActor(userId)
        if (notificationId != null) {
            val reference = firestore.collection("notifications").document(notificationId.toString())
            firestore.runTransaction { transaction ->
                val notification = transaction.get(reference)
                require(notification.exists() && notification.getString("userId") == userId) { "Notificación no encontrada" }
                transaction.update(reference, "isRead", true)
            }.await()
        } else {
            val unread = firestore.collection("notifications").whereEqualTo("userId", userId).get(Source.SERVER).await().documents
                .filter { it.getBoolean("isRead") != true }
            unread.chunked(400).forEach { documents ->
                requireActor(userId)
                val batch = firestore.batch()
                documents.forEach { batch.update(it.reference, "isRead", true) }
                batch.commit().await()
            }
        }
    }

    override suspend fun removeForEvent(eventId: Int) {
        requireSignedIn()
        // Se consulta el servidor, incluso si las listas locales no terminaron de sincronizarse.
        for (collection in listOf("comments", "votes", "favorites")) {
            deleteQuery(firestore.collection(collection).whereEqualTo("eventId", eventId))
        }
        val notices = firestore.collection("notifications").whereEqualTo("eventId", eventId).get(Source.SERVER).await().documents
        notices.chunked(400).forEach { documents ->
            requireSignedIn()
            val batch = firestore.batch()
            documents.forEach { batch.update(it.reference, "eventId", null) }
            batch.commit().await()
        }
    }

    override suspend fun removeForUser(userId: String) {
        requireActor(userId)
        for (collection in listOf("comments", "votes", "favorites", "notifications", "following")) {
            deleteQuery(firestore.collection(collection).whereEqualTo("userId", userId))
        }
        deleteQuery(firestore.collection("following").whereEqualTo("organizerId", userId))
    }

    private suspend fun deleteQuery(query: Query) {
        val documents = query.get(Source.SERVER).await().documents
        documents.chunked(400).forEach { chunk ->
            requireSignedIn()
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun nextId(transaction: Transaction, field: String): Int {
        val reference = firestore.collection("metadata").document("counters")
        val current = transaction.get(reference).getLong(field) ?: 0L
        require(current < Int.MAX_VALUE) { "Se alcanzó el límite de identificadores" }
        val next = current + 1L
        transaction.set(reference, mapOf(field to next), SetOptions.merge())
        return next.toInt()
    }

    private fun requireSignedIn(): String = requireNotNull(auth.currentUser?.uid) { "Inicia sesión para continuar" }
    private fun requireActor(userId: String): String = requireSignedIn().also {
        require(it == userId) { "La sesión cambió. Vuelve a abrir esta pantalla" }
    }
    private fun requireActive(user: DocumentSnapshot) {
        require(user.exists() && user.getString("accountStatus") == AccountStatus.ACTIVE.name) { "La cuenta no está disponible" }
    }
    private fun requirePublished(event: DocumentSnapshot) {
        require(event.exists() && event.getBoolean("isDeleted") != true) { "La publicación ya no está disponible" }
        require(event.getString("moderationStatus") == EventModerationStatus.APPROVED.name) { "La publicación todavía no está aprobada" }
    }
    private fun pairId(first: String, second: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${first.length}:$first${second.length}:$second".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun DocumentSnapshot.int(field: String): Int? = (get(field) as? Number)?.toLong()
        ?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
    private fun DocumentSnapshot.timestamp(field: String): Long = when (val value = get(field)) {
        is Number -> value.toLong()
        is Timestamp -> value.toDate().time
        else -> 0L
    }
    private fun readComment(document: DocumentSnapshot): EventComment? {
        val id = document.int("id") ?: document.id.toIntOrNull() ?: return null
        return EventComment(id, document.int("eventId") ?: return null, document.getString("userId") ?: return null,
            document.getString("authorName").orEmpty(), document.getString("text").orEmpty(), document.timestamp("createdAt"))
    }
    private fun readVote(document: DocumentSnapshot): EventVote? {
        return EventVote(document.int("eventId") ?: return null, document.getString("userId") ?: return null,
            document.int("value")?.takeIf { it == 1 || it == -1 } ?: return null)
    }
    private fun readFavorite(document: DocumentSnapshot): EventFavorite? {
        return EventFavorite(document.getString("userId") ?: return null, document.int("eventId") ?: return null)
    }
    private fun readFollow(document: DocumentSnapshot): UserFollow? {
        return UserFollow(document.getString("userId") ?: return null, document.getString("organizerId") ?: return null)
    }
    private fun readNotification(document: DocumentSnapshot): AppNotification? {
        val id = document.int("id") ?: document.id.toIntOrNull() ?: return null
        return AppNotification(id, document.getString("userId") ?: return null, document.getString("title").orEmpty(),
            document.getString("message").orEmpty(), document.int("eventId"), document.timestamp("createdAt"), document.getBoolean("isRead") == true)
    }
}
