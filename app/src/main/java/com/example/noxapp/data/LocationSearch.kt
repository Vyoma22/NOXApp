package com.example.noxapp.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationSearch {
    private val legacyExecutor by lazy { Executors.newSingleThreadExecutor() }

    suspend fun find(context: Context, query: String): List<Address> {
        if (!Geocoder.isPresent()) throw IOException("Geocoder unavailable")
        val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine<List<Address>> { continuation ->
                geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resumeWithException(IOException(errorMessage))
                    }
                })
            }
        } else {
            awaitLegacyResult {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 5).orEmpty()
            }
        }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }
            .distinctBy { it.latitude to it.longitude }
    }

    // Legacy Binder calls may outlive interruption, so UI cancellation cannot wait for them.
    internal suspend fun awaitLegacyResult(lookup: () -> List<Address>): List<Address> =
        suspendCancellableCoroutine { continuation ->
            val request = legacyExecutor.submit {
                try {
                    val result = lookup()
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { request.cancel(true) }
        }
}
