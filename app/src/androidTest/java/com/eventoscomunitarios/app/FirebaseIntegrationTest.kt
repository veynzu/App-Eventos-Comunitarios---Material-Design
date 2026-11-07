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
import kotlinx.coroutines.*
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
        val author = owner.users.register("Organizador", "owner-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.ORGANIZER).getOrThrow()
        // Fixture solo local: el registro público nunca permite elegir ADMINISTRATOR.
        owner.db.collection("users").document(author.id).update("role", UserRole.ADMINISTRATOR.name).await()
        waitUntil { owner.users.findUser(author.id)?.role == UserRole.ADMINISTRATOR }
        val a = client()
        val aUser = a.users.register("Participante A", "a-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        val b = client()
        val bUser = b.users.register("Participante B", "b-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.PARTICIPANT).getOrThrow()
        waitUntil { listOf(owner, a, b).all { it.users.findUser(author.id) != null && it.users.findUser(aUser.id) != null && it.users.findUser(bUser.id) != null } }
        val event = owner.events.create(author.id, draft(author.id, author.name)).getOrThrow()
        assertTrue(owner.events.moderate(author.id, event.id, EventModerationStatus.REJECTED, " ").isFailure)
        owner.events.moderate(author.id, event.id, EventModerationStatus.APPROVED).getOrThrow()
        waitUntil { a.events.findEvent(event.id)?.moderationStatus == EventModerationStatus.APPROVED && b.events.findEvent(event.id)?.moderationStatus == EventModerationStatus.APPROVED }
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
        winner.events.cancelRegistration(winnerId, event.id).getOrThrow()
        waitUntil { owner.events.registrations.value.any { it.eventId == event.id && it.userId == promotedId && it.status == EventRegistrationStatus.CONFIRMED } }
        assertEquals(1, owner.events.findEvent(event.id)!!.attendeeCount)
        val comment = a.community.addComment(aUser.id, event.id, "Comentario persistido").getOrThrow()
        a.community.vote(aUser.id, event.id, 1).getOrThrow()
        a.community.toggleFavorite(aUser.id, event.id).getOrThrow()
        a.community.toggleFollow(aUser.id, author.id).getOrThrow()
        waitUntil { owner.community.comments.value.any { it.id == comment.id } && owner.community.votes.value.any { it.eventId == event.id } }
        assertEquals(1, owner.users.statistics(author.id).receivedVotes)
        a.community.vote(aUser.id, event.id, 1).getOrThrow()
        waitUntil { owner.community.votes.value.none { it.eventId == event.id } }
        waitUntil { owner.community.notifications.value.any { it.eventId == event.id && it.title == "Nuevo comentario" } }
        assertTrue(owner.community.notifications.value.all { it.userId == author.id })
        val notification = owner.community.notifications.value.first { it.eventId == event.id && it.title == "Nuevo comentario" }
        owner.community.markRead(author.id, notification.id).getOrThrow()
        assertTrue(owner.db.collection("notifications").document(notification.id.toString()).get(Source.SERVER).await().getBoolean("isRead") == true)
        owner.events.delete(author.id, event.id).getOrThrow()
        assertFalse(owner.db.collection("events").document(event.id.toString()).get(Source.SERVER).await().exists())
        assertTrue(owner.db.collection("comments").whereEqualTo("eventId", event.id).get(Source.SERVER).await().isEmpty)
        a.userRepository.signOut()
        waitUntil { a.community.notifications.value.isEmpty() && a.events.events.value.isEmpty() }
    }

    @Test fun storageUploadsEventAndProfileImagesAndRejectsInvalidContent() = runBlocking {
        val client = client()
        val user = client.users.register("Fotos Firebase", "image-${UUID.randomUUID()}@example.test", PASSWORD, null, UserRole.ORGANIZER).getOrThrow()
        val uri = imageUri()
        val event = client.events.create(user.id, draft(user.id, user.name).copy(photoUri = uri.toString())).getOrThrow()
        val remoteUrl = requireNotNull(event.photoUri)
        assertFalse(remoteUrl.startsWith("content:"))
        assertEquals(remoteUrl, client.db.collection("events").document(event.id.toString()).get(Source.SERVER).await().getString("photoUri"))
        assertTrue(client.storage.imageReference(remoteUrl).getBytes(1_000_000).await().isNotEmpty())
        client.users.updateProfile(user.id, user.name, user.email, null, photoUri = uri.toString()).getOrThrow()
        val profileImage = client.db.collection("users").document(user.id).get(Source.SERVER).await().getString("profilePictureUrl")!!
        assertTrue(client.storage.imageReference(profileImage).getBytes(1_000_000).await().isNotEmpty())
        val withoutPhoto = client.events.update(user.id, event.copy(photoUri = null)).getOrThrow()
        assertNull(withoutPhoto.photoUri)
        val deletedImage = runCatching { client.storage.imageReference(remoteUrl).getMetadata().await() }.exceptionOrNull()
        assertTrue(deletedImage is com.google.firebase.storage.StorageException &&
            deletedImage.errorCode == com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND)
        assertTrue(runCatching { client.media.uploadImage(user.id, "file:///private/image.png", "events") }.isFailure)
        client.events.delete(user.id, event.id).getOrThrow()
        client.users.deleteAccount(user.id, PASSWORD).getOrThrow()
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
        val userRepository = FirebaseUserRepository(auth, db)
        val eventRepository = FirebaseEventRepository(db, auth)
        val communityRepository = FirebaseCommunityRepository(db, auth)
        val media = FirebaseMediaRepository(context, storage, auth)
        val events = EventUseCases(eventRepository, userRepository, communityRepository, media)
        val users = UserUseCases(userRepository, eventRepository, communityRepository, events, media)
        val community = CommunityUseCases(communityRepository, eventRepository, userRepository)
    }

    private fun draft(owner: String, name: String) = Event(
        id = 0, title = "Evento Firebase", startsAt = System.currentTimeMillis() + 86_400_000,
        endsAt = System.currentTimeMillis() + 90_000_000, location = "Armenia", distanceKm = 0.0,
        price = 0, category = EventCategory.COMMUNITY, attendeeCount = 0, description = "Prueba multicliente",
        capacity = 1, organizerId = owner, organizerName = name)

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
