package com.example.noxapp.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.noxapp.BrowseActivity
import com.example.noxapp.ChatInboxActivity
import com.example.noxapp.MainActivity
import com.example.noxapp.ProfileActivity
import com.example.noxapp.R
import com.example.noxapp.postlisting.PostListing
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * The five destinations of the bottom tab bar.
 *
 * Every screen used to carry its own copy of this mapping and decide for itself whether to reuse
 * the destination and whether to finish the current screen. The copies drifted: Browse kept itself
 * alive when opening Chat, Chat built a second Browse on the way back, and a round trip left two
 * Browse activities on the stack. One mapping plus one policy below is what keeps them agreeing.
 */
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

/**
 * Switches tab: reuse the destination when the task already holds it, then leave this screen.
 *
 * CLEAR_TOP | SINGLE_TOP brings an existing instance forward instead of stacking a second copy, so
 * Home to Browse to Chat to Browse ends with one Browse. Home anchors the task and is never
 * finished, so Back from any tab still returns to it.
 */
fun Activity.openTab(destination: NavTab) {
    startActivity(
        Intent(this, destination.screen)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    if (this !is MainActivity) finish()
}

/**
 * Wires a bottom tab bar with the shared policy.
 *
 * Selecting another tab navigates through [navigate]. Selecting the current tab does nothing, and
 * reselecting it runs [onReselect], which is how each screen resets its own content. The listener
 * accepts the selection so Material plays its own tap feedback: answering false discards it, which
 * left every wired screen with a bar that looked dead until the next screen appeared. Resuming
 * puts the highlight back on [currentTab], since a screen that outlives the hop keeps that
 * selection and would otherwise read its own tab as a reselect.
 *
 * [navigate] is overridable for the map, which hands its tab choice back to the screen that
 * launched it rather than starting activities itself.
 */
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
            // Leave Material's menu dispatch before starting or finishing anything.
            bar.post { navigate(destination) }
        }
        true
    }
    bar.setOnItemReselectedListener { item ->
        if (item.itemId == currentTab.itemId && !isFinishing && !isDestroyed) onReselect()
    }
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        // Home is never finished, so the highlight accepted on the way out is still there when the
        // student comes back. Restore it, or that tab reads as a reselect and goes nowhere.
        if (event == Lifecycle.Event.ON_RESUME && bar.selectedItemId != currentTab.itemId) {
            bar.selectedItemId = currentTab.itemId
        }
    })
}