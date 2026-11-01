package com.example.noxapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineTest {
    @Test
    fun samePointHasZeroDistance() {
        assertEquals(0.0, ListingFilters.haversineKm(43.9483414, -78.8982025, 43.9483414, -78.8982025), 0.0)
    }

    @Test
    fun oneDegreeOfLatitudeAtEquatorIsAbout111Kilometers() {
        assertEquals(111.19, ListingFilters.haversineKm(0.0, 0.0, 1.0, 0.0), 0.5)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquatorIsAbout111Kilometers() {
        assertEquals(111.19, ListingFilters.haversineKm(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test
    fun distanceIsSymmetric() {
        val forward = ListingFilters.haversineKm(43.9483414, -78.8982025, 43.9430812, -78.8967816)
        val reverse = ListingFilters.haversineKm(43.9430812, -78.8967816, 43.9483414, -78.8982025)

        assertTrue(forward > 0.0)
        assertEquals(forward, reverse, 1e-9)
    }

    @Test
    fun antipodalPointsRemainFiniteDespiteFloatingPointRounding() {
        for (latitude in listOf(-89.9, -78.0, -45.0, -23.456, 0.0, 23.456, 45.0, 78.0, 89.9)) {
            val distance = ListingFilters.haversineKm(latitude, 17.0, -latitude, -163.0)

            assertTrue("Latitude $latitude", distance.isFinite())
            assertEquals("Latitude $latitude", 20015.086796020572, distance, 0.001)
        }
    }

    @Test
    fun nearAntipodalPointsRemainFinite() {
        val distance = ListingFilters.haversineKm(43.9483414, -78.8982025, -43.9483413, 101.1017974)

        assertTrue(distance.isFinite())
        assertEquals(20015.086796020572, distance, 0.001)
    }
}
