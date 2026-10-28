package com.example.noxapp.models

data class Listing(
    var id: String = "",
    val title: String = "",
    val price: Double = 0.0,
    val category: String = "All",
    val condition: String = "Used",
    val description: String = "",
    val pickupLocation: String = "Ontario Tech (North Campus)",
    val imageUrl: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val featured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)