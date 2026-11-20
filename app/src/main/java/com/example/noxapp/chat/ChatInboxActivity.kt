package com.example.noxapp.chat

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.chat.adapters.ChatInboxAdapter
import com.example.noxapp.chat.adapters.ChatInboxItem
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.wireTabBar
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingRepo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatInboxActivity : AppCompatActivity() {

    private val enableRealInbox = true

    private lateinit var chatAdapter: ChatInboxAdapter
    private lateinit var emptyStateView: View
    private lateinit var recyclerView: RecyclerView

    private var inboxListener: ListenerRegistration? = null
    private var inboxVersion = 0
    private var inboxRows: List<ChatInboxItem> = emptyList()
    private var accessJob: Job? = null

    private val listingCache = mutableMapOf<String, Listing?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_inbox)

        applyEdgeToEdgeInsets(R.id.main, R.id.chatBottomNav)

        emptyStateView = findViewById(R.id.chatInboxEmptyState)
        recyclerView = findViewById(R.id.chatInboxRecyclerView)

        setupBottomNavigation()
        setupConversationList()
    }

    private fun setupConversationList() {
        val previewConversations = if (!enableRealInbox) {
            listOf(
                ChatInboxItem(
                    conversationId = "preview-amazon-cat",
                    studentName = "Amazon Cat",
                    listingTitle = "MacBook Air · $550",
                    lastMessage = "Perfect, see you then!",
                    time = "Today · 2:15 PM",
                    hasUnread = true
                )
            )
        } else {
            emptyList()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null

        chatAdapter = ChatInboxAdapter(previewConversations) { selected ->
            val intent = Intent(this, ChatConversationActivity::class.java).apply {
                if (enableRealInbox) {
                    putExtra("conversationId", selected.conversationId)
                    putExtra("listingId", selected.listingId)
                }
            }
            startActivity(intent)
        }

        recyclerView.adapter = chatAdapter
        inboxRows = previewConversations
        emptyStateView.isVisible = !enableRealInbox && inboxRows.isEmpty()
    }

    private fun listenForConversations() {
        if (!enableRealInbox) return

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Please sign in to view conversations", Toast.LENGTH_SHORT).show()
            return
        }

        inboxListener?.remove()
        inboxListener = ChatRepository.listenToMyConversations(
            userId = userId,
            onUpdate = { conversations ->
                if (isActivityGone()) return@listenToMyConversations

                val version = ++inboxVersion

                inboxRows = conversations.map { thread ->
                    buildInboxItem(
                        thread = thread,
                        userId = userId,
                        listing = listingCache[thread.listingId],
                        isLoaded = listingCache.containsKey(thread.listingId)
                    )
                }

                chatAdapter.updateConversations(inboxRows)
                emptyStateView.isVisible = inboxRows.isEmpty()

                conversations
                    .distinctBy { it.listingId }
                    .filter { it.listingId.isNotBlank() && !listingCache.containsKey(it.listingId) }
                    .forEach { thread ->
                        ListingRepo.getListing(thread.listingId) { listing ->
                            if (isActivityGone() || version != inboxVersion) return@getListing

                            listingCache[thread.listingId] = listing
                            inboxRows = conversations.map { item ->
                                buildInboxItem(
                                    thread = item,
                                    userId = userId,
                                    listing = listingCache[item.listingId],
                                    isLoaded = listingCache.containsKey(item.listingId)
                                )
                            }
                            chatAdapter.updateConversations(inboxRows)
                        }
                    }
            },
            onError = { error ->
                if (!isActivityGone()) {
                    Toast.makeText(this, "Unable to load chats: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun buildInboxItem(
        thread: ChatThread,
        userId: String,
        listing: Listing?,
        isLoaded: Boolean
    ): ChatInboxItem {
        val isSeller = thread.sellerId == userId
        val name = when {
            isSeller -> "Buyer"
            !isLoaded -> "Seller"
            else -> listing?.sellerName?.ifBlank { "Seller" } ?: "Seller"
        }

        val productDetails = when {
            !isLoaded -> ""
            listing != null -> "${listing.title} · \$${listing.price.toInt()}"
            else -> "Listing unavailable"
        }

        val formattedTimestamp = thread.lastMessageAt
            ?.toDate()
            ?.let { formatInboxDateTime(it) }
            .orEmpty()

        val lastMessageMillis = thread.lastMessageAt?.toDate()?.time ?: 0L
        val lastReadMillis = thread.lastReadAt[userId]?.toDate()?.time ?: 0L
        val isUnread = thread.lastMessage.isNotBlank() &&
                thread.lastSenderId.isNotBlank() &&
                thread.lastSenderId != userId &&
                lastMessageMillis > lastReadMillis

        return ChatInboxItem(
            conversationId = thread.id,
            listingId = thread.listingId,
            studentName = name,
            listingTitle = productDetails,
            lastMessage = thread.lastMessage.ifBlank { "No messages yet" },
            time = formattedTimestamp,
            hasUnread = isUnread
        )
    }

    private fun formatInboxDateTime(date: Date): String {
        val millis = date.time
        val timePart = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

        return when {
            DateUtils.isToday(millis) -> "Today · $timePart"
            DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday · $timePart"
            else -> {
                val nowYear = Calendar.getInstance().get(Calendar.YEAR)
                val msgYear = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
                val datePattern = if (nowYear == msgYear) "MMM d" else "MMM d, yyyy"
                val datePart = SimpleDateFormat(datePattern, Locale.getDefault()).format(date)
                "$datePart · $timePart"
            }
        }
    }

    override fun onStart() {
        super.onStart()
        accessJob?.cancel()
        accessJob = checkAccessOnStart { listenForConversations() }
    }

    override fun onStop() {
        accessJob?.cancel()
        inboxListener?.remove()
        inboxListener = null
        inboxVersion++
        super.onStop()
    }

    private fun setupBottomNavigation() {
        wireTabBar(R.id.chatBottomNav, NavTab.CHAT, onReselect = {
            recyclerView.smoothScrollToPosition(0)
        })
    }

    private fun isActivityGone(): Boolean = isFinishing || isDestroyed
}