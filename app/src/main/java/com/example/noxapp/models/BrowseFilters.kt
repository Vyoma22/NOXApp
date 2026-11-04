package com.example.noxapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Price brackets offered by the Browse filter.
 *
 * The Free entry is a zero-width band ([minInclusive] == [maxExclusive]); [contains] treats it as
 * "no cost", so free listings match regardless of the normal half-open range rule.
 */
enum class PriceRange(val label: String, val minInclusive: Double, val maxExclusive: Double) {
    FREE("Free", 0.0, 0.0),
    UNDER_25("Under $25", 0.0, 25.0),
    FROM_25_TO_50("$25 - $50", 25.0, 50.0),
    FROM_50_TO_100("$50 - $100", 50.0, 100.0),
    OVER_100("$100+", 100.0, Double.MAX_VALUE);

    fun contains(price: Double): Boolean =
        if (minInclusive == maxExclusive) {
            // A zero-width band means "exactly free"; negative prices are treated as no cost too.
            price <= 0.0
        } else {
            minInclusive <= price && price < maxExclusive
        }
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
