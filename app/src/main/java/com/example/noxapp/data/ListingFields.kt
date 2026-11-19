package com.example.noxapp.data

import com.example.noxapp.models.Listing

/**
 * Builds a [Listing] from a Firestore document's raw fields.
 *
 * Pure Kotlin on purpose: no DocumentSnapshot, so a malformed document can be unit tested on the
 * JVM. Every field is taken only when it holds the expected type. DocumentSnapshot's typed getters
 * throw on a wrong type (they answer null only for a missing field) and convertToCustomClass throws
 * on the whole document; either would crash inside a feed's success listener and take every other
 * listing down with it. A safe cast per field turns a bad document into a listing with defaults.
 *
 * The security rules reject these types on write. This is the second half of the same defence, for
 * documents written before the rules were tightened.
 *
 * Documents written before the model merge use name/location/imageUrl. Those fallbacks are kept.
 */
object ListingFields {

    fun toListing(id: String, fields: Map<String, Any?>): Listing {
        // One source of truth for the defaults: the model itself.
        val defaults = Listing()

        val images = (fields["imageUrls"] as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()

        return Listing(
            id = id,
            title = (fields["title"] as? String).orEmpty()
                .ifBlank { (fields["name"] as? String).orEmpty() },
            price = (fields["price"] as? Number)?.toDouble() ?: defaults.price,
            category = (fields["category"] as? String) ?: defaults.category,
            condition = (fields["condition"] as? String) ?: defaults.condition,
            description = (fields["description"] as? String) ?: defaults.description,
            pickupLocation = (fields["location"] as? String)
                ?: (fields["pickupLocation"] as? String)
                ?: defaults.pickupLocation,
            imageUrls = images.ifEmpty { listOfNotNull(fields["imageUrl"] as? String) },
            sellerId = (fields["sellerId"] as? String) ?: defaults.sellerId,
            sellerName = (fields["sellerName"] as? String) ?: defaults.sellerName,
            featured = (fields["featured"] as? Boolean) ?: defaults.featured,
            sold = (fields["sold"] as? Boolean) ?: defaults.sold,
            salePending = (fields["salePending"] as? Boolean) ?: defaults.salePending,
            createdAt = ListingTimestamps.toMillis(fields["createdAt"]) ?: defaults.createdAt,
            latitude = (fields["latitude"] as? Number)?.toDouble() ?: defaults.latitude,
            longitude = (fields["longitude"] as? Number)?.toDouble() ?: defaults.longitude,
            viewCount = (fields["viewCount"] as? Number)?.toLong() ?: defaults.viewCount,
            previousPrice = (fields["previousPrice"] as? Number)?.toDouble()
        )
    }
}