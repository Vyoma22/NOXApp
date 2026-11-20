package com.example.noxapp.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class SellerRatingRecord(
    val conversationId: String = "",
    val listingId: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val stars: Int = 0,
    val review: String = ""
)

object SellerRatingRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val ratingsRef by lazy { db.collection("sellerRatings") }
    private val transactionsRef by lazy { db.collection("saleTransactions") }

    fun listenToRating(
        conversationId: String,
        onUpdate: (SellerRatingRecord?) -> Unit
    ): ListenerRegistration? {
        if (conversationId.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            onUpdate(null)
            return null
        }
        return ratingsRef.document(conversationId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                onUpdate(null)
                return@addSnapshotListener
            }
            onUpdate(snapshot.toObject(SellerRatingRecord::class.java))
        }
    }

    fun submitRating(
        conversationId: String,
        stars: Int,
        review: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
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

        val saleRef = transactionsRef.document(conversationId)
        val ratingRef = ratingsRef.document(conversationId)
        val conversationRef = db.collection("conversations").document(conversationId)

        db.runTransaction { transaction ->
            val sale = transaction.get(saleRef)
            val existingRating = transaction.get(ratingRef)
            val conversation = transaction.get(conversationRef)

            if (!sale.exists() || !conversation.exists()) {
                throw Exception("Purchase not found")
            }

            val buyerId = sale.getString("buyerId").orEmpty()
            val sellerId = sale.getString("sellerId").orEmpty()
            val listingId = sale.getString("listingId").orEmpty()

            val listingRef = db.collection("listings").document(listingId)
            val listing = transaction.get(listingRef)

            if (!listing.exists()) throw Exception("Listing no longer exists")
            if (listing.getString("sellerId") != sellerId) throw Exception("Listing seller does not match")
            if (listing.getBoolean("sold") == true) throw Exception("Listing is already sold")
            if (listing.getBoolean("salePending") != true) throw Exception("Listing is not awaiting completion")
            if (buyerId != currentUserId) throw Exception("Only the buyer can rate this seller")
            if (sellerId.isBlank() || sellerId == buyerId) throw Exception("Invalid seller information")

            if (
                sale.getString("conversationId") != conversationId ||
                conversation.getString("buyerId") != buyerId ||
                conversation.getString("sellerId") != sellerId ||
                conversation.getString("listingId") != listingId
            ) {
                throw Exception("Purchase details do not match")
            }

            if (sale.getString("status") != SaleStatus.AWAITING_RATING) {
                throw Exception("This purchase is not awaiting a rating")
            }
            if (existingRating.exists()) {
                throw Exception("This purchase has already been rated")
            }

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

            transaction.update(
                saleRef,
                mapOf(
                    "status" to SaleStatus.COMPLETED,
                    "completedAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.update(
                listingRef,
                mapOf(
                    "sold" to true,
                    "salePending" to false
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}