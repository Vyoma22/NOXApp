package com.example.noxapp.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R

// Represents one chat message.
data class ChatMessage(
    val senderId: String,
    val text: String
)

class ChatMessageAdapter(
    private var messages: List<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(
        val container: FrameLayout
    ) : RecyclerView.ViewHolder(container) {

        val messageText: TextView =
            container.findViewById(R.id.chatMessageText)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MessageViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
                as FrameLayout

        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: MessageViewHolder,
        position: Int
    ) {
        val message = messages[position]

        // Display the actual message.
        holder.messageText.text = message.text

        val isSentByMe = message.senderId == currentUserId

        // Sent messages = green.
        // Received messages = pink.
        if (isSentByMe) {
            holder.messageText.setBackgroundResource(
                R.drawable.chat_bubble_sent
            )
        } else {
            holder.messageText.setBackgroundResource(
                R.drawable.chat_bubble_received
            )
        }

        // Keep bubble width fitted to its text.
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Sent messages go right, received go left.
        params.gravity = if (isSentByMe) {
            Gravity.END
        } else {
            Gravity.START
        }

        holder.messageText.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size

    // Replace displayed messages when chat data changes.
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}
