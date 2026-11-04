package com.example.noxapp.data

import com.example.noxapp.models.Listing

/**
 * Builds the pre-filled email a buyer sends when reporting a listing.
 *
 * The copy lives in strings.xml (`listing_report_subject_format` and
 * `listing_report_body_format`) so it stays editable and localisable; the item detail sheet
 * passes those templates in and this class only fills in the listing fields. That keeps the
 * logic plain Kotlin (no Android types) and unit tested on the JVM.
 *
 * Subject placeholders: 1 title, 2 listing id.
 * Body placeholders: 1 id, 2 title, 3 price, 4 category, 5 condition, 6 seller id,
 * 7 seller name, 8 pickup location. A blank seller name becomes [UNKNOWN_SELLER].
 */
class ListingReport(
    private val subjectFormat: String,
    private val bodyFormat: String,
) {
    fun subject(listing: Listing): String = subjectFormat.format(listing.title, listing.id)

    fun body(listing: Listing): String = bodyFormat.format(
        listing.id,
        listing.title,
        listing.price.toInt(),
        listing.category,
        listing.condition,
        listing.sellerId,
        listing.sellerName.ifBlank { UNKNOWN_SELLER },
        listing.pickupLocation,
    )

    companion object {
        /** Shown instead of an empty "Seller name:" line when a listing has no seller on record. */
        const val UNKNOWN_SELLER = "Unknown seller"
    }
}
