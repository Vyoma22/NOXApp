package com.example.noxapp.data

import com.example.noxapp.models.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingReportTest {

    // Mirrors listing_report_subject_format and listing_report_body_format in strings.xml.
    private val report = ListingReport(
        subjectFormat = "Report listing: %1\$s (%2\$s)",
        bodyFormat = "Listing ID: %1\$s\n" +
            "Title: %2\$s\n" +
            "Price: \$%3\$s\n" +
            "Category: %4\$s\n" +
            "Condition: %5\$s\n" +
            "Seller ID: %6\$s\n" +
            "Seller name: %7\$s\n" +
            "Pickup location: %8\$s\n" +
            "\n" +
            "Reason for report:\n",
    )

    private val listing = Listing(
        id = "listing-42",
        title = "Desk lamp",
        price = 25.0,
        category = "Furniture",
        condition = "Used",
        pickupLocation = "SIRC",
        sellerId = "uid-7",
        sellerName = "Avery Chen",
    )

    @Test
    fun subjectNamesTheListingAndItsId() {
        assertEquals("Report listing: Desk lamp (listing-42)", report.subject(listing))
    }

    @Test
    fun bodyCarriesEveryListingField() {
        val body = report.body(listing)

        assertTrue(body.contains("Listing ID: listing-42"))
        assertTrue(body.contains("Title: Desk lamp"))
        assertTrue(body.contains("Price: \$25"))
        assertTrue(body.contains("Category: Furniture"))
        assertTrue(body.contains("Condition: Used"))
        assertTrue(body.contains("Seller ID: uid-7"))
        assertTrue(body.contains("Seller name: Avery Chen"))
        assertTrue(body.contains("Pickup location: SIRC"))
    }

    @Test
    fun bodyLeavesABlankReasonLineForTheBuyer() {
        assertTrue(report.body(listing).contains("Reason for report:"))
    }

    @Test
    fun blankSellerNameFallsBackToAClearLabelInsteadOfAnEmptyLine() {
        val body = report.body(listing.copy(sellerId = "", sellerName = ""))

        assertTrue(body.contains("Seller name: ${ListingReport.UNKNOWN_SELLER}"))
    }

    @Test
    fun missingSellerStillProducesACompleteReport() {
        val body = report.body(Listing(id = "listing-43", title = "Bike", price = 0.0))

        assertTrue(body.contains("Listing ID: listing-43"))
        assertTrue(body.contains("Seller name: ${ListingReport.UNKNOWN_SELLER}"))
        assertTrue(body.contains("Reason for report:"))
    }
}
