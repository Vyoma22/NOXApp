package com.example.noxapp.data

import com.example.noxapp.models.Listing

/**
 * The filter rules of the read-only seller profile, kept free of Android types so they can be
 * unit tested: the screen loads one list from Firestore, splits it into the two tabs with
 * partition, then narrows the visible tab with the search box and the category spinner.
 */
object SellerListingFilters {

    /** Keeps only the listings posted by [sellerId]; a blank id matches nothing. */
    fun forSeller(listings: List<Listing>, sellerId: String): List<Listing> {
        if (sellerId.isBlank()) return emptyList()
        return listings.filter { it.sellerId == sellerId }
    }

    /** True when [listing] matches the trimmed query (title or description) and the category. */
    fun matches(listing: Listing, query: String, category: String?): Boolean {
        val search = query.trim()
        if (search.isNotEmpty() &&
            !listing.title.contains(search, ignoreCase = true) &&
            !listing.description.contains(search, ignoreCase = true)
        ) {
            return false
        }

        val wantedCategory = category?.trim().orEmpty()
        if (wantedCategory.isNotEmpty() &&
            !listing.category.equals(wantedCategory, ignoreCase = true)
        ) {
            return false
        }

        return true
    }

    /** [listings] narrowed by the query and category, order preserved. */
    fun apply(listings: List<Listing>, query: String, category: String?): List<Listing> =
        listings.filter { matches(it, query, category) }
}
