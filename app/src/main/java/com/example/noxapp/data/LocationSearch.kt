package com.example.noxapp.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

object LocationSearch {
    /** The geocoder blocks, so a caller's timeout bounds the wait, not the lookup itself. */
    @Suppress("DEPRECATION")
    suspend fun find(context: Context, query: String): List<Address> {
        if (!Geocoder.isPresent()) throw IOException("Geocoder unavailable")
        val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
        val addresses = withContext(Dispatchers.IO) {
            geocoder.getFromLocationName(query, 5).orEmpty()
        }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }
            .distinctBy { it.latitude to it.longitude }
    }

    /** Turns the pin's coordinates into a human-readable pickup label. */
    @Suppress("DEPRECATION")
    suspend fun reverse(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
        val addresses = withContext(Dispatchers.IO) {
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }
        return addresses.firstOrNull()?.let { address ->
            address.getAddressLine(0) ?: listOfNotNull(
                address.featureName,
                address.locality,
                address.adminArea,
                address.countryName
            ).distinct().joinToString(", ").ifEmpty { null }
        }
    }
}
