package com.example.noxapp.data

import com.example.noxapp.models.Listing

/**
 * Where the map should drop the pickup pin for a listing, plus the labels shown around it.
 *
 * The detail sheet and the map screen both need this, so it is resolved once in plain Kotlin
 * (no Android types) and unit tested on the JVM.
 */
data class ListingLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val title: String,
) {
    companion object {
        /** Stored coordinates win; listings saved before map pins existed fall back to campus. */
        fun resolve(listing: Listing): ListingLocation {
            val (latitude, longitude) = CampusLocations.resolve(listing)
            return ListingLocation(
                latitude = latitude,
                longitude = longitude,
                label = listing.pickupLocation,
                title = listing.title,
            )
        }
    }
}
