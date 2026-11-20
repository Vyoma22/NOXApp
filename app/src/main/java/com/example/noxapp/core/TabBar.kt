package com.example.noxapp.core

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.noxapp.R
import com.example.noxapp.browse.BrowseActivity
import com.example.noxapp.chat.ChatInboxActivity
import com.example.noxapp.chat.ChatRepository
import com.example.noxapp.home.MainActivity
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

enum class NavTab(val itemId: Int, val screen: Class<out Activity>) {
    HOME(R.id.navHome, MainActivity::class.java),
    BROWSE(R.id.navBrowse, BrowseActivity::class.java),
    POST(R.id.navPost, PostListing::class.java),
    CHAT(R.id.navChat, ChatInboxActivity::class.java),
    PROFILE(R.id.navProfile, ProfileActivity::class.java);

    companion object {
        fun forItemId(itemId: Int): NavTab? = entries.firstOrNull { it.itemId == itemId }
    }
}

fun Activity.openTab(destination: NavTab) {
    startActivity(
        Intent(this, destination.screen)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    if (this !is MainActivity) finish()
}

fun ComponentActivity.wireTabBar(
    barId: Int,
    currentTab: NavTab,
    onReselect: () -> Unit,
    navigate: (NavTab) -> Unit = { openTab(it) }
) {
    val bar = findViewById<BottomNavigationView>(barId)
    bar.selectedItemId = currentTab.itemId
    bar.setOnItemSelectedListener { item ->
        val destination = NavTab.forItemId(item.itemId)
        if (destination != null && destination != currentTab) {
            bar.post { navigate(destination) }
        }
        true
    }
    bar.setOnItemReselectedListener { item ->
        if (item.itemId == currentTab.itemId && !isFinishing && !isDestroyed) onReselect()
    }

    var unreadListener: ListenerRegistration? = null

    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (uid.isNotBlank() && unreadListener == null) {
                    unreadListener = ChatRepository.listenToMyConversations(
                        userId = uid,
                        onUpdate = { threads ->
                            if (isFinishing || isDestroyed) return@listenToMyConversations
                            val hasUnread = threads.any { thread ->
                                val lastMsgAt = thread.lastMessageAt?.toDate()?.time ?: 0L
                                val lastReadAt = thread.lastReadAt[uid]?.toDate()?.time ?: 0L
                                thread.lastMessage.isNotBlank() &&
                                        thread.lastSenderId.isNotBlank() &&
                                        thread.lastSenderId != uid &&
                                        lastMsgAt > lastReadAt
                            }
                            updateChatTabBadge(bar, hasUnread)
                        },
                        onError = { /* Ignore badge errors silently */ }
                    )
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                if (bar.selectedItemId != currentTab.itemId) {
                    bar.selectedItemId = currentTab.itemId
                }
            }
            Lifecycle.Event.ON_STOP -> {
                unreadListener?.remove()
                unreadListener = null
            }
            else -> Unit
        }
    })
}

private fun updateChatTabBadge(bar: BottomNavigationView, hasUnread: Boolean) {
    if (hasUnread) {
        bar.getOrCreateBadge(R.id.navChat).apply {
            isVisible = true
            clearNumber()
            backgroundColor = ContextCompat.getColor(bar.context, R.color.nox_primary_green)
        }
    } else {
        bar.getBadge(R.id.navChat)?.isVisible = false
    }
}