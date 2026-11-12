
package com.example.noxapp.models

import com.google.firebase.Timestamp

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
