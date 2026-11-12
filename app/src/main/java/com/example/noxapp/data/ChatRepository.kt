
package com.example.noxapp.data

import com.example.noxapp.models.ChatMessageRecord
import com.example.noxapp.models.ChatThread
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

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
}
