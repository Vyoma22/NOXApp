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
    // Written by the (not yet built) rating flow; a missing count means the seller has no ratings.
    val rating: Double = 0.0,
    val ratingCount: Long = 0L
)
