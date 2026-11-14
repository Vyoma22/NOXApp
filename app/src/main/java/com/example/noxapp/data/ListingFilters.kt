package com.example.noxapp.data

import android.location.Location
import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.models.SortOption

object ListingFilters {
    /** Uses the same WGS84 distance the map's radius circle is drawn with. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val meters = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, meters)
        return meters[0] / 1000.0
    }

    fun matches(listing: Listing, filters: BrowseFilters): Boolean {
        val query = filters.query.trim()
        if (query.isNotEmpty() &&
            !listing.title.contains(query, ignoreCase = true) &&
            !listing.description.contains(query, ignoreCase = true)
        ) {
            return false
        }

        val category = filters.category
        if (category != null && !listing.category.equals(category, ignoreCase = true)) {
            return false
        }

        val priceRange = filters.priceRange
        if (priceRange != null && !priceRange.contains(listing.price)) {
            return false
        }

        val condition = filters.condition
        if (condition != null && !listing.condition.equals(condition, ignoreCase = true)) {
            return false
        }

        val centerLat = filters.centerLat
        val centerLng = filters.centerLng
        val radiusKm = filters.radiusKm
        if (centerLat != null && centerLng != null && radiusKm != null && radiusKm > 0.0) {
            val (latitude, longitude) = CampusLocations.resolve(listing)
            return distanceKm(centerLat, centerLng, latitude, longitude) <= radiusKm
        }

        return true
    }

    fun apply(listings: List<Listing>, filters: BrowseFilters): List<Listing> {
        val filtered = listings.filter { matches(it, filters) }
        return when (filters.sort) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOption.PRICE_LOW -> filtered.sortedWith(
                compareBy<Listing> { it.price }.thenByDescending { it.createdAt }
            )
            SortOption.PRICE_HIGH -> filtered.sortedWith(
                compareByDescending<Listing> { it.price }.thenByDescending { it.createdAt }
            )
            SortOption.NEAREST -> {
                val lat = filters.centerLat
                val lng = filters.centerLng
                if (lat == null || lng == null) {
                    filtered.sortedByDescending { it.createdAt }
                } else {
                    filtered.sortedBy {
                        val (listingLat, listingLng) = CampusLocations.resolve(it)
                        distanceKm(lat, lng, listingLat, listingLng)
                    }
                }
            }
        }
    }
}
