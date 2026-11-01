package com.example.noxapp.data

import com.example.noxapp.models.Listing
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusLocationsTest {
    @Test
    fun listingCoordinatesDefaultToZeroForExistingDocuments() {
        val listing = Listing()

        assertEquals(0.0, listing.latitude, 0.0)
        assertEquals(0.0, listing.longitude, 0.0)
    }

    @Test
    fun nonZeroCoordinatesAreReturnedUnchangedInsteadOfCampusFallback() {
        for (pickupLocation in listOf("Durham College", "Ontario Tech")) {
            val listing = Listing(
                pickupLocation = pickupLocation,
                latitude = -33.865143,
                longitude = 151.2099
            )

            assertEquals(-33.865143 to 151.2099, CampusLocations.resolve(listing))
        }
    }

    @Test
    fun zeroCoordinatesResolveDurhamSubstringIgnoringCase() {
        assertEquals(43.9430812 to -78.8967816, CampusLocations.DURHAM_COLLEGE)
        for (pickupLocation in listOf("Durham College", "Meet at DURHAM north campus", "durham")) {
            assertEquals(
                pickupLocation,
                43.9430812 to -78.8967816,
                CampusLocations.resolve(Listing(pickupLocation = pickupLocation))
            )
        }
    }

    @Test
    fun zeroCoordinatesResolveAnyOtherLocationToOntarioTech() {
        assertEquals(43.9483414 to -78.8982025, CampusLocations.ONTARIO_TECH)
        for (pickupLocation in listOf("Ontario Tech (North Campus)", "Library", "", "Unknown campus")) {
            assertEquals(
                pickupLocation,
                43.9483414 to -78.8982025,
                CampusLocations.resolve(Listing(pickupLocation = pickupLocation))
            )
        }
    }

    @Test
    fun singleZeroCoordinateTriggersCompleteCampusFallback() {
        for ((latitude, longitude) in listOf(0.0 to -79.0, 44.0 to 0.0)) {
            assertEquals(
                43.9430812 to -78.8967816,
                CampusLocations.resolve(Listing(
                    pickupLocation = "Durham College",
                    latitude = latitude,
                    longitude = longitude
                ))
            )
            assertEquals(
                43.9483414 to -78.8982025,
                CampusLocations.resolve(Listing(
                    pickupLocation = "Unknown",
                    latitude = latitude,
                    longitude = longitude
                ))
            )
        }
    }
}
