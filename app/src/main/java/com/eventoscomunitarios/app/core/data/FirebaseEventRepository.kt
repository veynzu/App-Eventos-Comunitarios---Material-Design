package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.core.util.toFirebaseMessage
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.repository.EventRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Las inscripciones pertenecen al documento del evento: cupo y cola cambian atómicamente. */
@Singleton
class FirebaseEventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : EventRepository {
    private val collection = firestore.collection("events")
    private val mutableEvents = MutableStateFlow<List<Event>>(emptyList())
    private val mutableRegistrations = MutableStateFlow<List<EventRegistration>>(emptyList())
    private val mutableSync = MutableStateFlow(RepositorySyncState())
    override val events = mutableEvents.asStateFlow()
    override val registrations = mutableRegistrations.asStateFlow()
    override val syncState = mutableSync.asStateFlow()
    private var listener: ListenerRegistration? = null
    private var listeningUserId: String? = null
    private var cleanupError: String? = null
    private val deletedVersions = mutableMapOf<Int, Long>()
    override fun reportCleanupError(message: String) { cleanupError = message; mutableSync.value = RepositorySyncState(error = message) }

    init { auth.addAuthStateListener { connect(it.currentUser?.uid) } }

    @Synchronized
    private fun connect(userId: String?) {
        if (listeningUserId == userId && (userId == null || listener != null)) return
        listener?.remove()
        listener = null
        listeningUserId = userId
        cleanupError = null
        deletedVersions.clear()
        mutableEvents.value = emptyList()
        mutableRegistrations.value = emptyList()
        mutableSync.value = RepositorySyncState(isLoading = userId != null)
        if (userId == null) return
        listener = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (auth.currentUser?.uid != userId || listeningUserId != userId) return@addSnapshotListener
            if (error != null) {
                mutableSync.value = RepositorySyncState(error = error.toFirebaseMessage())
                return@addSnapshotListener
            }
            // Aplicar cambios incrementales conserva una escritura confirmada si un snapshot anterior
            // llega tarde. revision evita que una modificación antigua revierta un conteo más reciente.
            snapshot?.documentChanges.orEmpty().forEach { change ->
                val record = change.document.toEventRecord() ?: return@forEach
                if (change.type == DocumentChange.Type.REMOVED || record.event.isDeleted) {
                    removeFromCache(record.event.id, if (change.type == DocumentChange.Type.REMOVED) Long.MAX_VALUE else record.event.revision)
                } else updateCache(record)
            }
            mutableSync.value = RepositorySyncState(
                isLoading = snapshot?.metadata?.isFromCache == true && events.value.isEmpty(), error = cleanupError)
        }
    }

    override suspend fun nextId(): Int {
        requireNotNull(auth.currentUser) { "Inicia sesión para publicar" }
        val reference = firestore.collection("metadata").document("counters")
        return firestore.runTransaction { transaction ->
            val next = (transaction.get(reference).getLong("events") ?: 0L) + 1L
            require(next <= Int.MAX_VALUE) { "No se pueden crear más identificadores de eventos" }
            transaction.set(reference, mapOf("events" to next), SetOptions.merge())
            next.toInt()
        }.await()
    }

    override suspend fun create(actor: User, event: Event): Event {
        requireActor(actor.id)
        val reference = collection.document(event.id.toString())
        val created = firestore.runTransaction { transaction ->
            val profile = transaction.get(firestore.collection("users").document(actor.id))
            val existing = transaction.get(reference)
            require(profile.exists() && profile.getString("accountStatus") == AccountStatus.ACTIVE.name) { "La cuenta no está disponible" }
            require(!existing.exists()) { "El identificador del evento ya existe; vuelve a intentarlo" }
            val current = event.copy(organizerId = actor.id, organizerName = profile.getString("name") ?: actor.name,
                submittedByName = profile.getString("name") ?: actor.name, attendeeCount = 0, revision = 1)
            transaction.set(reference, EventRecord(current, emptyList()).toDocument())
            current
        }.await()
        if (auth.currentUser?.uid == actor.id) updateCache(EventRecord(created, emptyList()))
        return created
    }

    override suspend fun mutate(
        eventId: Int,
        actor: User,
        activeUserIds: Set<String>,
        transform: (EventRecord, User, Set<String>) -> EventRecord?
    ): EventChange {
        requireActor(actor.id)
        val reference = collection.document(eventId.toString())
        val change = firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val before = requireNotNull(snapshot.toEventRecord()) { "Publicación no encontrada" }
            // Todas las lecturas ocurren antes de escribir y se repiten si otro dispositivo modifica el cupo.
            val userIds = before.registrations.filter { it.status == EventRegistrationStatus.WAITLIST }
                .map { it.userId }.toSet() + actor.id
            val profiles = userIds.associateWith { transaction.get(firestore.collection("users").document(it)) }
            val profile = requireNotNull(profiles[actor.id])
            require(profile.exists() && profile.getString("accountStatus") == AccountStatus.ACTIVE.name) { "La cuenta no está disponible" }
            val freshActor = actor.copy(
                name = profile.getString("name") ?: actor.name,
                role = enumValueOrNull<UserRole>(profile.getString("role")) ?: UserRole.PARTICIPANT,
                accountStatus = AccountStatus.ACTIVE
            )
            val activeIds = profiles.filterValues { it.exists() && it.getString("accountStatus") == AccountStatus.ACTIVE.name }.keys
            val after = transform(before, freshActor, activeIds)?.let {
                it.copy(event = it.event.copy(revision = before.event.revision + 1))
            }
            if (after == null) {
                // La marca impide interacciones nuevas mientras se limpia la información relacionada.
                transaction.set(reference, EventRecord(before.event.copy(isDeleted = true, attendeeCount = 0, revision = before.event.revision + 1), emptyList()).toDocument())
            } else {
                require(after.event.id == eventId) { "El identificador no se puede cambiar" }
                transaction.set(reference, after.toDocument())
            }
            EventChange(before, after)
        }.await()
        if (auth.currentUser?.uid == actor.id) {
            if (change.after == null) removeFromCache(eventId, change.before.event.revision + 1) else updateCache(change.after)
        }
        return change
    }

    override suspend fun purge(eventId: Int, actor: User) {
        requireActor(actor.id)
        val reference = collection.document(eventId.toString())
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val profile = transaction.get(firestore.collection("users").document(actor.id))
            if (!snapshot.exists()) return@runTransaction
            require(profile.getString("accountStatus") == AccountStatus.ACTIVE.name) { "La cuenta no está disponible" }
            require(snapshot.getString("organizerId") == actor.id || profile.getString("role") == UserRole.ADMINISTRATOR.name) { "No puedes eliminar esta publicación" }
            require(snapshot.getBoolean("isDeleted") == true) { "La publicación todavía no fue retirada" }
            transaction.delete(reference)
        }.await()
        if (auth.currentUser?.uid == actor.id) removeFromCache(eventId)
    }

    override suspend fun ownedBy(userId: String): List<Event> = collection.whereEqualTo("organizerId", userId)
        .get(Source.SERVER).await().documents.mapNotNull { it.toEventRecord()?.event }

    override suspend fun registeredBy(userId: String): List<Event> = collection.whereArrayContains("registrationUserIds", userId)
        .get(Source.SERVER).await().documents.mapNotNull { it.toEventRecord()?.event }

    private fun requireActor(userId: String) {
        require(auth.currentUser?.uid == userId) { "La sesión cambió. Vuelve a ingresar" }
    }
    @Synchronized private fun updateCache(record: EventRecord) {
        val id = record.event.id
        if ((deletedVersions[id] ?: -1) >= record.event.revision) return
        if ((events.value.firstOrNull { it.id == id }?.revision ?: -1) > record.event.revision) return
        mutableEvents.value = (events.value.filterNot { it.id == id } + record.event).filterNot { it.isDeleted }.sortedBy { it.id }
        mutableRegistrations.value = (registrations.value.filterNot { it.eventId == id } + record.registrations)
            .sortedWith(compareBy<EventRegistration> { it.queueOrder }.thenBy { it.registeredAt })
    }
    @Synchronized private fun removeFromCache(eventId: Int, revision: Long = Long.MAX_VALUE) {
        if ((events.value.firstOrNull { it.id == eventId }?.revision ?: -1) > revision) return
        deletedVersions[eventId] = maxOf(deletedVersions[eventId] ?: -1, revision)
        mutableEvents.value = events.value.filterNot { it.id == eventId }
        mutableRegistrations.value = registrations.value.filterNot { it.eventId == eventId }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }

