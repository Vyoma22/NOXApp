package com.example.noxapp.data

import com.example.noxapp.models.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Test

class PickupPreferenceRemovalTest {
    @Test
    fun listingRepoDoesNotExposePreferenceWrites() {
        val methods = ListingRepo::class.java.methods.map { it.name }

        assertFalse(methods.contains("saveGeoPreference"))
        assertFalse(methods.contains("clearGeoPreference"))
    }

    @Test
    fun userProfileDoesNotCarryPreferredLocationFields() {
        val fields = UserProfile::class.java.declaredFields.map { it.name }

        assertFalse(fields.contains("preferredLat"))
        assertFalse(fields.contains("preferredLng"))
        assertFalse(fields.contains("preferredRadiusKm"))
    }
}
