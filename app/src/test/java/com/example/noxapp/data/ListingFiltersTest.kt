package com.example.noxapp.data

import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.models.PriceRange
import com.example.noxapp.models.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingFiltersTest {
    private val book = Listing(
        id = "book",
        title = "Calculus Textbook",
        price = 25.0,
        category = "Books",
        condition = "Like New",
        description = "Notes for first-year engineering",
        latitude = 43.9483414,
        longitude = -78.8982025,
        createdAt = 100L
    )

    @Test
    fun emptyFiltersReturnAllListingsNewestFirstWithoutChangingInput() {
        val newest = book.copy(id = "newest", createdAt = 300L)
        val middle = book.copy(id = "middle", createdAt = 200L)
        val listings = mutableListOf(book, newest, middle)

        assertEquals(listOf(newest, middle, book), ListingFilters.apply(listings, BrowseFilters()))
        assertEquals(listOf(book, newest, middle), listings)
        assertTrue(ListingFilters.apply(emptyList(), BrowseFilters()).isEmpty())
    }

    @Test
    fun queryTrimsAndMatchesTitleOrDescriptionIgnoringCase() {
        for (query in listOf(" \tCALCULUS\n ", "  FIRST-YEAR  ")) {
            assertEquals(
                query,
                listOf(book),
                ListingFilters.apply(listOf(book), BrowseFilters(query = query))
            )
        }
    }

    @Test
    fun emptyOrWhitespaceQueryMatchesEverything() {
        for (query in listOf("", " \t\n ")) {
            assertTrue(ListingFilters.matches(book, BrowseFilters(query = query)))
        }
    }

    @Test
    fun nonMatchingQueryReturnsEmpty() {
        assertTrue(ListingFilters.apply(listOf(book), BrowseFilters(query = "laptop")).isEmpty())
    }

    @Test
    fun categoryNullMatchesAllAndNonNullUsesCaseInsensitiveEquality() {
        val chair = book.copy(id = "chair", category = "Furniture")
        val listings = listOf(book, chair)

        assertEquals(listings, ListingFilters.apply(listings, BrowseFilters(category = null)))
        assertEquals(listOf(book), ListingFilters.apply(listings, BrowseFilters(category = "books")))
        assertTrue(ListingFilters.apply(listings, BrowseFilters(category = "Electronics")).isEmpty())
        assertFalse(ListingFilters.matches(book, BrowseFilters(category = "book")))
    }

    @Test
    fun eachPriceBoundaryMatchesTheExpectedBrackets() {
        val cases = listOf(
            0.0 to listOf(PriceRange.FREE, PriceRange.UNDER_25),
            24.99 to listOf(PriceRange.UNDER_25),
            25.0 to listOf(PriceRange.FROM_25_TO_50),
            49.99 to listOf(PriceRange.FROM_25_TO_50),
            50.0 to listOf(PriceRange.FROM_50_TO_100),
            99.99 to listOf(PriceRange.FROM_50_TO_100),
            100.0 to listOf(PriceRange.OVER_100),
            1000.0 to listOf(PriceRange.OVER_100)
        )

        for ((price, expectedRanges) in cases) {
            val matchedRanges = PriceRange.entries.filter { range ->
                ListingFilters.matches(book.copy(price = price), BrowseFilters(priceRange = range))
            }
            assertEquals("Price $price", expectedRanges, matchedRanges)
        }
    }

    @Test
    fun priceBracketsExcludeValuesOutsideTheirBounds() {
        // Negative prices count as free; the largest Double falls outside OVER_100's exclusive max.
        assertEquals(
            listOf(PriceRange.FREE),
            PriceRange.entries.filter { range ->
                ListingFilters.matches(book.copy(price = -0.01), BrowseFilters(priceRange = range))
            }
        )
        assertTrue(PriceRange.entries.none { range ->
            ListingFilters.matches(book.copy(price = Double.MAX_VALUE), BrowseFilters(priceRange = range))
        })
    }

    @Test
    fun freePriceRangeMatchesOnlyNonPositivePrices() {
        assertTrue(ListingFilters.matches(book.copy(price = 0.0), BrowseFilters(priceRange = PriceRange.FREE)))
        assertTrue(ListingFilters.matches(book.copy(price = -1.0), BrowseFilters(priceRange = PriceRange.FREE)))
        assertFalse(ListingFilters.matches(book.copy(price = 0.01), BrowseFilters(priceRange = PriceRange.FREE)))
        assertFalse(ListingFilters.matches(book, BrowseFilters(priceRange = PriceRange.FREE)))
    }

    @Test
    fun freeFilterCombinesWithQueryAndCategory() {
        val freeListing = book.copy(id = "free", price = 0.0)
        val filters = BrowseFilters(query = "  CALCULUS  ", category = "books", priceRange = PriceRange.FREE)

        assertTrue(ListingFilters.matches(freeListing, filters))
        assertFalse(ListingFilters.matches(freeListing, filters.copy(query = "laptop")))
        assertFalse(ListingFilters.matches(freeListing, filters.copy(category = "Electronics")))
        assertFalse(ListingFilters.matches(freeListing.copy(price = 10.0), filters))
    }

    @Test
    fun under25StillIncludesFreeAndPaidListingsUnder25() {
        assertTrue(ListingFilters.matches(book.copy(price = 0.0), BrowseFilters(priceRange = PriceRange.UNDER_25)))
        assertTrue(ListingFilters.matches(book.copy(price = 10.0), BrowseFilters(priceRange = PriceRange.UNDER_25)))
        assertFalse(ListingFilters.matches(book.copy(price = 25.0), BrowseFilters(priceRange = PriceRange.UNDER_25)))
    }

    @Test
    fun nullPriceRangeDoesNotRestrictPrices() {
        assertTrue(ListingFilters.matches(book.copy(price = -0.01), BrowseFilters(priceRange = null)))
        assertTrue(ListingFilters.matches(book.copy(price = Double.MAX_VALUE), BrowseFilters(priceRange = null)))
    }

    @Test
    fun conditionNullMatchesAllAndNonNullUsesCaseInsensitiveEquality() {
        val used = book.copy(id = "used", condition = "Used")
        val listings = listOf(book, used)

        assertEquals(listings, ListingFilters.apply(listings, BrowseFilters(condition = null)))
        assertEquals(listOf(book), ListingFilters.apply(listings, BrowseFilters(condition = "like new")))
        assertTrue(ListingFilters.apply(listings, BrowseFilters(condition = "Good")).isEmpty())
        assertFalse(ListingFilters.matches(book, BrowseFilters(condition = "New")))
    }

    @Test
    fun listingExactlyAtRadiusIsIncluded() {
        val listing = book.copy(latitude = 2.0, longitude = 1.0)
        val radius = ListingFilters.haversineKm(1.0, 1.0, 2.0, 1.0)

        assertTrue(ListingFilters.matches(listing, BrowseFilters(
            centerLat = 1.0,
            centerLng = 1.0,
            radiusKm = radius
        )))
    }

    @Test
    fun listingJustOutsideRadiusIsExcluded() {
        val listing = book.copy(latitude = 2.0, longitude = 1.0)
        val radius = ListingFilters.haversineKm(1.0, 1.0, 2.0, 1.0)

        assertFalse(ListingFilters.matches(listing, BrowseFilters(
            centerLat = 1.0,
            centerLng = 1.0,
            radiusKm = Math.nextDown(radius)
        )))
    }

    @Test
    fun anyMissingDistanceFieldDisablesDistanceFilter() {
        val filters = BrowseFilters(centerLat = 1.0, centerLng = 1.0, radiusKm = 0.1)
        assertFalse(ListingFilters.matches(book, filters))

        for (incomplete in listOf(
            filters.copy(centerLat = null),
            filters.copy(centerLng = null),
            filters.copy(centerLat = null, centerLng = null),
            filters.copy(radiusKm = null)
        )) {
            assertTrue(incomplete.toString(), ListingFilters.matches(book, incomplete))
        }
    }

    @Test
    fun zeroOrNegativeRadiusDisablesDistanceFilter() {
        for (radius in listOf(0.0, -0.1, -10.0)) {
            assertTrue(ListingFilters.matches(book, BrowseFilters(
                centerLat = 1.0,
                centerLng = 1.0,
                radiusKm = radius
            )))
        }
    }

    @Test
    fun zeroCenterCoordinatesStillApplyDistanceFilter() {
        assertFalse(ListingFilters.matches(book, BrowseFilters(
            centerLat = 0.0,
            centerLng = 0.0,
            radiusKm = 1.0
        )))
    }

    @Test
    fun distanceUsesResolvedCampusCoordinates() {
        val durham = Listing(pickupLocation = "DURHAM College")
        val ontario = Listing(pickupLocation = "Ontario Tech")
        val filters = BrowseFilters(
            centerLat = 43.9430812,
            centerLng = -78.8967816,
            radiusKm = 0.001
        )

        assertTrue(ListingFilters.matches(durham, filters))
        assertFalse(ListingFilters.matches(ontario, filters))
    }

    @Test
    fun combinedFiltersIntersectAndSortOnlyMatchingListings() {
        val newerBook = book.copy(id = "newer", createdAt = 200L)
        val listings = listOf(
            book,
            book.copy(id = "wrongCategory", category = "Electronics"),
            book.copy(id = "wrongPrice", price = 50.0),
            book.copy(id = "wrongQuery", title = "Algebra Workbook"),
            book.copy(id = "wrongCondition", condition = "Used"),
            book.copy(id = "tooFar", latitude = 44.9483414),
            newerBook
        )
        val filters = BrowseFilters(
            query = "  CALCULUS  ",
            category = "books",
            priceRange = PriceRange.FROM_25_TO_50,
            condition = "like new",
            centerLat = 43.9483414,
            centerLng = -78.8982025,
            radiusKm = 1.0
        )

        assertEquals(listOf(newerBook, book), ListingFilters.apply(listings, filters))
    }

    @Test
    fun sortOptionOrdersResultsByPriceWithoutLosingNewestDefault() {
        val cheap = book.copy(id = "cheap", price = 5.0, createdAt = 10L)
        val mid = book.copy(id = "mid", price = 50.0, createdAt = 20L)
        val pricey = book.copy(id = "pricey", price = 500.0, createdAt = 30L)
        val listings = listOf(mid, pricey, cheap)

        assertEquals(
            listOf(cheap, mid, pricey),
            ListingFilters.apply(listings, BrowseFilters(sort = SortOption.PRICE_LOW))
        )
        assertEquals(
            listOf(pricey, mid, cheap),
            ListingFilters.apply(listings, BrowseFilters(sort = SortOption.PRICE_HIGH))
        )
        assertEquals(
            listOf(pricey, mid, cheap),
            ListingFilters.apply(listings, BrowseFilters(sort = SortOption.NEWEST))
        )
    }

    @Test
    fun nearestSortUsesTheFilterCentreAndFallsBackToNewestWithoutOne() {
        val centerLat = 43.9483414
        val centerLng = -78.8982025
        val near = book.copy(id = "near", latitude = centerLat + 0.001, longitude = centerLng, createdAt = 1L)
        val far = book.copy(id = "far", latitude = centerLat + 0.1, longitude = centerLng, createdAt = 2L)
        val middle = book.copy(id = "middle", latitude = centerLat + 0.01, longitude = centerLng, createdAt = 3L)
        val listings = listOf(far, middle, near)
        val filters = BrowseFilters(centerLat = centerLat, centerLng = centerLng, sort = SortOption.NEAREST)

        assertEquals(listOf(near, middle, far), ListingFilters.apply(listings, filters))
        assertEquals(
            listOf(middle, far, near),
            ListingFilters.apply(listings, BrowseFilters(sort = SortOption.NEAREST))
        )
    }
}
