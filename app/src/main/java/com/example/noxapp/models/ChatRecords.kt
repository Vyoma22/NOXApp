package com.example.noxapp.models

import com.google.firebase.Timestamp

/**
 * One conversation between a buyer and a seller
 * about one specific NOX listing.
 */
data class ChatThread(
    var id: String = "",

    // Product being discussed
    val listingId: String = "",

    // The two students in this conversation
    val buyerId: String = "",
    val sellerId: String = "",

    // Used to find conversations belonging to a student
    val participantIds: List<String> = emptyList(),

    // Information displayed in the Chats Inbox
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,

    // When the conversation was created
    val createdAt: Timestamp? = null
)

/**
 * One message sent inside a NOX conversation.
 *
 * Messages will be stored under:
 * conversations/{conversationId}/messages/{messageId}
 */
data class ChatMessageRecord(

    // Unique message ID
    var id: String = "",

    // Firebase UID of the student who sent the message
    val senderId: String = "",

    // Message content
    val text: String = "",

    // Time the message was created
    val createdAt: Timestamp? = null

)
