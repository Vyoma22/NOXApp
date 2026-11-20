package com.example.noxapp.chat.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R

data class ChatInboxItem(
    val conversationId: String,
    val studentName: String,
    val listingTitle: String,
    val lastMessage: String,
    val time: String,
    val hasUnread: Boolean = false,
    val listingId: String = ""
)

class ChatInboxAdapter(
    private var conversations: List<ChatInboxItem>,
    private val onConversationClick: (ChatInboxItem) -> Unit
) : RecyclerView.Adapter<ChatInboxAdapter.ConversationViewHolder>() {

    init {
        setHasStableIds(true)
    }

    class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.chatInboxAvatar)
        val name: TextView = view.findViewById(R.id.chatInboxName)
        val listing: TextView = view.findViewById(R.id.chatInboxListing)
        val lastMessage: TextView = view.findViewById(R.id.chatInboxLastMessage)
        val time: TextView = view.findViewById(R.id.chatInboxTime)
        val unreadDot: View = view.findViewById(R.id.chatUnreadDot)
    }

    override fun getItemId(position: Int): Long =
        conversations[position].conversationId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_inbox, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]

        holder.name.text = conversation.studentName
        holder.listing.text = conversation.listingTitle
        holder.lastMessage.text = conversation.lastMessage
        holder.time.text = conversation.time

        holder.avatar.text = conversation.studentName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }

        holder.unreadDot.isVisible = conversation.hasUnread
        holder.lastMessage.setTypeface(
            null,
            if (conversation.hasUnread) Typeface.BOLD else Typeface.NORMAL
        )

        holder.itemView.setOnClickListener { onConversationClick(conversation) }
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ChatInboxItem>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = conversations.size
            override fun getNewListSize(): Int = newConversations.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                conversations[oldPos].conversationId == newConversations[newPos].conversationId

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                conversations[oldPos] == newConversations[newPos]
        })

        conversations = newConversations
        diffResult.dispatchUpdatesTo(this)
    }
}