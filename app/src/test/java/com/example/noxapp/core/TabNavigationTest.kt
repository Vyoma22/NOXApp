package com.example.noxapp.core

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.ComponentActivity
import com.example.noxapp.browse.BrowseActivity
import com.example.noxapp.chat.ChatConversationActivity
import com.example.noxapp.chat.ChatInboxActivity
import com.example.noxapp.home.MainActivity
import com.example.noxapp.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The one tab policy every screen shares.
 *
 * A plain ComponentActivity stands in for Browse, Post, Chat and Profile: openTab only looks at the
 * destination and at whether the caller is Home, so the policy can be tested without Firebase.
 * The reuse flags asserted here are what stop Home to Browse to Chat to Browse from stacking a
 * second Browse.
 *
 * The conversation screen is built for real, with no intent extras: that is its preview mode, so
 * onCreate touches no Firebase and only create() is dispatched, never the onStart access gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TabNavigationTest {

    @Test
    fun everyTabBarItemHasItsOwnDestination() {
        val bar = tabBar(hostScreen())
        val screens = mutableSetOf<Class<*>>()
        for (index in 0 until bar.menu.size()) {
            val item = bar.menu.getItem(index)
            val tab = NavTab.forItemId(item.itemId)
            assertNotNull("No destination for tab item ${item.itemId}", tab)
            assertTrue("Two tab items share one screen", screens.add(tab!!.screen))
        }
        assertEquals(NavTab.entries.size, screens.size)
    }

    @Test
    fun switchingTabReusesTheDestinationAndLeavesTheCurrentScreen() {
        val reuseFlags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        for (destination in NavTab.entries) {
            val screen = hostScreen()
            screen.openTab(destination)

            val started = shadowOf(screen).nextStartedActivity
            assertEquals(destination.screen.name, started.component?.className)
            assertEquals(
                "Reuse flags missing when opening ${destination.name}",
                reuseFlags,
                started.flags and reuseFlags
            )
            assertTrue("${destination.name} left the old screen on the stack", screen.isFinishing)
        }
    }

    @Test
    fun homeStaysOnTheStackAsTheBackAnchor() {
        val home = Robolectric.buildActivity(MainActivity::class.java).get()

        home.openTab(NavTab.BROWSE)

        assertFalse("Back from any tab must still reach Home", home.isFinishing)
        assertEquals(
            BrowseActivity::class.java.name,
            shadowOf(home).nextStartedActivity.component?.className
        )
    }

    @Test
    fun tappingAnotherTabNavigatesOnceAndMovesTheHighlight() {
        val screen = hostScreen()
        var navigated: NavTab? = null
        var reselects = 0
        screen.wireTabBar(
            R.id.bottomNavBar,
            NavTab.BROWSE,
            onReselect = { reselects++ },
            navigate = { navigated = it }
        )

        val bar = tabBar(screen)
        bar.selectedItemId = R.id.navChat
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(NavTab.CHAT, navigated)
        assertEquals(0, reselects)
        // Accepting the selection is what plays Material's tap feedback on the way out.
        assertEquals(R.id.navChat, bar.selectedItemId)
    }

    @Test
    fun tapFeedbackIsMaterialDefaultAndNeedsNoListener() {
        // hostScreen leaves the bar unwired, exactly as the conversation screen used to.
        val bar = tabBar(hostScreen())
        bar.selectedItemId = R.id.navBrowse

        bar.findViewById<View>(R.id.navChat).performClick()

        assertEquals(R.id.navChat, bar.selectedItemId)
    }

    @Test
    fun tappingTheCurrentTabResetsItInsteadOfNavigating() {
        val screen = hostScreen()
        var navigated: NavTab? = null
        var reselects = 0
        screen.wireTabBar(
            R.id.bottomNavBar,
            NavTab.BROWSE,
            onReselect = { reselects++ },
            navigate = { navigated = it }
        )

        val bar = tabBar(screen)
        bar.selectedItemId = R.id.navBrowse
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(navigated)
        assertEquals(1, reselects)
    }

    @Test
    fun conversationScreenLeavesForAnotherTab() {
        val screen = conversationScreen()
        val bar = screen.findViewById<BottomNavigationView>(R.id.chatBottomNav)
        assertEquals(R.id.navChat, bar.selectedItemId)

        bar.findViewById<View>(R.id.navBrowse).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            BrowseActivity::class.java.name,
            shadowOf(screen).nextStartedActivity.component?.className
        )
    }

    @Test
    fun conversationScreenReturnsToTheInboxOnItsOwnTab() {
        val screen = conversationScreen()
        val bar = screen.findViewById<BottomNavigationView>(R.id.chatBottomNav)

        bar.findViewById<View>(R.id.navChat).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // The conversation also opens from a listing dialog over Browse, so no inbox is
        // guaranteed to be underneath it: go through the shared reuse policy, not finish().
        assertEquals(
            ChatInboxActivity::class.java.name,
            shadowOf(screen).nextStartedActivity.component?.className
        )
    }

    @Test
    fun comingBackToAScreenRestoresItsOwnHighlight() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val screen = controller.get()
        val bar = installBar(screen)

        var navigated: NavTab? = null
        var reselects = 0
        screen.wireTabBar(
            R.id.bottomNavBar,
            NavTab.HOME,
            onReselect = { reselects++ },
            navigate = { navigated = it }
        )

        // Home is never finished, so the highlight it accepted on the way out survives the hop.
        bar.selectedItemId = R.id.navChat
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavTab.CHAT, navigated)

        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(R.id.navHome, bar.selectedItemId)
        assertEquals("Restoring the highlight must not reset the screen", 0, reselects)

        // Otherwise Chat looks selected and tapping it is a reselect, so Home traps the student.
        navigated = null
        bar.findViewById<View>(R.id.navChat).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavTab.CHAT, navigated)
    }

    // visible() attaches the decor view, without which View.post queues the tab hop forever.
    private fun conversationScreen(): Activity =
        Robolectric.buildActivity(ChatConversationActivity::class.java).create().visible().get()

    private fun hostScreen(): ComponentActivity {
        val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        installBar(screen)
        return screen
    }

    private fun installBar(screen: ComponentActivity): BottomNavigationView {
        val bar = BottomNavigationView(ContextThemeWrapper(screen, R.style.Theme_NOXApp))
        bar.id = R.id.bottomNavBar
        bar.inflateMenu(R.menu.bottom_navigation)
        screen.setContentView(bar)
        return bar
    }

    private fun tabBar(screen: Activity): BottomNavigationView =
        screen.findViewById(R.id.bottomNavBar)
}