
package com.example.noxapp.data

import com.example.noxapp.models.ChatMessageRecord
import com.example.noxapp.models.ChatThread
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source

/**
 * Handles Firebase Firestore operations for NOX Chat.
 *
 * Currently READ-ONLY.
 * No messages or conversations are created here yet.
 */
object ChatRepository {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    // Proposed Firestore collection for conversations.
    private val conversationsRef by lazy {
        db.collection("conversations")
    }

    // ---------------------------------------------
    // 1. LISTEN TO A STUDENT'S CONVERSATIONS
    // ---------------------------------------------

    fun listenToMyConversations(
        userId: String,
        onUpdate: (List<ChatThread>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {

        if (userId.isBlank()) {
            onError(
                IllegalArgumentException("User ID cannot be empty")
            )
            return null
        }

        return conversationsRef
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val conversations = snapshot
                    ?.documents
                    ?.mapNotNull { document ->
                        document.toObject(ChatThread::class.java)
                            ?.copy(id = document.id)
                    }
                    ?.sortedByDescending {
                        it.lastMessageAt?.toDate()?.time ?: 0L
                    }
                    .orEmpty()

                onUpdate(conversations)
            }
    }

    // ---------------------------------------------
    // 2. LISTEN TO MESSAGES IN ONE CONVERSATION
    // ---------------------------------------------

    fun listenToMessages(
        conversationId: String,
        onUpdate: (List<ChatMessageRecord>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {

        if (conversationId.isBlank()) {
            onError(
                IllegalArgumentException(
                    "Conversation ID cannot be empty"
                )
            )
            return null
        }

        // Find messages belonging to this conversation.
        return conversationsRef
            .document(conversationId)
            .collection("messages")

            // Display oldest messages first.
            .orderBy(
                "createdAt",
                Query.Direction.ASCENDING
            )

            // Listen for changes in real time.
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                // Convert Firestore documents into messages.
                val messages = snapshot
                    ?.documents
                    ?.mapNotNull { document ->
                        document.toObject(
                            ChatMessageRecord::class.java
                        )?.copy(id = document.id)
                    }
                    .orEmpty()

                onUpdate(messages)
            }
    }


    fun sendMessage(
        conversationId: String,
        messageText: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val senderId =
            FirebaseAuth.getInstance().currentUser?.uid

        val text = messageText.trim()

        if (senderId.isNullOrBlank()) {
            onError(Exception("Please sign in to send messages"))
            return
        }

        if (conversationId.isBlank()) {
            onError(Exception("Conversation ID is missing"))
            return
        }

        if (text.isEmpty() || text.length > 2000) {
            onError(Exception("Message must be 1–2000 characters"))
            return
        }

        val conversationRef =
            conversationsRef.document(conversationId)

        val messageRef =
            conversationRef.collection("messages").document()

        // Save the message and update the inbox preview together.
        val batch = db.batch()

        batch.set(
            messageRef,
            mapOf(
                "senderId" to senderId,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )


        batch.update(
            conversationRef,
            mapOf(
                "lastMessage" to text,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageId" to messageRef.id
            )
        )


        batch.commit()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }


    // --------------------------------------------------
    // CREATE OR REOPEN A BUYER-SELLER CONVERSATION
    // --------------------------------------------------



    fun createOrOpenConversation(
        listingId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val buyerId =
            FirebaseAuth.getInstance().currentUser?.uid

        if (buyerId.isNullOrBlank()) {
            onError(Exception("Please sign in first"))
            return
        }

        if (listingId.isBlank()) {
            onError(Exception("Listing ID is missing"))
            return
        }

        // Get the real listing and seller.
        ListingRepo.getListing(listingId) { listing ->

            if (listing == null) {
                onError(Exception("Listing not found or unavailable"))
                return@getListing
            }

            val sellerId = listing.sellerId

            if (sellerId.isBlank()) {
                onError(Exception("This listing has no registered seller"))
                return@getListing
            }

            if (sellerId == buyerId) {
                onError(Exception("You cannot message yourself"))
                return@getListing
            }

            // Same buyer + same listing = same conversation.
            val conversationId = "${listingId}_${buyerId}"

            val conversationRef =
                conversationsRef.document(conversationId)

            val conversationData = hashMapOf<String, Any>(
                "listingId" to listingId,
                "buyerId" to buyerId,
                "sellerId" to sellerId,
                "participantIds" to listOf(buyerId, sellerId),
                "lastMessage" to "",
                "lastMessageId" to "",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )

            // Attempt to create the conversation.
            // Firestore rules allow creation only when valid.
            conversationRef.set(conversationData)
                .addOnSuccessListener {
                    onSuccess(conversationId)
                }
                .addOnFailureListener { creationError ->

                    // It may already exist.
                    // Reopen it without changing old messages
                    // or resetting its timestamps.
                    conversationRef.get(Source.SERVER)
                        .addOnSuccessListener { existing ->

                            val validExistingChat =
                                existing.exists() &&
                                        existing.getString("listingId") == listingId &&
                                        existing.getString("buyerId") == buyerId &&
                                        existing.getString("sellerId") == sellerId &&
                                        existing.get("participantIds") ==
                                        listOf(buyerId, sellerId)

                            if (validExistingChat) {
                                onSuccess(conversationId)
                            } else {
                                onError(creationError)
                            }
                        }
                        .addOnFailureListener {
                            onError(creationError)
                        }
                }
        }
    }



}
