package com.example.noxapp.profile

import com.example.noxapp.listing.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerListingFiltersTest {

    private val sellerId = "seller-1"
    private val otherSellerId = "seller-2"

    private val textbook = Listing(
        id = "textbook",
        title = "Calculus Textbook",
        category = "Books",
        description = "First-year notes",
        sellerId = sellerId
    )
    private val chair = Listing(
        id = "chair",
        title = "Desk Chair",
        category = "Furniture",
        description = "Comfortable for long study nights",
        sellerId = sellerId
    )
    private val headphones = Listing(
        id = "headphones",
        title = "Noise Cancelling Headphones",
        category = "Electronics",
        description = "Great for the library",
        sellerId = sellerId
    )
    private val sellerListings = listOf(textbook, chair, headphones)

    @Test
    fun sellerFilterKeepsOnlyThatSellersListings() {
        val foreign = Listing(id = "foreign", title = "Calculus Textbook", sellerId = otherSellerId)

        assertEquals(
            listOf(textbook, chair),
            SellerListingFilters.forSeller(listOf(textbook, foreign, chair), sellerId)
        )
    }

    @Test
    fun soldSplitUsesTheSoldFlagAndKeepsOrder() {
        val soldChair = chair.copy(sold = true)
        val soldHeadphones = headphones.copy(sold = true)

        val (available, sold) = listOf(soldChair, textbook, soldHeadphones).partition { !it.sold }

        assertEquals(listOf(textbook), available)
        assertEquals(listOf(soldChair, soldHeadphones), sold)
    }

    @Test
    fun unsoldIsTheDefaultSoExistingListingsStayAvailable() {
        assertFalse(textbook.sold)

        val (available, sold) = sellerListings.partition { !it.sold }

        assertEquals(sellerListings, available)
        assertTrue(sold.isEmpty())
    }

    @Test
    fun splitOfEmptyListGivesTwoEmptyTabs() {
        val (available, sold) = emptyList<Listing>().partition { !it.sold }

        assertTrue(available.isEmpty())
        assertTrue(sold.isEmpty())
    }

    @Test
    fun queryTrimsAndMatchesTitleOrDescriptionIgnoringCase() {
        assertEquals(listOf(textbook), SellerListingFilters.apply(sellerListings, "  CALCULUS  ", null))
        assertEquals(listOf(textbook), SellerListingFilters.apply(sellerListings, "first-year", null))
        assertEquals(listOf(chair), SellerListingFilters.apply(sellerListings, "STUDY", null))
    }

    @Test
    fun blankQueryMatchesEveryListing() {
        for (query in listOf("", "   ", "\t\n ")) {
            assertEquals(query, sellerListings, SellerListingFilters.apply(sellerListings, query, null))
        }
    }

    @Test
    fun queryWithNoMatchReturnsNothing() {
        assertTrue(SellerListingFilters.apply(sellerListings, "laptop", null).isEmpty())
    }

    @Test
    fun nullOrBlankCategoryMatchesAllAndOtherwiseIgnoresCase() {
        assertEquals(sellerListings, SellerListingFilters.apply(sellerListings, "", null))
        assertEquals(sellerListings, SellerListingFilters.apply(sellerListings, "", "   "))
        assertEquals(listOf(textbook), SellerListingFilters.apply(sellerListings, "", "books"))
        assertEquals(listOf(textbook), SellerListingFilters.apply(sellerListings, "", " BOOKS "))
        assertEquals(listOf(headphones), SellerListingFilters.apply(sellerListings, "", "Electronics"))
        assertTrue(SellerListingFilters.apply(sellerListings, "", "Tickets").isEmpty())
    }

    @Test
    fun combinedQueryAndCategoryMustBothMatch() {
        val artBook = textbook.copy(id = "art-book", title = "Calculus Study Art", category = "Decor")
        val listings = listOf(textbook, chair, artBook)

        assertEquals(listOf(textbook), SellerListingFilters.apply(listings, "calculus", "Books"))
        assertTrue(SellerListingFilters.apply(listings, "art", "Books").isEmpty())
        assertEquals(listOf(artBook), SellerListingFilters.apply(listings, "art", "decor"))
    }

    @Test
    fun tabAndFiltersKeepOnlyTheSellersMatchingListings() {
        val soldBook = textbook.copy(id = "sold-book", title = "Algebra Textbook", sold = true)
        val foreignBook = Listing(
            id = "foreign-book",
            title = "Calculus Textbook",
            category = "Books",
            sellerId = otherSellerId,
            sold = true
        )
        val listings = listOf(textbook, soldBook, chair, foreignBook)
        val (available, sold) = SellerListingFilters.forSeller(listings, sellerId)
            .partition { !it.sold }

        assertEquals(listOf(textbook), SellerListingFilters.apply(available, "textbook", null))
        assertEquals(listOf(soldBook), SellerListingFilters.apply(sold, "textbook", null))
        assertEquals(listOf(chair), SellerListingFilters.apply(available, "", "Furniture"))
    }

    @Test
    fun blankSellerIdMatchesNothingIncludingOtherSellers() {
        assertTrue(SellerListingFilters.forSeller(sellerListings, "").isEmpty())
        assertTrue(SellerListingFilters.forSeller(sellerListings, "   ").isEmpty())
        assertTrue(SellerListingFilters.forSeller(emptyList(), sellerId).isEmpty())
    }

    @Test
    fun matchesHandlesBlankQueryAndBlankCategory() {
        assertTrue(SellerListingFilters.matches(textbook, "", null))
        assertTrue(SellerListingFilters.matches(textbook, "  \n", "  \t "))
        assertFalse(SellerListingFilters.matches(textbook, "laptop", null))
        assertFalse(SellerListingFilters.matches(textbook, "", "Furniture"))
    }
}
