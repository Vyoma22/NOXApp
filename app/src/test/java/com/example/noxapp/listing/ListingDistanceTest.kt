package com.example.noxapp.listing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned because Robolectric 4.14.1 ships no system image for this project's targetSdk.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListingDistanceTest {
    @Test
    fun samePointHasZeroDistance() {
        assertEquals(0.0, ListingFilters.distanceKm(43.9483414, -78.8982025, 43.9483414, -78.8982025), 0.0)
    }

    @Test
    fun oneDegreeOfLatitudeAtEquatorIsAbout110Point6Kilometers() {
        assertEquals(110.574, ListingFilters.distanceKm(0.0, 0.0, 1.0, 0.0), 0.01)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquatorIsAbout111Point3Kilometers() {
        assertEquals(111.319, ListingFilters.distanceKm(0.0, 0.0, 0.0, 1.0), 0.01)
    }

    @Test
    fun distanceIsSymmetric() {
        val forward = ListingFilters.distanceKm(43.9483414, -78.8982025, 43.9430812, -78.8967816)
        val reverse = ListingFilters.distanceKm(43.9430812, -78.8967816, 43.9483414, -78.8982025)

        assertTrue(forward > 0.0)
        // The platform returns Float metres, so the two directions can differ by a rounding step.
        assertEquals(forward, reverse, 1e-6)
    }
}
