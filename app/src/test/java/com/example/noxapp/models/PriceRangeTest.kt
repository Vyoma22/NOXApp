package com.example.noxapp.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceRangeTest {
    @Test
    fun freeIsTheFirstEntryAndUsesAZeroWidthBand() {
        assertEquals(PriceRange.FREE, PriceRange.entries.first())
        assertEquals("Free", PriceRange.FREE.label)
        assertEquals(0.0, PriceRange.FREE.minInclusive, 0.0)
        assertEquals(0.0, PriceRange.FREE.maxExclusive, 0.0)
    }

    @Test
    fun freeBandMatchesZeroAndNegativePricesOnly() {
        assertTrue(PriceRange.FREE.contains(0.0))
        assertTrue(PriceRange.FREE.contains(-0.01))
        assertTrue(PriceRange.FREE.contains(-5.0))
        assertFalse(PriceRange.FREE.contains(0.01))
        assertFalse(PriceRange.FREE.contains(10.0))
        assertFalse(PriceRange.FREE.contains(Double.MAX_VALUE))
    }

    @Test
    fun everyBandIncludesItsMinimumAndExcludesItsMaximum() {
        for (range in PriceRange.entries) {
            assertTrue(range.name, range.contains(range.minInclusive))
            if (range.minInclusive != range.maxExclusive) {
                assertFalse(range.name, range.contains(range.maxExclusive))
            }
        }
    }

    @Test
    fun everyBandedRangeExcludesPricesJustOutsideItsBounds() {
        for (range in PriceRange.entries.filter { it.minInclusive != it.maxExclusive }) {
            assertFalse(range.name, range.contains(Math.nextDown(range.minInclusive)))
            assertTrue(range.name, range.contains(Math.nextDown(range.maxExclusive)))
        }
    }

    @Test
    fun freeOverlapsUnder25OnlyAtZeroAndEveryOtherPriceMatchesOneBand() {
        val cases = listOf(
            -0.01 to listOf(PriceRange.FREE),
            0.0 to listOf(PriceRange.FREE, PriceRange.UNDER_25),
            10.0 to listOf(PriceRange.UNDER_25),
            24.99 to listOf(PriceRange.UNDER_25),
            25.0 to listOf(PriceRange.FROM_25_TO_50),
            49.99 to listOf(PriceRange.FROM_25_TO_50),
            50.0 to listOf(PriceRange.FROM_50_TO_100),
            99.99 to listOf(PriceRange.FROM_50_TO_100),
            100.0 to listOf(PriceRange.OVER_100),
            1000.0 to listOf(PriceRange.OVER_100),
            Double.MAX_VALUE to emptyList<PriceRange>()
        )

        for ((price, expectedRanges) in cases) {
            assertEquals("Price $price", expectedRanges, PriceRange.entries.filter { it.contains(price) })
        }
    }

    @Test
    fun negativePricesAreTreatedAsFreeRatherThanUnmatched() {
        for (price in listOf(-0.01, -1.0, -100.0, Math.nextDown(0.0))) {
            assertEquals("Price $price", listOf(PriceRange.FREE), PriceRange.entries.filter { it.contains(price) })
        }
    }
}
