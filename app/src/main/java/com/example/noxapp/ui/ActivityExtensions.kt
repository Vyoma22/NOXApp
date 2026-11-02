package com.example.noxapp.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.noxapp.R

// Opens [target] and clears the back stack
fun Activity.goTo(target: Class<out Activity>) {
    startActivity(
        Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

/**
 * Status-bar, navigation-bar, and keyboard padding for screens whose root is a
 * [ScrollView] (login, sign-up, verify email).
 *
 * The window is edge to edge, so `adjustResize` no longer shrinks it and the keyboard
 * would otherwise sit on top of the low fields. The bottom padding is raised to the
 * keyboard height and the focused field is scrolled above it.
 */
fun Activity.applySystemBarPadding() {
    // Android 11+ reports the IME as an inset instead of resizing an edge-to-edge window.
    // Turn the resize off so the keyboard is counted once, through the padding below.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
    val root = findViewById<View>(R.id.main)
    val scroll = root as? ScrollView
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottom = maxOf(bars.bottom, ime.bottom)
        view.setPadding(bars.left, bars.top, bars.right, bottom)
        if (ime.bottom > 0 && scroll != null) {
            // Wait for the new padding to be measured before scrolling the field into view.
            scroll.post { scroll.scrollFocusedChildIntoView(ime.bottom) }
        }
        insets
    }
}

/** Raises the focused field above the keyboard once the IME finishes resizing the screen. */
private fun ScrollView.scrollFocusedChildIntoView(imeHeight: Int) {
    val focused = findFocus() ?: return
    if (focused === this) return
    val rect = Rect()
    focused.getDrawingRect(rect)
    offsetDescendantRectToMyCoords(focused, rect)
    // Leave most of a form row visible below the field so the submit button is not hidden.
    val margin = (120 * resources.displayMetrics.density).toInt()
    val visibleBottom = height - imeHeight - margin
    val overflow = rect.bottom - visibleBottom
    if (overflow > 0) smoothScrollBy(0, overflow)
}

/**
 * Edge-to-edge inset handling for screens that have a bottom tab bar.
 *
 * Status bar and side insets pad [rootId], but the navigation bar inset does not: it is added to
 * the tab bar's own bottom padding (so its background runs all the way to the bottom of the
 * screen, behind the system navigation bar) and to [scrollId]'s bottom padding (so scrolled
 * content still clears the bar).
 *
 * The IME is deliberately not handled here: `adjustResize` shrinks the window instead, and
 * Material's BottomNavigationView already pads itself for the keyboard, so adding IME padding to
 * the root would double-count it and collapse the screen.
 */
fun Activity.applyEdgeToEdgeInsets(
    rootId: Int,
    tabBarId: Int? = null,
    scrollId: Int? = null,
) {
    val root = findViewById<View>(rootId)
    val tabBar = tabBarId?.let { findViewById<View>(it) }
    val scroll = scrollId?.let { findViewById<View>(it) }
    val tabBarPadding = tabBar?.paddingBottom ?: 0
    val scrollPadding = scroll?.paddingBottom ?: 0

    // Stop the system painting its light contrast scrim over the tab bar area (3-button nav).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, 0)
        tabBar?.updatePadding(bottom = tabBarPadding + bars.bottom)
        scroll?.updatePadding(bottom = scrollPadding + bars.bottom)
        insets
    }
}
