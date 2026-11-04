package com.example.noxapp.models

data class Listing(
    var id: String = "",
    val title: String = "",
    val price: Double = 0.0,
    val category: String = "All",
    val condition: String = "Used",
    val description: String = "",
    val pickupLocation: String = "Ontario Tech (North Campus)",
    val imageUrls: List<String> = emptyList(),
    val sellerId: String = "",
    val sellerName: String = "",
    val featured: Boolean = false,
    // Set by the mark-as-sold flow; a missing Firestore field reads as false, so every
    // listing written before that flow existed stays on the Listings tab.
    val sold: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)