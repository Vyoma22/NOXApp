package com.example.noxapp.models

import kotlin.math.roundToLong

/**
 * Social-proof decisions for listing cards and the detail sheet.
 *
 * Pure Kotlin on purpose: no Android imports, so the thresholds and drops can be unit tested on
 * the JVM. The UI keeps the formatting; this object only decides what should be shown.
 */
object ListingSignals {

    /** Views at which a listing counts as popular and earns the badge. */
    const val POPULAR_VIEW_THRESHOLD = 10L

    /** True once a listing has been opened often enough to be called popular. */
    fun isPopular(listing: Listing): Boolean = listing.viewCount >= POPULAR_VIEW_THRESHOLD

    /**
     * Whole-dollar amount the seller has cut from the price, or null when there is no drop.
     *
     * A drop is only a signal when the previous price is strictly higher than the current one,
     * so an edit that raised the price shows no badge. The amount is rounded down from the exact
     * cent difference: a $0.50 cut has no whole-dollar badge, and the pill never claims more of
     * a saving than the seller actually made.
     */
    fun priceDropAmount(listing: Listing): Long? {
        val previousPrice = listing.previousPrice ?: return null
        if (!previousPrice.isFinite() || !listing.price.isFinite()) return null
        if (previousPrice <= listing.price) return null

        val dropCents = ((previousPrice - listing.price) * 100).roundToLong()
        if (dropCents < 100) return null
        return dropCents / 100
    }

    /**
     * Count shown in the detail sheet. A buyer's open is counted immediately, before the
     * Firestore write lands, so the number moves with the tap. The owner sees the stored count
     * because their own visits are not social proof.
     */
    fun displayedViewCount(listing: Listing, isOwner: Boolean): Long =
        if (isOwner) listing.viewCount else listing.viewCount + 1
}
