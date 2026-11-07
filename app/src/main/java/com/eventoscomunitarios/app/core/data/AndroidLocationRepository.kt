package com.eventoscomunitarios.app.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLocationRepository @Inject constructor(@param:ApplicationContext private val context: Context) : LocationRepository {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Coordinates? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { (fine || it != LocationManager.GPS_PROVIDER) && manager.isProviderEnabled(it) }
        if (providers.isEmpty()) return null
        // Una posición antigua no debe usarse como si fuera la ubicación actual.
        val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..120_000_000_000L }
            .minByOrNull { it.accuracy }
        if (recent != null) return Coordinates(recent.latitude, recent.longitude)
        return withTimeoutOrNull(12_000) {
            coroutineScope {
                val responses = Channel<Coordinates?>(providers.size)
                val jobs = providers.map { provider -> launch {
                    val point = try { singleFix(manager, provider) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    responses.send(point)
                } }
                try {
                    repeat(providers.size) { responses.receive()?.let { return@coroutineScope it } }
                    null
                } finally { jobs.forEach { it.cancel() }; responses.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(manager: LocationManager, provider: String): Coordinates? =
            suspendCancellableCoroutine { continuation ->
                fun deliver(location: Location?) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(location?.let { Coordinates(it.latitude, it.longitude) }))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { deliver(it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) { manager.removeUpdates(this); deliver(location) }
                        @Deprecated("API anterior a Android 11")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }

    override suspend fun addressFor(coordinates: Coordinates): String? {
        require(coordinates.isValid)
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("es", "CO"))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resumeWith(Result.success(addresses.firstOrNull()?.getAddressLine(0)))
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resumeWith(Result.success(null))
                        }
                    })
                }
            }
        } else runInterruptible(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()?.getAddressLine(0)
        }
    }
}
