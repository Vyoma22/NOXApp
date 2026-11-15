
package com.example.noxapp.models

import com.google.firebase.Timestamp

// The possible stages of a buyer-seller exchange.
object SaleStatus {
    const val ACTIVE = "active"
    const val PENDING_BUYER = "pending_buyer"
    const val AWAITING_RATING = "awaiting_rating"
    const val COMPLETED = "completed"
    const val ISSUE_REPORTED = "issue_reported"
}

// Represents one purchase transaction.
data class SaleTransaction(
    val id: String = "",
    val conversationId: String = "",
    val listingId: String = "",
    val buyerId: String = "",
    val sellerId: String = "",

    val status: String = SaleStatus.ACTIVE,

    val sellerConfirmedAt: Timestamp? = null,
    val buyerConfirmedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
) {

    // Only the seller can initiate sale confirmation.
    fun canSellerConfirm(userId: String): Boolean {
        return userId == sellerId &&
                status == SaleStatus.ACTIVE
    }

    // Only the buyer can confirm receiving the item.
    fun canBuyerConfirm(userId: String): Boolean {
        return userId == buyerId &&
                status == SaleStatus.PENDING_BUYER
    }

    // Only the buyer can submit the required rating.
    fun canBuyerRate(userId: String): Boolean {
        return userId == buyerId &&
                status == SaleStatus.AWAITING_RATING
    }
}
