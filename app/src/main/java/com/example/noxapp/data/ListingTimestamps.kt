package com.example.noxapp.data

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Normalises the raw Firestore `createdAt` value for [com.example.noxapp.models.Listing].
 *
 * Listings created after the server-timestamp migration store a Firestore [Timestamp] there;
 * documents written by earlier app versions store epoch millis as a Number. Both end up as epoch
 * millis so the model can keep `createdAt` a plain Long and every screen keeps comparing longs.
 *
 * Pure Kotlin on purpose (Firebase's Timestamp is a plain value type), so it is unit tested on the
 * JVM.
 */
object ListingTimestamps {

    /** Epoch millis for [value], or null when it is missing or an unsupported/garbage type. */
    fun toMillis(value: Any?): Long? = when (value) {
        is Timestamp -> value.seconds * 1_000L + value.nanoseconds / 1_000_000L
        is Date -> value.time
        is Number -> value.toLong()
        else -> null
    }
}