private fun DocumentSnapshot.toEventRecord(): EventRecord? {
    if (!exists()) return null
    val eventId = getLong("id")?.toInt() ?: id.toIntOrNull() ?: return null
    val category = enumValueOrNull<EventCategory>(getString("category")) ?: return null
    val status = enumValueOrNull<EventModerationStatus>(getString("moderationStatus")) ?: EventModerationStatus.PENDING
    val entries = (get("registrations") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
        val uid = key as? String ?: return@mapNotNull null
        val data = value as? Map<*, *> ?: return@mapNotNull null
        val registrationStatus = enumValueOrNull<EventRegistrationStatus>(data["status"] as? String) ?: return@mapNotNull null
        EventRegistration(uid, eventId, registrationStatus, (data["registeredAt"] as? Number)?.toLong() ?: 0,
            (data["queueOrder"] as? Number)?.toLong() ?: 0)
    }.sortedWith(compareBy<EventRegistration> { it.queueOrder }.thenBy { it.registeredAt })
    val event = Event(
        id = eventId, title = getString("title").orEmpty(), startsAt = getLong("startsAt") ?: 0,
        endsAt = getLong("endsAt") ?: 0, location = getString("location").orEmpty(),
        distanceKm = getDouble("distanceKm") ?: 0.0, price = (getLong("price") ?: 0).toInt(), category = category,
        attendeeCount = entries.count { it.status == EventRegistrationStatus.CONFIRMED }, description = getString("description").orEmpty(),
        capacity = (getLong("capacity") ?: 0).toInt(), organizerId = getString("organizerId").orEmpty(),
        organizerName = getString("organizerName").orEmpty(), photoUri = getString("photoUri"), moderationStatus = status,
        submittedAtLabel = getString("submittedAtLabel").orEmpty(), submittedByName = getString("submittedByName").orEmpty(),
        latitude = getDouble("latitude") ?: 4.5339, longitude = getDouble("longitude") ?: -75.6811,
        isFinished = getBoolean("isFinished") ?: false, rejectionReason = getString("rejectionReason"), isDeleted = getBoolean("isDeleted") ?: false, revision = getLong("revision") ?: 0
    )
    return EventRecord(event, entries)
}

private fun EventRecord.toDocument(): Map<String, Any?> = with(event) {
    mapOf(
        "id" to id, "title" to title, "startsAt" to startsAt, "endsAt" to endsAt, "location" to location,
        "distanceKm" to distanceKm, "price" to price, "category" to category.name,
        "attendeeCount" to registrations.count { it.status == EventRegistrationStatus.CONFIRMED },
        "description" to description, "capacity" to capacity, "organizerId" to organizerId,
        "organizerName" to organizerName, "photoUri" to photoUri, "moderationStatus" to moderationStatus.name,
        "submittedAtLabel" to submittedAtLabel, "submittedByName" to submittedByName,
        "latitude" to latitude, "longitude" to longitude, "isFinished" to isFinished,
        "rejectionReason" to rejectionReason, "isDeleted" to isDeleted, "revision" to revision,
        "registrationUserIds" to registrations.map { it.userId },
        "registrations" to registrations.associate { it.userId to mapOf("status" to it.status.name, "registeredAt" to it.registeredAt, "queueOrder" to it.queueOrder) }
    )
}
