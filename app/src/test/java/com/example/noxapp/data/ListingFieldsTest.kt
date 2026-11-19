package com.example.noxapp.data

import com.example.noxapp.models.Listing
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A single malformed listing must not take the whole feed down with it.
 *
 * These are the documents the tightened security rules now reject on write, plus the ones written
 * before those rules existed. Every case is a plain map, so no Firestore connection is needed.
 */
class ListingFieldsTest {

    private val goodDocument = mapOf<String, Any?>(
        "title" to "Desk lamp",
        "price" to 12.5,
        "category" to "Home",
        "condition" to "Like New",
        "description" to "Barely used",
        "pickupLocation" to "Ontario Tech (North Campus)",
        "imageUrls" to listOf("https://example.test/a.jpg"),
        "sellerId" to "seller-1",
        "sellerName" to "Ada",
        "featured" to true,
        "sold" to false,
        "salePending" to false,
        "createdAt" to Timestamp(1_700_000_000L, 0),
        "latitude" to 43.945,
        "longitude" to -78.896,
        "viewCount" to 7L,
        "previousPrice" to 20.0
    )

    /** Positive control: the safe casts must not quietly discard a well-formed document. */
    @Test fun aWellFormedDocumentKeepsEveryField() {
        val listing = ListingFields.toListing("listing-1", goodDocument)

        assertEquals("listing-1", listing.id)
        assertEquals("Desk lamp", listing.title)
        assertEquals(12.5, listing.price, 0.0)
        assertEquals("Home", listing.category)
        assertEquals("Like New", listing.condition)
        assertEquals("Barely used", listing.description)
        assertEquals("Ontario Tech (North Campus)", listing.pickupLocation)
        assertEquals(listOf("https://example.test/a.jpg"), listing.imageUrls)
        assertEquals("seller-1", listing.sellerId)
        assertEquals("Ada", listing.sellerName)
        assertTrue(listing.featured)
        assertFalse(listing.sold)
        assertFalse(listing.salePending)
        assertEquals(1_700_000_000_000L, listing.createdAt)
        assertEquals(43.945, listing.latitude, 0.0)
        assertEquals(-78.896, listing.longitude, 0.0)
        assertEquals(7L, listing.viewCount)
        assertEquals(20.0, listing.previousPrice!!, 0.0)
    }

    /** The crash the finding describes: every field holds the wrong type at once. */
    @Test fun aDocumentWithEveryFieldMalformedStillBecomesAListing() {
        val defaults = Listing()
        val garbage = mapOf<String, Any?>(
            "title" to 42,
            "price" to "12.50",
            "category" to listOf("Home"),
            "condition" to 3,
            "description" to true,
            "pickupLocation" to 43.9,
            "imageUrls" to "https://example.test/a.jpg",
            "sellerId" to 1,
            "sellerName" to listOf("Ada"),
            "featured" to "yes",
            "sold" to "no",
            "salePending" to 1,
            "createdAt" to "yesterday",
            "latitude" to "north",
            "longitude" to false,
            "viewCount" to "many",
            "previousPrice" to "20"
        )

        val listing = ListingFields.toListing("listing-1", garbage)

        assertEquals("listing-1", listing.id)
        assertEquals(defaults.title, listing.title)
        assertEquals(defaults.price, listing.price, 0.0)
        assertEquals(defaults.category, listing.category)
        assertEquals(defaults.condition, listing.condition)
        assertEquals(defaults.description, listing.description)
        assertEquals(defaults.pickupLocation, listing.pickupLocation)
        assertEquals(defaults.imageUrls, listing.imageUrls)
        assertEquals(defaults.sellerId, listing.sellerId)
        assertEquals(defaults.sellerName, listing.sellerName)
        assertEquals(defaults.featured, listing.featured)
        assertEquals(defaults.sold, listing.sold)
        assertEquals(defaults.salePending, listing.salePending)
        assertEquals(defaults.latitude, listing.latitude, 0.0)
        assertEquals(defaults.longitude, listing.longitude, 0.0)
        assertEquals(defaults.viewCount, listing.viewCount)
        assertNull(listing.previousPrice)
    }

    @Test fun aStringPriceFallsBackWithoutLosingTheRestOfTheListing() {
        val listing = ListingFields.toListing(
            "listing-1",
            goodDocument + mapOf("price" to "12.50")
        )

        assertEquals(Listing().price, listing.price, 0.0)
        assertEquals("Desk lamp", listing.title)
        assertEquals("seller-1", listing.sellerId)
    }

    @Test fun integersAndFloatsBothReadAsNumbers() {
        val whole = ListingFields.toListing(
            "listing-1",
            goodDocument + mapOf("price" to 12L, "viewCount" to 3, "latitude" to 44)
        )

        assertEquals(12.0, whole.price, 0.0)
        assertEquals(3L, whole.viewCount)
        assertEquals(44.0, whole.latitude, 0.0)
    }

    @Test fun nonStringEntriesAreDroppedFromTheImageList() {
        val listing = ListingFields.toListing(
            "listing-1",
            goodDocument + mapOf("imageUrls" to listOf("https://example.test/a.jpg", 7, null))
        )

        assertEquals(listOf("https://example.test/a.jpg"), listing.imageUrls)
    }

    @Test fun aMapWhereTheImageListBelongsFallsBackToTheLegacySingleImage() {
        val listing = ListingFields.toListing(
            "listing-1",
            goodDocument + mapOf(
                "imageUrls" to mapOf("0" to "https://example.test/a.jpg"),
                "imageUrl" to "https://example.test/legacy.jpg"
            )
        )

        assertEquals(listOf("https://example.test/legacy.jpg"), listing.imageUrls)
    }

    /** Documents written before the model merge used name/location/imageUrl. */
    @Test fun legacyFieldNamesAreStillRead() {
        val listing = ListingFields.toListing(
            "listing-1",
            mapOf(
                "name" to "Old desk",
                "location" to "Simcoe Hall",
                "imageUrl" to "https://example.test/legacy.jpg"
            )
        )

        assertEquals("Old desk", listing.title)
        assertEquals("Simcoe Hall", listing.pickupLocation)
        assertEquals(listOf("https://example.test/legacy.jpg"), listing.imageUrls)
    }

    @Test fun anEmptyDocumentUsesTheModelDefaults() {
        val defaults = Listing()
        val listing = ListingFields.toListing("listing-1", emptyMap())

        assertEquals("listing-1", listing.id)
        assertEquals(defaults.title, listing.title)
        assertEquals(defaults.category, listing.category)
        assertEquals(defaults.condition, listing.condition)
        assertEquals(defaults.pickupLocation, listing.pickupLocation)
        assertEquals(defaults.imageUrls, listing.imageUrls)
        assertFalse(listing.sold)
        assertFalse(listing.salePending)
        assertEquals(0L, listing.viewCount)
        assertNull(listing.previousPrice)
    }

    /** A garbage createdAt must not become 0, which would sort the listing to the bottom. */
    @Test fun aGarbageCreatedAtFallsBackToNow() {
        val before = System.currentTimeMillis()
        val listing = ListingFields.toListing(
            "listing-1",
            goodDocument + mapOf("createdAt" to "yesterday")
        )

        assertTrue(listing.createdAt >= before)
        assertTrue(listing.createdAt <= System.currentTimeMillis())
    }
}