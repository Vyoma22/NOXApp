package com.example.noxapp.chat

import com.example.noxapp.listing.ListingRepo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source

/**
 * Handles Firebase Firestore operations for NOX Chat.
 */
object ChatRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val conversationsRef by lazy { db.collection("conversations") }

    // ---------------------------------------------
    // 1. LISTEN TO A STUDENT'S CONVERSATIONS
    // ---------------------------------------------

    fun listenToMyConversations(
        userId: String,
        onUpdate: (List<ChatThread>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        if (userId.isBlank()) {
            onError(IllegalArgumentException("User ID cannot be empty"))
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
                    ?.mapNotNull { doc ->
                        doc.toObject(ChatThread::class.java)?.copy(id = doc.id)
                    }
                    ?.sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
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
            onError(IllegalArgumentException("Conversation ID cannot be empty"))
            return null
        }

        return conversationsRef
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val messages = snapshot
                    ?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(ChatMessageRecord::class.java)?.copy(id = doc.id)
                    }
                    .orEmpty()

                onUpdate(messages)
            }
    }

    // ---------------------------------------------
    // 3. SEND MESSAGE & MARK CONVERSATION READ
    // ---------------------------------------------

    fun sendMessage(
        conversationId: String,
        messageText: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid
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

        val conversationRef = conversationsRef.document(conversationId)
        val messageRef = conversationRef.collection("messages").document()
        val now = FieldValue.serverTimestamp()

        db.batch().apply {
            set(
                messageRef,
                mapOf(
                    "senderId" to senderId,
                    "text" to text,
                    "createdAt" to now
                )
            )
            update(
                conversationRef,
                mapOf(
                    "lastMessage" to text,
                    "lastMessageAt" to now,
                    "lastMessageId" to messageRef.id,
                    "lastSenderId" to senderId,
                    "lastReadAt.$senderId" to now
                )
            )
        }
            .commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun markConversationRead(conversationId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (conversationId.isBlank() || uid.isBlank()) return

        conversationsRef.document(conversationId)
            .update("lastReadAt.$uid", FieldValue.serverTimestamp())
    }

    // ---------------------------------------------
    // 4. CREATE OR REOPEN A BUYER-SELLER CONVERSATION
    // ---------------------------------------------

    fun createOrOpenConversation(
        listingId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        if (buyerId.isNullOrBlank()) {
            onError(Exception("Please sign in first"))
            return
        }
        if (listingId.isBlank()) {
            onError(Exception("Listing ID is missing"))
            return
        }

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

            val conversationId = "${listingId}_${buyerId}"
            val conversationRef = conversationsRef.document(conversationId)
            val now = FieldValue.serverTimestamp()

            val conversationData = hashMapOf<String, Any>(
                "listingId" to listingId,
                "buyerId" to buyerId,
                "sellerId" to sellerId,
                "participantIds" to listOf(buyerId, sellerId),
                "lastMessage" to "",
                "lastMessageId" to "",
                "lastSenderId" to "",
                "lastMessageAt" to now,
                "createdAt" to now
            )

            conversationRef.set(conversationData)
                .addOnSuccessListener { onSuccess(conversationId) }
                .addOnFailureListener { creationError ->
                    conversationRef.get(Source.SERVER)
                        .addOnSuccessListener { existing ->
                            val validExistingChat =
                                existing.exists() &&
                                        existing.getString("listingId") == listingId &&
                                        existing.getString("buyerId") == buyerId &&
                                        existing.getString("sellerId") == sellerId &&
                                        existing.get("participantIds") == listOf(buyerId, sellerId)

                            if (validExistingChat) {
                                onSuccess(conversationId)
                            } else {
                                onError(creationError)
                            }
                        }
                        .addOnFailureListener { onError(creationError) }
                }
        }
    }
}