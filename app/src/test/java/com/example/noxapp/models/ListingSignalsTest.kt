package com.example.noxapp.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingSignalsTest {

    private fun listing(
        price: Double = 40.0,
        previousPrice: Double? = null,
        viewCount: Long = 0L
    ) = Listing(price = price, previousPrice = previousPrice, viewCount = viewCount)

    private fun views(count: Long) = Listing(viewCount = count)

    @Test
    fun listingBelowTheThresholdIsNotPopular() {
        assertFalse(ListingSignals.isPopular(views(0)))
        assertFalse(ListingSignals.isPopular(views(ListingSignals.POPULAR_VIEW_THRESHOLD - 1)))
        assertFalse(ListingSignals.isPopular(Listing()))
    }

    @Test
    fun listingAtOrAboveTheThresholdIsPopular() {
        assertTrue(ListingSignals.isPopular(views(ListingSignals.POPULAR_VIEW_THRESHOLD)))
        assertTrue(ListingSignals.isPopular(views(ListingSignals.POPULAR_VIEW_THRESHOLD + 1)))
        assertEquals(10L, ListingSignals.POPULAR_VIEW_THRESHOLD)
    }

    @Test
    fun missingPreviousPriceHasNoDrop() {
        assertNull(ListingSignals.priceDropAmount(listing(price = 40.0, previousPrice = null)))
        assertNull(ListingSignals.priceDropAmount(Listing()))
    }

    @Test
    fun unchangedOrRaisedPriceHasNoDrop() {
        assertNull(ListingSignals.priceDropAmount(listing(price = 40.0, previousPrice = 40.0)))
        assertNull(ListingSignals.priceDropAmount(listing(price = 40.0, previousPrice = 25.0)))
        assertNull(ListingSignals.priceDropAmount(listing(price = 0.0, previousPrice = 0.0)))
    }

    @Test
    fun loweredPriceReportsWholeDollars() {
        assertEquals(15L, ListingSignals.priceDropAmount(listing(price = 35.0, previousPrice = 50.0)))
        assertEquals(1L, ListingSignals.priceDropAmount(listing(price = 0.0, previousPrice = 1.0)))
        assertEquals(20L, ListingSignals.priceDropAmount(listing(price = 25.0, previousPrice = 45.0)))
    }

    @Test
    fun fractionalPricesReportWholeDollarsWithoutOverstating() {
        // Cards truncate cents too, so a partial dollar rounds down and the pill never claims
        // more of a saving than the seller made.
        assertEquals(14L, ListingSignals.priceDropAmount(listing(price = 35.5, previousPrice = 50.0)))
        assertEquals(9L, ListingSignals.priceDropAmount(listing(price = 40.99, previousPrice = 50.0)))
        assertEquals(10L, ListingSignals.priceDropAmount(listing(price = 39.99, previousPrice = 49.99)))
    }

    @Test
    fun subDollarDropHasNoBadge() {
        assertNull(ListingSignals.priceDropAmount(listing(price = 49.5, previousPrice = 50.0)))
        assertNull(ListingSignals.priceDropAmount(listing(price = 20.0, previousPrice = 20.5)))
        assertNull(ListingSignals.priceDropAmount(listing(price = 20.0, previousPrice = 20.99)))
    }

    @Test
    fun nonFinitePricesHaveNoDrop() {
        assertNull(
            ListingSignals.priceDropAmount(
                listing(price = 40.0, previousPrice = Double.POSITIVE_INFINITY)
            )
        )
        assertNull(
            ListingSignals.priceDropAmount(
                listing(price = Double.POSITIVE_INFINITY, previousPrice = 40.0)
            )
        )
    }

    @Test
    fun ownerSeesStoredCountAndBuyerSeesTheOptimisticBump() {
        assertEquals(7L, ListingSignals.displayedViewCount(views(7), isOwner = true))
        assertEquals(8L, ListingSignals.displayedViewCount(views(7), isOwner = false))
        assertEquals(0L, ListingSignals.displayedViewCount(views(0), isOwner = true))
        assertEquals(1L, ListingSignals.displayedViewCount(views(0), isOwner = false))
    }
}
