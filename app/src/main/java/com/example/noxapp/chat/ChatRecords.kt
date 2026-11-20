package com.example.noxapp.chat

import com.google.firebase.Timestamp

/**
 * One conversation between a buyer and a seller
 * about one specific NOX listing.
 */
data class ChatThread(
    val id: String = "",
    val listingId: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageId: String = "",
    val lastSenderId: String = "",
    val lastMessageAt: com.google.firebase.Timestamp? = null,
    val lastReadAt: Map<String, com.google.firebase.Timestamp> = emptyMap(),
    val createdAt: com.google.firebase.Timestamp? = null
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
