package com.example.noxapp.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R

// Information displayed for each conversation.
data class ChatInboxItem(
    val conversationId: String,
    val studentName: String,
    val listingTitle: String,
    val lastMessage: String,
    val time: String,
    val hasUnread: Boolean = false
)

class ChatInboxAdapter(
    private var conversations: List<ChatInboxItem>,
    private val onConversationClick: (ChatInboxItem) -> Unit
) : RecyclerView.Adapter<ChatInboxAdapter.ConversationViewHolder>() {

    class ConversationViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val avatar: TextView =
            view.findViewById(R.id.chatInboxAvatar)

        val name: TextView =
            view.findViewById(R.id.chatInboxName)

        val listing: TextView =
            view.findViewById(R.id.chatInboxListing)

        val lastMessage: TextView =
            view.findViewById(R.id.chatInboxLastMessage)

        val time: TextView =
            view.findViewById(R.id.chatInboxTime)

        // Our new unread indicator!
        val unreadDot: View =
            view.findViewById(R.id.chatUnreadDot)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConversationViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_inbox, parent, false)

        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ConversationViewHolder,
        position: Int
    ) {
        val conversation = conversations[position]

        holder.name.text = conversation.studentName
        holder.listing.text = conversation.listingTitle
        holder.lastMessage.text = conversation.lastMessage
        holder.time.text = conversation.time

        // Show initials in the circular avatar.
        val initials = conversation.studentName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")

        holder.avatar.text = initials

        // Show the dot only for unread conversations.
        holder.unreadDot.visibility =
            if (conversation.hasUnread) View.VISIBLE
            else View.GONE

        // Make unread message previews bolder.
        holder.lastMessage.setTypeface(
            null,
            if (conversation.hasUnread) Typeface.BOLD
            else Typeface.NORMAL
        )

        // Open the conversation when tapped.
        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ChatInboxItem>) {
        conversations = newConversations
        notifyDataSetChanged()
    }
}
