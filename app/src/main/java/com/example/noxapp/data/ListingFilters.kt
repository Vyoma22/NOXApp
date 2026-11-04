package com.example.noxapp.data

import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.models.SortOption
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ListingFilters {
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val lat1Radians = lat1 * PI / 180.0
        val lat2Radians = lat2 * PI / 180.0
        val halfLatDifference = (lat2 - lat1) * PI / 360.0
        val halfLngDifference = (lng2 - lng1) * PI / 360.0
        val sinLat = sin(halfLatDifference)
        val sinLng = sin(halfLngDifference)
        val a = (sinLat * sinLat + cos(lat1Radians) * cos(lat2Radians) * sinLng * sinLng)
            .coerceIn(0.0, 1.0)

        return 6371.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
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
            return haversineKm(centerLat, centerLng, latitude, longitude) <= radiusKm
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
                        haversineKm(lat, lng, listingLat, listingLng)
                    }
                }
            }
        }
    }
}
