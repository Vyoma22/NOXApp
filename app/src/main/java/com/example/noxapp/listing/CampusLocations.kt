package com.example.noxapp.listing

import com.example.noxapp.listing.Listing

object CampusLocations {
    val ONTARIO_TECH: Pair<Double, Double> = 43.9483414 to -78.8982025
    val DURHAM_COLLEGE: Pair<Double, Double> = 43.9430812 to -78.8967816

    fun resolve(listing: Listing): Pair<Double, Double> =
        if (listing.latitude != 0.0 && listing.longitude != 0.0) {
            listing.latitude to listing.longitude
        } else {
            forSchool(listing.pickupLocation)
        }

    /** Default map centre for a student's school, so Durham users do not land across the street. */
    fun forSchool(school: String?): Pair<Double, Double> =
        if (school?.contains("durham", ignoreCase = true) == true) DURHAM_COLLEGE else ONTARIO_TECH
}
