package com.example.noxapp.chat.adapters

import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val senderId: String,
    val text: String,
    val timestampMillis: Long = 0L
)

class ChatMessageAdapter(
    private var messages: List<ChatMessage>,
    private var currentUserId: String
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container) {
        val messageText: TextView = container.findViewById(R.id.chatMessageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false) as FrameLayout
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isSentByMe = message.senderId == currentUserId

        holder.messageText.text = if (message.timestampMillis > 0L) {
            "${message.text}\n${formatDayAndTime(message.timestampMillis)}"
        } else {
            message.text
        }

        holder.messageText.setBackgroundResource(
            if (isSentByMe) R.drawable.chat_bubble_sent else R.drawable.chat_bubble_received
        )

        val params = (holder.messageText.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        params.gravity = if (isSentByMe) Gravity.END else Gravity.START
        holder.messageText.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size

    fun setCurrentUserId(uid: String) {
        if (currentUserId != uid) {
            currentUserId = uid
            notifyItemRangeChanged(0, messages.size)
        }
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = messages.size
            override fun getNewListSize(): Int = newMessages.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = messages[oldPos]
                val newItem = newMessages[newPos]
                return oldItem.senderId == newItem.senderId &&
                        oldItem.timestampMillis == newItem.timestampMillis &&
                        oldItem.text == newItem.text
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                messages[oldPos] == newMessages[newPos]
        })

        messages = newMessages
        diffResult.dispatchUpdatesTo(this)
    }

    private fun formatDayAndTime(millis: Long): String {
        val timePart = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        return when {
            DateUtils.isToday(millis) -> "Today · $timePart"
            DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday · $timePart"
            else -> {
                val nowYear = Calendar.getInstance().get(Calendar.YEAR)
                val msgYear = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
                val pattern = if (nowYear == msgYear) "MMM d" else "MMM d, yyyy"
                val datePart = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
                "$datePart · $timePart"
            }
        }
    }
}