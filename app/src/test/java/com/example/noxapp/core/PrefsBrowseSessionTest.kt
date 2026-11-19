package com.example.noxapp.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.noxapp.listing.BrowseFilters
import com.example.noxapp.listing.PriceRange
import com.example.noxapp.listing.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned because Robolectric 4.14.1 ships no system image for this project's targetSdk.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrefsBrowseSessionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val filters = BrowseFilters(
            query = "  Calculus textbook ",
            category = "Books",
            priceRange = PriceRange.FROM_25_TO_50,
            condition = "Used",
            centerLat = 43.9483414,
            centerLng = -78.8982025,
            radiusKm = 2.5,
            sort = SortOption.NEAREST
        )
        Prefs.saveBrowseSession(context, "student-a", filters, categoriesExpanded = true)

        val restored = Prefs.browseSession(context, "student-a")
        assertEquals(filters, restored?.first)
        assertEquals(true, restored?.second)
    }

    @Test
    fun coordinatesKeepFullDoublePrecision() {
        // Stored as text because SharedPreferences has no Double and Float would round these.
        val filters = BrowseFilters(centerLat = 43.94834141234567, centerLng = -78.89820259876543)
        Prefs.saveBrowseSession(context, "student-a", filters, categoriesExpanded = false)

        val restored = Prefs.browseSession(context, "student-a")!!.first
        assertEquals(43.94834141234567, restored.centerLat!!, 0.0)
        assertEquals(-78.89820259876543, restored.centerLng!!, 0.0)
    }

    @Test
    fun unsetFiltersStayNullInsteadOfBecomingDefaults() {
        Prefs.saveBrowseSession(context, "student-a", BrowseFilters(), categoriesExpanded = false)

        val restored = Prefs.browseSession(context, "student-a")!!
        assertEquals(BrowseFilters(), restored.first)
        assertEquals(false, restored.second)
    }

    @Test
    fun anotherAccountDoesNotInheritTheStoredSearch() {
        Prefs.saveBrowseSession(context, "student-a", BrowseFilters(query = "desk"), categoriesExpanded = false)

        assertNull(Prefs.browseSession(context, "student-b"))
        assertEquals("desk", Prefs.browseSession(context, "student-a")!!.first.query)
    }

    @Test
    fun signedOutStoresAndRestoresNothing() {
        Prefs.saveBrowseSession(context, null, BrowseFilters(query = "desk"), categoriesExpanded = true)

        assertNull(Prefs.browseSession(context, null))
        assertNull(Prefs.browseSession(context, "student-a"))
    }

    @Test
    fun clearingRemovesEveryField() {
        Prefs.saveBrowseSession(
            context, "student-a", BrowseFilters(query = "desk", radiusKm = 3.0), categoriesExpanded = true
        )
        Prefs.clearBrowseSession(context)

        assertNull(Prefs.browseSession(context, "student-a"))
    }
}