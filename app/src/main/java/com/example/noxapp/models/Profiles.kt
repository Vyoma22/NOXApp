package com.example.noxapp.models

import com.google.firebase.Timestamp

data class UserProfile(
    var uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val school: String? = null,
    val gradMonth: Long? = null,
    val gradYear: Long? = null,
    val accessExpiresAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val savedItemIds: List<String> = emptyList(),
    // A missing count means the seller has no ratings yet.
    val rating: Double = 0.0,
    val ratingCount: Long = 0L
)

/**
 * The part of a student's profile that other students may read.
 *
 * `users/{uid}` is owner-only account data; `sellerProfiles/{uid}` is the public projection.
 * They are separate documents because Firestore rules hide whole documents, never individual
 * fields: a single readable profile would expose savedItemIds and the account email too.
 *
 * This type deliberately has no email, savedItemIds or accessExpiresAt, so a screen that shows
 * a seller cannot reach them by accident.
 */
data class SellerProfile(
    val uid: String = "",
    val displayName: String? = null,
    val school: String? = null,
    val gradYear: Long? = null,
    val rating: Double = 0.0,
    val ratingCount: Long = 0L
)
