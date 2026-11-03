package com.example.noxapp.data

import com.example.noxapp.models.Listing
import org.junit.Assert.assertEquals
import org.junit.Test

class ListingLocationTest {
    @Test
    fun storedCoordinatesAreUsedForThePin() {
        val listing = Listing(
            title = "Desk lamp",
            pickupLocation = "Durham College",
            latitude = 43.865143,
            longitude = -78.2099,
        )

        val location = ListingLocation.resolve(listing)

        assertEquals(43.865143, location.latitude, 0.0)
        assertEquals(-78.2099, location.longitude, 0.0)
    }

    @Test
    fun missingCoordinatesFallBackToTheCampusMatchingThePickupName() {
        val durham = ListingLocation.resolve(Listing(pickupLocation = "Durham College"))
        assertEquals(43.9430812, durham.latitude, 0.0)
        assertEquals(-78.8967816, durham.longitude, 0.0)

        val ontarioTech = ListingLocation.resolve(Listing(pickupLocation = "Library"))
        assertEquals(43.9483414, ontarioTech.latitude, 0.0)
        assertEquals(-78.8982025, ontarioTech.longitude, 0.0)
    }

    @Test
    fun singleZeroCoordinateIsTreatedAsAMissingPin() {
        val location = ListingLocation.resolve(
            Listing(pickupLocation = "Durham College", latitude = 0.0, longitude = -79.0)
        )

        assertEquals(43.9430812, location.latitude, 0.0)
        assertEquals(-78.8967816, location.longitude, 0.0)
    }

    @Test
    fun listingPickupNameAndTitleBecomeTheMapLabels() {
        val location = ListingLocation.resolve(
            Listing(title = "Organic chemistry textbook", pickupLocation = "SIRC", latitude = 44.0, longitude = -79.0)
        )

        assertEquals("SIRC", location.label)
        assertEquals("Organic chemistry textbook", location.title)
    }
}
