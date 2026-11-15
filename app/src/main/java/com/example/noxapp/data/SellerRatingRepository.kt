
package com.example.noxapp.data

import com.example.noxapp.models.SaleStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object SellerRatingRepository {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    // One rating document per completed transaction.
    private val ratingsRef by lazy {
        db.collection("sellerRatings")
    }

    private val transactionsRef by lazy {
        db.collection("saleTransactions")
    }

    fun submitRating(
        conversationId: String,
        stars: Int,
        review: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUserId =
            FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId.isNullOrBlank()) {
            onError(Exception("Please sign in first"))
            return
        }

        if (conversationId.isBlank()) {
            onError(Exception("Conversation ID is missing"))
            return
        }

        if (stars !in 1..5) {
            onError(Exception("Please select 1 to 5 stars"))
            return
        }

        val cleanReview = review.trim()

        if (cleanReview.length > 500) {
            onError(Exception("Review cannot exceed 500 characters"))
            return
        }

        val saleRef =
            transactionsRef.document(conversationId)

        val ratingRef =
            ratingsRef.document(conversationId)

        val conversationRef =
            db.collection("conversations")
                .document(conversationId)

        db.runTransaction { transaction ->

            // Read all necessary documents first.
            val sale = transaction.get(saleRef)
            val existingRating = transaction.get(ratingRef)
            val conversation = transaction.get(conversationRef)

            if (!sale.exists() || !conversation.exists()) {
                throw Exception("Purchase not found")
            }

            val buyerId =
                sale.getString("buyerId").orEmpty()

            val sellerId =
                sale.getString("sellerId").orEmpty()

            val listingId =
                sale.getString("listingId").orEmpty()

            // Read the listing before making any writes.
            val listingRef =
                db.collection("listings").document(listingId)

            val listing = transaction.get(listingRef)

            if (!listing.exists()) {
                throw Exception("Listing no longer exists")
            }

            if (listing.getString("sellerId") != sellerId) {
                throw Exception("Listing seller does not match")
            }

            if (listing.getBoolean("sold") == true) {
                throw Exception("Listing is already sold")
            }

            if (listing.getBoolean("salePending") != true) {
                throw Exception("Listing is not awaiting completion")
            }


            // Only the real buyer can rate the seller.
            if (buyerId != currentUserId) {
                throw Exception("Only the buyer can rate this seller")
            }

            if (sellerId.isBlank() || sellerId == buyerId) {
                throw Exception("Invalid seller information")
            }

            // Check that the purchase belongs to this chat.
            if (
                sale.getString("conversationId") != conversationId ||
                conversation.getString("buyerId") != buyerId ||
                conversation.getString("sellerId") != sellerId ||
                conversation.getString("listingId") != listingId
            ) {
                throw Exception("Purchase details do not match")
            }

            // Buyer must confirm the purchase first.
            if (
                sale.getString("status") !=
                SaleStatus.AWAITING_RATING
            ) {
                throw Exception(
                    "This purchase is not awaiting a rating"
                )
            }

            // Prevent multiple ratings for one transaction.
            if (existingRating.exists()) {
                throw Exception("This purchase has already been rated")
            }

            // Save the rating.
            transaction.set(
                ratingRef,
                mapOf(
                    "conversationId" to conversationId,
                    "listingId" to listingId,
                    "buyerId" to buyerId,
                    "sellerId" to sellerId,
                    "stars" to stars,
                    "review" to cleanReview,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            // Complete the transaction in the same operation.
            transaction.update(
                saleRef,
                mapOf(
                    "status" to SaleStatus.COMPLETED,
                    "completedAt" to FieldValue.serverTimestamp()
                )
            )

            // Finalize the listing after the buyer rates.
            transaction.update(
                listingRef,
                mapOf(
                    "sold" to true,
                    "salePending" to false
                )
            )

        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }
}
