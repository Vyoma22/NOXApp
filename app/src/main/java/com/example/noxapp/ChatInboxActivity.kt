package com.example.noxapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.ChatInboxAdapter
import com.example.noxapp.adapters.ChatInboxItem
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.google.android.material.bottomnavigation.BottomNavigationView

class ChatInboxActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_inbox)

        applyEdgeToEdgeInsets(R.id.main, R.id.chatBottomNav)

        setupBottomNavigation()
        setupConversationList()
    }

    // Display temporary sample conversations.
    private fun setupConversationList() {

        val previewConversations = listOf(

            ChatInboxItem(
                conversationId = "preview-amazon-cat",
                studentName = "Amazon Cat",
                listingTitle = "MacBook Air · $550",
                lastMessage = "Perfect, see you then!",
                time = "2m",
                hasUnread = true
            )

        )

        val recyclerView =
            findViewById<RecyclerView>(R.id.chatInboxRecyclerView)

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        val chatAdapter = ChatInboxAdapter(
            previewConversations
        ) { selectedConversation ->

            // Open the individual conversation screen.
            val intent = Intent(
                this,
                ChatConversationActivity::class.java
            )

            intent.putExtra(
                "conversationId",
                selectedConversation.conversationId
            )

            startActivity(intent)
        }

        recyclerView.adapter = chatAdapter

        // Hide the empty message when conversations exist.
        findViewById<View>(
            R.id.chatInboxEmptyState
        ).visibility = if (previewConversations.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    // Keep the bottom navigation working.
    private fun setupBottomNavigation() {

        val bottomNav =
            findViewById<BottomNavigationView>(R.id.chatBottomNav)

        bottomNav.selectedItemId = R.id.navChat

        bottomNav.setOnItemSelectedListener { item ->

            when (item.itemId) {

                R.id.navHome -> {
                    bottomNav.post {
                        val intent = Intent(
                            this@ChatInboxActivity,
                            MainActivity::class.java
                        ).addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )

                        startActivity(intent)
                        finish()
                    }
                }

                R.id.navBrowse -> {
                    bottomNav.post {
                        startActivity(
                            Intent(
                                this@ChatInboxActivity,
                                BrowseActivity::class.java
                            )
                        )
                        finish()
                    }
                }

                R.id.navPost -> {
                    bottomNav.post {
                        startActivity(
                            Intent(
                                this@ChatInboxActivity,
                                PostListing::class.java
                            )
                        )
                        finish()
                    }
                }

                R.id.navProfile -> {
                    bottomNav.post {
                        startActivity(
                            Intent(
                                this@ChatInboxActivity,
                                ProfileActivity::class.java
                            )
                        )
                        finish()
                    }
                }

                R.id.navChat -> {
                    // Already on Chat.
                }
            }

            item.itemId == R.id.navChat
        }
    }
}
