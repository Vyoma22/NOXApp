
package com.example.noxapp.chat

import com.example.noxapp.chat.SaleTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.noxapp.chat.SaleStatus
import com.google.firebase.firestore.FieldValue

object SaleTransactionRepository {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    // Proposed collection: one transaction per conversation.
    private val transactionsRef by lazy {
        db.collection("saleTransactions")
    }

    // Listen for changes to the transaction.
    fun listenToTransaction(
        conversationId: String,
        onUpdate: (SaleTransaction?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {

        val currentUserId =
            FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId.isNullOrBlank()) {
            onError(Exception("Please sign in first"))
            return null
        }

        if (conversationId.isBlank()) {
            onError(Exception("Conversation ID is missing"))
            return null
        }

        return transactionsRef
            .document(conversationId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }

                val transaction =
                    snapshot.toObject(
                        SaleTransaction::class.java
                    )?.copy(id = snapshot.id)

                onUpdate(transaction)
            }
    }

    // Seller confirms that the exchange has taken place.
    fun confirmSale(
        conversationId: String,
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

        val conversationRef =
            db.collection("conversations")
                .document(conversationId)

        val transactionRef =
            transactionsRef.document(conversationId)

        db.runTransaction { transaction ->

            // Read the real conversation.
            val conversation = transaction.get(conversationRef)

            if (!conversation.exists()) {
                throw Exception("Conversation not found")
            }

            val sellerId =
                conversation.getString("sellerId").orEmpty()

            val buyerId =
                conversation.getString("buyerId").orEmpty()

            val listingId =
                conversation.getString("listingId").orEmpty()

            // Only the actual seller can confirm.
            if (sellerId != currentUserId) {
                throw Exception("Only the seller can confirm this sale")
            }

            if (
                buyerId.isBlank() ||
                buyerId == sellerId ||
                listingId.isBlank()
            ) {
                throw Exception("Invalid conversation details")
            }

            // Verify the conversation's participants.
            val participants =
                conversation.get("participantIds") as? List<*>

            if (participants != listOf(buyerId, sellerId)) {
                throw Exception("Invalid conversation participants")
            }

            // Check the original listing.
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
                throw Exception("This listing is already sold")
            }
            if (listing.getBoolean("salePending") == true) {
                throw Exception(
                    "This listing is already awaiting buyer confirmation"
                )
            }

            // Prevent duplicate sale transactions.
            val existingSale =
                transaction.get(transactionRef)
            // Check whether another buyer has already been confirmed.
            val saleLockRef =
                db.collection("saleLocks").document(listingId)

            val existingLock =
                transaction.get(saleLockRef)

            if (existingLock.exists()) {
                throw Exception(
                    "This listing already has a confirmed buyer"
                )
            }

            if (existingSale.exists()) {
                throw Exception("This sale has already been confirmed")
            }

            // Reserve this listing for one buyer.
            transaction.set(
                saleLockRef,
                mapOf(
                    "listingId" to listingId,
                    "conversationId" to conversationId,
                    "buyerId" to buyerId,
                    "sellerId" to sellerId,
                    "status" to SaleStatus.PENDING_BUYER,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            // Create a pending transaction.
            transaction.set(
                transactionRef,
                mapOf(
                    "conversationId" to conversationId,
                    "listingId" to listingId,
                    "buyerId" to buyerId,
                    "sellerId" to sellerId,
                    "status" to SaleStatus.PENDING_BUYER,
                    "sellerConfirmedAt" to
                            FieldValue.serverTimestamp()
                )
            )
            // Hide the listing from public feeds when
            // the seller confirms the exchange.
            transaction.update(
                listingRef,
                "salePending",
                true
            )
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }

    // Buyer confirms they received the item.
    fun confirmPurchase(
        conversationId: String,
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

        val transactionRef =
            transactionsRef.document(conversationId)

        val conversationRef =
            db.collection("conversations")
                .document(conversationId)

        db.runTransaction { transaction ->

            // Retrieve the actual conversation.
            val conversation =
                transaction.get(conversationRef)

            if (!conversation.exists()) {
                throw Exception("Conversation not found")
            }

            val buyerId =
                conversation.getString("buyerId").orEmpty()

            val sellerId =
                conversation.getString("sellerId").orEmpty()

            val listingId =
                conversation.getString("listingId").orEmpty()

            // Only the actual buyer can confirm.
            if (buyerId != currentUserId) {
                throw Exception(
                    "Only the buyer can confirm the purchase"
                )
            }

            if (
                sellerId.isBlank() ||
                sellerId == buyerId ||
                listingId.isBlank()
            ) {
                throw Exception("Invalid conversation details")
            }

            // Retrieve the sale transaction.
            val sale = transaction.get(transactionRef)

            if (!sale.exists()) {
                throw Exception(
                    "The seller has not confirmed this sale yet"
                )
            }

            // Verify that this sale belongs to this conversation.
            if (
                sale.getString("buyerId") != buyerId ||
                sale.getString("sellerId") != sellerId ||
                sale.getString("listingId") != listingId ||
                sale.getString("conversationId") != conversationId
            ) {
                throw Exception(
                    "Sale details do not match this conversation"
                )
            }

            // The seller must confirm before the buyer.
            if (
                sale.getString("status") !=
                SaleStatus.PENDING_BUYER
            ) {
                throw Exception(
                    "This purchase is not awaiting confirmation"
                )
            }

            // Buyer confirmed: rating is now required.
            transaction.update(
                transactionRef,
                mapOf(
                    "status" to SaleStatus.AWAITING_RATING,
                    "buyerConfirmedAt" to
                            FieldValue.serverTimestamp()
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
