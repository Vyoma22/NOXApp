package com.example.noxapp.postlisting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupLocationSelectionTest {

    private val buildings = listOf("Ontario Tech (North Campus)", "ACE", "Library")

    @Test fun matchesBuildingNameIgnoringCaseAndSpaces() {
        assertEquals(1, PickupLocationSelection.selectionFor(" ace ", buildings))
        assertEquals(2, PickupLocationSelection.selectionFor("LIBRARY", buildings))
    }

    @Test fun unknownOrBlankLocationFallsBackToCustomEntry() {
        assertEquals(buildings.size, PickupLocationSelection.selectionFor("Pinned map location", buildings))
        assertEquals(buildings.size, PickupLocationSelection.selectionFor("", buildings))
        assertEquals(buildings.size, PickupLocationSelection.selectionFor(null, buildings))
    }

    @Test fun customEntryFollowsLastBuilding() {
        val customIndex = buildings.size
        assertTrue(PickupLocationSelection.isCustom(customIndex, customIndex))
        assertFalse(PickupLocationSelection.isCustom(customIndex - 1, customIndex))
        assertFalse(PickupLocationSelection.isCustom(-1, customIndex))
    }
}
