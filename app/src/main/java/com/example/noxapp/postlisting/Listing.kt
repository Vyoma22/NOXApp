package com.example.noxapp.postlisting

data class Listing(
    val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val location: String = "Ontario Tech (North Campus)",
    val condition: String = "",
    val imageUrls: List<String>,
    val featured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
