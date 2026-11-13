package com.eventoscomunitarios.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventoscomunitarios.app.core.data.*
import com.eventoscomunitarios.app.domain.model.*
import com.eventoscomunitarios.app.domain.usecase.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Se ejecuta únicamente contra demo-share-you en Firebase Emulator Suite local. */
@RunWith(AndroidJUnit4::class)
class FirebaseIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clients = mutableListOf<Client>()
    private val images = mutableListOf<Uri>()
    private val host = "10.0.2.2"

    @Before fun requireLocalEmulators() {
        assumeTrue("Inicia scripts/start-firebase-emulators.ps1 y pasa firebaseEmulators=true",
            InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val connection = URL("http://$host:9099/").openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        try { assertTrue("Auth Emulator no está disponible", connection.responseCode < 500) }
        finally { connection.disconnect() }
    }

    @After fun closeClients() = runBlocking {
        images.forEach { context.contentResolver.delete(it, null, null) }
        clients.forEach { client ->
            client.auth.signOut()
            delay(50)
            client.db.terminate().await()
            client.app.delete()
        }
    }

    @Test fun authPersistsProfilesRecoversPasswordAndDeletesAccount() = runBlocking {
        val first = client()
        val email = "auth-${UUID.randomUUID()}@example.test"
        val user = first.users.register("Usuario Firebase", email, PASSWORD, "3101234567", UserRole.PARTICIPANT).getOrThrow()
        assertEquals(first.auth.currentUser!!.uid, user.id)
        val stored = first.db.collection("users").document(user.id).get(Source.SERVER).await()
        assertEquals(user.name, stored.getString("name"))
        assertFalse(stored.contains("password"))
        assertTrue(first.users.recoverPassword(email).isSuccess)
        first.userRepository.signOut()
        waitUntil { first.userRepository.currentUserId.value == null && first.userRepository.users.value.isEmpty() }
        assertTrue(first.users.login(email, "incorrecta").isFailure)
        val second = client()
        assertEquals(user.id, second.users.login(email, PASSWORD).getOrThrow().id)
        second.users.updateProfile(user.id, "Nombre actualizado", email, null).getOrThrow()
        assertEquals("Nombre actualizado", second.db.collection("users").document(user.id).get(Source.SERVER).await().getString("name"))
        assertTrue(second.users.deleteAccount(user.id, "incorrecta").isFailure)
        assertTrue(second.db.collection("users").document(user.id).get(Source.SERVER).await().exists())
        second.users.deleteAccount(user.id, PASSWORD).getOrThrow()
        assertNull(second.auth.currentUser)
        assertTrue(first.users.login(email, PASSWORD).isFailure)
    }

    @Test fun concurrentAttendanceAndSocialDataPersistAcrossClients() = runBlocking {
        val owner = client()
        val author = owner.users.register("Organizador", "owner-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        // Fixture solo local: el registro público nunca permite elegir ADMINISTRATOR.
        fixtureUpdate("users/${author.id}", mapOf("role" to mapOf("stringValue" to UserRole.ADMINISTRATOR.name)))
        waitUntil { owner.users.findUser(author.id)?.role == UserRole.ADMINISTRATOR }
        val a = client()
        val aUser = a.users.register("Participante A", "a-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        val b = client()
        val bUser = b.users.register("Participante B", "b-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        waitUntil { listOf(owner, a, b).all { it.users.findUser(author.id) != null && it.users.findUser(aUser.id) != null && it.users.findUser(bUser.id) != null } }
        val event = owner.events.create(author.id, reviewedDraft(author.id, draft(author.id, author.name))).getOrThrow()
        assertTrue(owner.events.moderate(author.id, event.id, EventModerationStatus.REJECTED, " ").isFailure)
        owner.events.moderate(author.id, event.id, EventModerationStatus.APPROVED).getOrThrow()
        waitUntil { a.events.findEvent(event.id)?.moderationStatus == EventModerationStatus.APPROVED && b.events.findEvent(event.id)?.moderationStatus == EventModerationStatus.APPROVED }
        println("Integración: dos inscripciones concurrentes")
        val statuses = coroutineScope {
            listOf(async { a.events.register(aUser.id, event.id).getOrThrow() },
                async { b.events.register(bUser.id, event.id).getOrThrow() }).awaitAll()
        }
        assertEquals(1, statuses.count { it == EventRegistrationStatus.CONFIRMED })
        assertEquals(1, statuses.count { it == EventRegistrationStatus.WAITLIST })
        waitUntil { owner.events.registrations.value.count { it.eventId == event.id } == 2 }
        assertEquals(1, owner.events.findEvent(event.id)!!.attendeeCount)
        val confirmed = owner.events.registrations.value.first { it.eventId == event.id && it.status == EventRegistrationStatus.CONFIRMED }
        val (winner, winnerId, promotedId) = if (confirmed.userId == aUser.id) Triple(a, aUser.id, bUser.id) else Triple(b, bUser.id, aUser.id)
        println("Integración: cancelar y promover espera")
        winner.events.cancelRegistration(winnerId, event.id).getOrThrow()
        waitUntil { owner.events.registrations.value.any { it.eventId == event.id && it.userId == promotedId && it.status == EventRegistrationStatus.CONFIRMED } }
        assertEquals(1, owner.events.findEvent(event.id)!!.attendeeCount)
        val reviewer = if (promotedId == aUser.id) a else b
        assertTrue(reviewer.community.addComment(promotedId, event.id, "Todavía no finalizó").isFailure)
        fixtureUpdate("events/${event.id}", mapOf(
            "startsAt" to mapOf("integerValue" to (System.currentTimeMillis() - 7200000).toString()),
            "endsAt" to mapOf("integerValue" to (System.currentTimeMillis() - 1000).toString())
        ))
        waitUntil { reviewer.events.findEvent(event.id)?.hasEnded == true }
        println("Integración: reseña posterior a la participación")
        val comment = reviewer.community.addComment(promotedId, event.id, "Comentario persistido", reviewedText(promotedId, "Comentario persistido")).getOrThrow()
        a.community.vote(aUser.id, event.id, 1).getOrThrow()
        a.community.toggleFavorite(aUser.id, event.id).getOrThrow()
        a.community.toggleFollow(aUser.id, author.id).getOrThrow()
        waitUntil { owner.community.comments.value.any { it.id == comment.id } && owner.community.votes.value.any { it.eventId == event.id } }
        assertEquals(1, owner.users.statistics(author.id).receivedVotes)
        waitUntil { owner.community.notifications.value.any { it.eventId == event.id && it.title == "Nuevo like" } }
        val like = owner.community.notifications.value.single { it.eventId == event.id && it.title == "Nuevo like" }
        assertEquals("${aUser.name} le dio like a ${event.title}.", like.message)
        assertFalse(like.isRead)
        a.community.vote(aUser.id, event.id, -1).getOrThrow()
        waitUntil { owner.community.notifications.value.any { it.eventId == event.id && it.title == "Nuevo dislike" } }
        val dislike = owner.community.notifications.value.single { it.eventId == event.id && it.title == "Nuevo dislike" }
        assertEquals("${aUser.name} le dio dislike a ${event.title}.", dislike.message)
        assertEquals(-1, owner.db.collection("votes").whereEqualTo("eventId", event.id).get(Source.SERVER).await().documents.single().getLong("value")?.toInt())
        a.community.vote(aUser.id, event.id, -1).getOrThrow()
        waitUntil { owner.community.votes.value.none { it.eventId == event.id } }
        assertEquals(2, owner.db.collection("notifications").whereEqualTo("eventId", event.id).get(Source.SERVER).await().documents.count {
            it.getString("title") in setOf("Nuevo like", "Nuevo dislike")
        })
        // Una reacción nueva vuelve a avisar después de retirarla.
        a.community.vote(aUser.id, event.id, 1).getOrThrow()
        a.community.vote(aUser.id, event.id, 1).getOrThrow()
        waitUntil { owner.community.votes.value.none { it.eventId == event.id } }
        waitUntil { owner.community.notifications.value.any { it.eventId == event.id && it.title == "Nuevo comentario" } }
        assertTrue(owner.community.notifications.value.all { it.userId == author.id })
        val notification = owner.community.notifications.value.first { it.eventId == event.id && it.title == "Nuevo comentario" }
        owner.community.markRead(author.id, notification.id).getOrThrow()
        assertTrue(owner.db.collection("notifications").document(notification.id.toString()).get(Source.SERVER).await().getBoolean("isRead") == true)
        println("Integración: limpieza de publicación e interacciones")
        owner.events.delete(author.id, event.id).getOrThrow()
        assertFalse(owner.db.collection("events").document(event.id.toString()).get(Source.SERVER).await().exists())
        assertTrue(owner.db.collection("comments").whereEqualTo("eventId", event.id).get(Source.SERVER).await().isEmpty)
        a.userRepository.signOut()
        waitUntil { a.community.notifications.value.isEmpty() && a.events.events.value.isEmpty() }
    }

    @Test fun relaunchPreservesFavoritesAndNotifiesOnlyAfterNewApproval() = runBlocking {
        val owner = client()
        val author = owner.users.register("Autor recurrente", "series-owner-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        fixtureUpdate("users/${author.id}", mapOf("role" to mapOf("stringValue" to UserRole.ADMINISTRATOR.name)))
        waitUntil { owner.users.findUser(author.id)?.role == UserRole.ADMINISTRATOR }
        val viewer = client()
        val participant = viewer.users.register("Público recurrente", "series-viewer-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        waitUntil { owner.users.findUser(participant.id) != null && viewer.users.findUser(author.id) != null }
        val original = owner.events.create(author.id, reviewedDraft(author.id, draft(author.id, author.name))).getOrThrow()
        owner.events.moderate(author.id, original.id, EventModerationStatus.APPROVED).getOrThrow()
        waitUntil { viewer.events.findEvent(original.id)?.moderationStatus == EventModerationStatus.APPROVED }
        viewer.community.toggleFavorite(participant.id, original.id).getOrThrow()
        viewer.events.register(participant.id, original.id).getOrThrow()
        fixtureUpdate("events/${original.id}", mapOf(
            "startsAt" to mapOf("integerValue" to (System.currentTimeMillis() - 7200000).toString()),
            "endsAt" to mapOf("integerValue" to (System.currentTimeMillis() - 1000).toString())
        ))
        waitUntil { owner.events.findEvent(original.id)?.hasEnded == true && viewer.events.findEvent(original.id)?.hasEnded == true }
        val comment = viewer.community.addComment(participant.id, original.id, "Primera edición disfrutada", reviewedText(participant.id, "Primera edición disfrutada")).getOrThrow()
        val next = owner.events.relaunch(author.id, original.id, reviewedDraft(author.id, draft(author.id, author.name), original.id)).getOrThrow()
        assertEquals(original.seriesId, next.seriesId)
        assertEquals(EventModerationStatus.PENDING, next.moderationStatus)
        assertEquals(0, next.attendeeCount)
        assertFalse(viewer.community.notifications.value.any { it.eventId == next.id })
        owner.events.moderate(author.id, next.id, EventModerationStatus.APPROVED).getOrThrow()
        waitUntil { viewer.community.notifications.value.any { it.eventId == next.id && it.title == "Nueva edición de tu evento favorito" } }
        assertEquals(1, viewer.community.notifications.value.count { it.eventId == next.id && it.title == "Nueva edición de tu evento favorito" })
        waitUntil { viewer.events.findEvent(next.id)?.moderationStatus == EventModerationStatus.APPROVED }
        assertFalse(viewer.events.registrations.value.any { it.eventId == next.id && it.userId == participant.id })
        assertTrue(viewer.community.comments.value.any { it.id == comment.id && it.eventId == original.id })
        assertFalse(viewer.community.canComment(participant.id, next.id))
        viewer.events.register(participant.id, next.id).getOrThrow()
        owner.events.delete(author.id, original.id).getOrThrow()
        assertTrue(viewer.db.collection("favorites").whereEqualTo("userId", participant.id).get(Source.SERVER).await().documents.isNotEmpty())
        viewer.community.toggleFavorite(participant.id, next.id).getOrThrow()
        assertTrue(viewer.db.collection("favorites").whereEqualTo("userId", participant.id).get(Source.SERVER).await().isEmpty)
    }

    @Test fun storageUploadsEventAndProfileImagesAndRejectsInvalidContent() = runBlocking {
        val client = client()
        val user = client.users.register("Fotos Firebase", "image-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        fixtureUpdate("users/${user.id}", mapOf("role" to mapOf("stringValue" to UserRole.ORGANIZER.name),
            "organizerVerificationStatus" to mapOf("stringValue" to OrganizerVerificationStatus.VERIFIED.name)))
        waitUntil { client.users.findUser(user.id)?.canCreateEvents == true }
        val uri = imageUri()
        val uploaded = client.media.uploadImage(user.id, uri.toString(), "events")
        val event = client.events.create(user.id, reviewedDraft(user.id, draft(user.id, user.name).copy(photoUri = uploaded))).getOrThrow()
        val remoteUrl = requireNotNull(event.photoUri)
        assertFalse(remoteUrl.startsWith("content:"))
        assertEquals(remoteUrl, client.db.collection("events").document(event.id.toString()).get(Source.SERVER).await().getString("photoUri"))
        assertTrue(client.storage.imageReference(remoteUrl).getBytes(1_000_000).await().isNotEmpty())
        client.users.updateProfile(user.id, user.name, user.email, null, photoUri = uri.toString()).getOrThrow()
        val profileImage = client.db.collection("users").document(user.id).get(Source.SERVER).await().getString("profilePictureUrl")!!
        assertTrue(client.storage.imageReference(profileImage).getBytes(1_000_000).await().isNotEmpty())
        val withoutPhoto = client.events.update(user.id, reviewedDraft(user.id, event.copy(photoUri = null), event.id)).getOrThrow()
        assertNull(withoutPhoto.photoUri)
        val deletedImage = runCatching { client.storage.imageReference(remoteUrl).getMetadata().await() }.exceptionOrNull()
        assertTrue(deletedImage is com.google.firebase.storage.StorageException &&
            deletedImage.errorCode == com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND)
        assertTrue(runCatching { client.media.uploadImage(user.id, "file:///private/image.png", "events") }.isFailure)
        client.events.delete(user.id, event.id).getOrThrow()
        client.users.deleteAccount(user.id, PASSWORD).getOrThrow()
    }

    @Test fun organizerNeedsPrivateIdentityReviewBeforePublishing() = runBlocking {
        val moderator = client()
        val admin = moderator.users.register("Administrador", "reviewer-${UUID.randomUUID()}@example.test",
            PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        fixtureUpdate("users/${admin.id}", mapOf("role" to mapOf("stringValue" to UserRole.ADMINISTRATOR.name)))
        waitUntil { moderator.users.findUser(admin.id)?.role == UserRole.ADMINISTRATOR }
        val owner = client()
        val organizer = owner.users.register("Organizador pendiente", "identity-${UUID.randomUUID()}@example.test",
            PASSWORD, null, UserRole.ORGANIZER, imageUri().toString()).getOrThrow()
        waitUntil { moderator.users.findUser(organizer.id) != null }
        assertEquals(OrganizerVerificationStatus.PENDING, organizer.organizerVerificationStatus)
        assertFalse(owner.events.canPublish(organizer.id))
        assertTrue(owner.events.create(organizer.id, draft(organizer.id, organizer.name)).isFailure)
        val publicProfile = owner.db.collection("users").document(organizer.id).get(Source.SERVER).await()
        assertFalse(publicProfile.contains("photoPath"))
        val identity = moderator.users.loadOrganizerIdentity(admin.id, organizer.id).getOrThrow()
        assertTrue(identity.photoPath.startsWith("gs://"))
        assertTrue(identity.imageBytes.isNotEmpty())
        val viewer = client()
        val participant = viewer.users.register("Participante", "private-viewer-${UUID.randomUUID()}@example.test",
            PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        assertTrue(runCatching { viewer.db.collection("organizerVerifications").document(organizer.id).get(Source.SERVER).await() }.isFailure)
        assertTrue(runCatching { viewer.media.readPrivateImage(identity.photoPath) }.isFailure)
        assertTrue(viewer.users.reviewOrganizerIdentity(participant.id, organizer.id, identity.photoPath, true).isFailure)
        moderator.users.reviewOrganizerIdentity(admin.id, organizer.id, identity.photoPath, true).getOrThrow()
        waitUntil { owner.users.findUser(organizer.id)?.canCreateEvents == true }
        assertTrue(owner.events.create(organizer.id, reviewedDraft(organizer.id, draft(organizer.id, organizer.name))).isSuccess)
        owner.users.deleteAccount(organizer.id, PASSWORD).getOrThrow()
        assertFalse(moderator.db.collection("organizerVerifications").document(organizer.id).get(Source.SERVER).await().exists())
        assertTrue(runCatching { moderator.media.readPrivateImage(identity.photoPath) }.isFailure)
    }

    @Test fun restrictedLoginReportsStatusWithoutPublishingAnAuthenticatedSession() = runBlocking {
        val original = client()
        val user = original.users.register("Cuenta restringida", "restricted-${UUID.randomUUID()}@example.test",
            PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        original.userRepository.signOut()
        val attempting = client()
        val publishedIds = mutableListOf<String?>()
        val publishedProfiles = mutableListOf<User>()
        val idsJob = launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            attempting.userRepository.currentUserId.collect { publishedIds.add(it) }
        }
        val profilesJob = launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            attempting.userRepository.users.collect { publishedProfiles.addAll(it) }
        }
        try {
            fixtureUpdate("users/${user.id}", mapOf("accountStatus" to mapOf("stringValue" to AccountStatus.SUSPENDED.name)))
            assertEquals("Tu cuenta está suspendida. Contacta al administrador.",
                attempting.users.login(user.email, PASSWORD).exceptionOrNull()?.message)
            assertNull(attempting.auth.currentUser)
            fixtureUpdate("users/${user.id}", mapOf("role" to mapOf("stringValue" to UserRole.ORGANIZER.name),
                "organizerVerificationStatus" to mapOf("stringValue" to OrganizerVerificationStatus.REJECTED.name)))
            assertEquals("Tu cuenta está bloqueada. Contacta al administrador.",
                attempting.users.login(user.email, PASSWORD).exceptionOrNull()?.message)
            assertNull(attempting.auth.currentUser)
            assertEquals("Correo o contraseña incorrectos.",
                attempting.users.login(user.email, "Incorrecta123").exceptionOrNull()?.message)
        } finally {
            idsJob.cancelAndJoin()
            profilesJob.cancelAndJoin()
        }
        assertFalse(user.id in publishedIds)
        assertFalse(publishedProfiles.any { it.id == user.id })
        fixtureUpdate("users/${user.id}", mapOf("accountStatus" to mapOf("stringValue" to AccountStatus.ACTIVE.name),
            "organizerVerificationStatus" to mapOf("stringValue" to OrganizerVerificationStatus.PENDING.name)))
        assertEquals(user.id, attempting.users.login(user.email, PASSWORD).getOrThrow().id)
    }

    private fun client(): Client {
        val options = FirebaseOptions.Builder().setProjectId("demo-share-you")
            .setApplicationId("1:123456789:android:1234567890abcdef")
            .setApiKey("demo-firebase-emulator-key")
            .setStorageBucket("demo-share-you.appspot.com").build()
        val app = FirebaseApp.initializeApp(context, options, "integration-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
        val db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator(host, 8080)
        }
        val storage = FirebaseStorage.getInstance(app).apply { useEmulator(host, 9199) }
        return Client(app, auth, db, storage).also(clients::add)
    }

    private inner class Client(val app: FirebaseApp, val auth: FirebaseAuth, val db: FirebaseFirestore, val storage: FirebaseStorage) {
        val media = FirebaseMediaRepository(context, storage, auth)
        val userRepository = FirebaseUserRepository(auth, db, media)
        val eventRepository = FirebaseEventRepository(db, auth)
        val communityRepository = FirebaseCommunityRepository(db, auth)
        val events = EventUseCases(eventRepository, userRepository, communityRepository, media)
        val users = UserUseCases(userRepository, eventRepository, communityRepository, events, media)
        val community = CommunityUseCases(communityRepository, eventRepository, userRepository)
    }

    private fun draft(owner: String, name: String) = Event(
        id = 0, title = "Evento Firebase", startsAt = System.currentTimeMillis() + 86_400_000,
        endsAt = System.currentTimeMillis() + 90_000_000, location = "Armenia", distanceKm = 0.0,
        price = 0, category = EventCategory.COMMUNITY, attendeeCount = 0, description = "Prueba multicliente",
        capacity = 1, organizerId = owner, organizerName = name, locationConfirmed = true)

    /** Simula únicamente la emisión del comprobante; Gemini se verifica en pruebas independientes. */
    private suspend fun reviewedDraft(uid: String, event: Event, sourceId: Int? = null): Event {
        val reviewId = UUID.randomUUID().toString()
        fun str(v: String): Map<String, Any> = mapOf("stringValue" to v)
        fun num(v: Long): Map<String, Any> = mapOf("integerValue" to v.toString())
        fixtureUpdate("aiReviews/$reviewId", mapOf(
            "kind" to str("event"), "uid" to str(uid), "title" to str(event.title.trim()),
            "description" to str(event.description.trim()), "category" to str(event.category.name),
            "latitude" to mapOf("doubleValue" to event.latitude), "longitude" to mapOf("doubleValue" to event.longitude),
            "startsAt" to num(event.startsAt), "endsAt" to num(event.endsAt),
            "sourceEventId" to (sourceId?.let { num(it.toLong()) } ?: mapOf("nullValue" to JSONObject.NULL)),
            "photoUri" to (event.photoUri?.let(::str) ?: mapOf("nullValue" to JSONObject.NULL)),
            "hasDuplicates" to mapOf("booleanValue" to false), "expiresAt" to num(System.currentTimeMillis() + 900_000)
        ))
        return event.copy(aiReviewId = reviewId)
    }

    private suspend fun reviewedText(uid: String, text: String): String {
        val id = UUID.randomUUID().toString()
        fixtureUpdate("aiReviews/$id", mapOf("kind" to mapOf("stringValue" to "text"), "uid" to mapOf("stringValue" to uid),
            "text" to mapOf("stringValue" to text), "expiresAt" to mapOf("integerValue" to (System.currentTimeMillis() + 900_000).toString())))
        return id
    }

    /** Fixture administrativa SOLO en el emulador local, nunca en el proyecto real. */
    private suspend fun fixtureUpdate(path: String, fields: Map<String, Map<String, Any>>) = withContext(Dispatchers.IO) {
        val update = JSONObject().put("name", "projects/demo-share-you/databases/(default)/documents/$path")
            .put("fields", JSONObject().apply { fields.forEach { (key, value) -> put(key, JSONObject(value)) } })
        val payload = JSONObject().put("writes", JSONArray().put(JSONObject().put("update", update)
            .put("updateMask", JSONObject().put("fieldPaths", JSONArray(fields.keys.toList())))))
        val connection = URL("http://$host:8080/v1/projects/demo-share-you/databases/(default)/documents:commit").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.doOutput = true
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            assertEquals("La fixture local debe guardarse", 200, connection.responseCode)
            connection.inputStream.close()
        } finally { connection.disconnect() }
    }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(50)
    }

    private fun imageUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "shareyou-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShareYouTests")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        images.add(uri)
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return uri
    }

    private companion object { const val PASSWORD = "PruebaFirebase123" }
}
