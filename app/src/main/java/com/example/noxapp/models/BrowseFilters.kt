package com.example.noxapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class PriceRange(val label: String, val minInclusive: Double, val maxExclusive: Double) {
    UNDER_25("Under $25", 0.0, 25.0),
    FROM_25_TO_50("$25 - $50", 25.0, 50.0),
    FROM_50_TO_100("$50 - $100", 50.0, 100.0),
    OVER_100("$100+", 100.0, Double.MAX_VALUE)
}

enum class SortOption { NEWEST, PRICE_LOW, PRICE_HIGH, NEAREST }

@Parcelize
data class BrowseFilters(
    val query: String = "",
    val category: String? = null,
    val priceRange: PriceRange? = null,
    val condition: String? = null,
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val radiusKm: Double? = null,
    val sort: SortOption = SortOption.NEWEST
) : Parcelable
