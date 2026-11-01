package com.example.noxapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
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

// insert handling, pads the root view
fun Activity.applySystemBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}

/**
 * Edge-to-edge inset handling for screens that have a bottom tab bar.
 *
 * Status bar and side insets pad [rootId], but the navigation bar inset does not: it is added to
 * the tab bar's own bottom padding (so its background runs all the way to the bottom of the
 * screen, behind the system navigation bar) and to [scrollId]'s bottom padding (so scrolled
 * content still clears the bar).
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
