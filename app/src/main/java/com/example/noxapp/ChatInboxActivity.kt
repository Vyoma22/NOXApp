
package com.example.noxapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.ChatInboxAdapter
import com.example.noxapp.adapters.ChatInboxItem
import com.example.noxapp.data.ChatRepository
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.ui.NavTab
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.checkAccessOnStart
import com.example.noxapp.ui.wireTabBar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Locale

class ChatInboxActivity : AppCompatActivity() {

    // SAFETY SWITCH:
    // Keep false until approved Firebase rules are deployed.
    private val enableRealInbox = true

    private lateinit var chatAdapter: ChatInboxAdapter

    private var inboxListener: ListenerRegistration? = null

    // Prevent old product requests from updating newer inbox data.
    private var inboxVersion = 0

    private var inboxRows: List<ChatInboxItem> = emptyList()

    private var accessJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_chat_inbox)

        applyEdgeToEdgeInsets(
            R.id.main,
            R.id.chatBottomNav
        )

        setupBottomNavigation()
        setupConversationList()
    }

    // --------------------------------------------------
    // SET UP THE CHAT INBOX
    // --------------------------------------------------

    private fun setupConversationList() {

        // Keep Amazon Cat as our temporary preview.
        // Once real inbox is enabled, this sample disappears.
        val previewConversations = if (!enableRealInbox) {

            listOf(
                ChatInboxItem(
                    conversationId = "preview-amazon-cat",
                    studentName = "Amazon Cat",
                    listingTitle = "MacBook Air · $550",
                    lastMessage = "Perfect, see you then!",
                    time = "2m",
                    hasUnread = true
                )
            )

        } else {
            emptyList()
        }

        val recyclerView =
            findViewById<RecyclerView>(
                R.id.chatInboxRecyclerView
            )

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        // Create the adapter.
        chatAdapter = ChatInboxAdapter(
            previewConversations
        ) { selectedConversation ->

            val intent = Intent(
                this,
                ChatConversationActivity::class.java
            )

            if (enableRealInbox) {

                // A real conversation requires both IDs.
                intent.putExtra(
                    "conversationId",
                    selectedConversation.conversationId
                )

                intent.putExtra(
                    "listingId",
                    selectedConversation.listingId
                )
            }

            // In preview mode, no Firebase IDs are passed.
            // This preserves the Amazon Cat demonstration.
            startActivity(intent)
        }

        recyclerView.adapter = chatAdapter

        inboxRows = previewConversations

        updateEmptyState()
    }

    // --------------------------------------------------
    // SHOW OR HIDE EMPTY INBOX MESSAGE
    // --------------------------------------------------

    private fun updateEmptyState() {

        findViewById<View>(
            R.id.chatInboxEmptyState
        ).visibility =
            if (inboxRows.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    // --------------------------------------------------
    // LISTEN TO REAL FIREBASE CONVERSATIONS
    // --------------------------------------------------

    private fun listenForConversations() {

        // Never contact the real Chat collection
        // while our safety switch is disabled.
        if (!enableRealInbox) return

        val userId =
            FirebaseAuth.getInstance().currentUser?.uid

        if (userId.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Please sign in to view conversations",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        inboxListener =
            ChatRepository.listenToMyConversations(
                userId = userId,

                onUpdate = { conversations ->

                    if (isFinishing || isDestroyed) {
                        return@listenToMyConversations
                    }

                    // Ignore results from older updates.
                    val version = ++inboxVersion

                    val timeFormatter =
                        SimpleDateFormat(
                            "h:mm a",
                            Locale.getDefault()
                        )

                    // First display the conversations
                    // using the available Firestore data.
                    inboxRows = conversations.map { conversation ->

                        val otherStudentName =
                            if (conversation.sellerId == userId) {
                                "Buyer"
                            } else {
                                "Seller"
                            }

                        ChatInboxItem(
                            conversationId = conversation.id,

                            listingId = conversation.listingId,

                            studentName = otherStudentName,

                            listingTitle = "Loading product...",

                            lastMessage =
                                conversation.lastMessage.ifBlank {
                                    "No messages yet"
                                },

                            time =
                                conversation.lastMessageAt
                                    ?.toDate()
                                    ?.let {
                                        timeFormatter.format(it)
                                    }
                                    .orEmpty(),

                            // Real unread tracking has not
                            // been implemented yet.
                            hasUnread = false
                        )
                    }

                    chatAdapter.updateConversations(inboxRows)

                    updateEmptyState()

                    // Fetch product information for
                    // each conversation in the inbox.
                    conversations.forEach { conversation ->

                        ListingRepo.getListing(
                            conversation.listingId
                        ) { listing ->

                            // Prevent outdated callbacks from
                            // modifying the current inbox.
                            if (
                                isFinishing ||
                                isDestroyed ||
                                version != inboxVersion
                            ) {
                                return@getListing
                            }

                            inboxRows = inboxRows.map { item ->

                                if (
                                    item.conversationId !=
                                    conversation.id
                                ) {
                                    item

                                } else {

                                    // Show the other person.
                                    // Do not retrieve private
                                    // student profile information.
                                    val name =
                                        if (
                                            conversation.sellerId ==
                                            userId
                                        ) {
                                            "Buyer"
                                        } else {
                                            listing?.sellerName
                                                ?.ifBlank {
                                                    "Seller"
                                                }
                                                ?: "Seller"
                                        }

                                    val productDetails =
                                        if (listing != null) {

                                            "${listing.title} · \$${listing.price.toInt()}"

                                        } else {

                                            "Listing unavailable"
                                        }

                                    item.copy(
                                        studentName = name,
                                        listingTitle = productDetails
                                    )
                                }
                            }

                            chatAdapter.updateConversations(
                                inboxRows
                            )

                            updateEmptyState()
                        }
                    }
                },

                onError = { error ->

                    if (!isFinishing && !isDestroyed) {

                        Toast.makeText(
                            this,
                            "Unable to load chats: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
    }

    // --------------------------------------------------
    // START AND STOP FIREBASE LISTENER
    // --------------------------------------------------

    override fun onStart() {
        super.onStart()

        accessJob?.cancel()
        accessJob = checkAccessOnStart { listenForConversations() }
    }

    override fun onStop() {

        accessJob?.cancel()

        // Stop Firestore listening when leaving the screen.
        inboxListener?.remove()
        inboxListener = null

        // Ignore any pending product lookup callbacks.
        inboxVersion++

        super.onStop()
    }

    // --------------------------------------------------
    // BOTTOM NAVIGATION
    // KEEP THE EXISTING NOX NAVIGATION WORKING
    // --------------------------------------------------

    private fun setupBottomNavigation() {
        wireTabBar(R.id.chatBottomNav, NavTab.CHAT, onReselect = {
            // Tapping Chat again returns to the top of the inbox.
            findViewById<RecyclerView>(R.id.chatInboxRecyclerView)
                ?.smoothScrollToPosition(0)
        })
    }
}
