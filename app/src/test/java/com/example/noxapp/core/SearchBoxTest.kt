package com.example.noxapp.core

import android.app.Activity
import android.app.Application
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.noxapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SearchBoxTest {

    private fun screen(): Activity {
        val activity = Robolectric.buildActivity(Activity::class.java).create().visible().get()
        val root = LinearLayout(activity)
        val search = EditText(activity)
        search.id = R.id.browseSearch
        val clear = ImageButton(activity)
        clear.id = R.id.browseSearchClear
        root.addView(search)
        root.addView(clear)
        activity.setContentView(root)
        return activity
    }

    @Test
    fun anInitialQueryIsShownWithItsClearButton() {
        val activity = screen()
        val changes = mutableListOf<String>()

        activity.wireSearchBox(R.id.browseSearch, R.id.browseSearchClear, "calculus") { changes.add(it) }

        val search = activity.findViewById<EditText>(R.id.browseSearch)
        assertEquals("calculus", search.text.toString())
        assertTrue(activity.findViewById<ImageButton>(R.id.browseSearchClear).isVisible)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun typingReportsTheTextAndRevealsTheClearButton() {
        val activity = screen()
        val changes = mutableListOf<String>()
        activity.wireSearchBox(R.id.browseSearch, R.id.browseSearchClear, "") { changes.add(it) }
        val search = activity.findViewById<EditText>(R.id.browseSearch)
        val clear = activity.findViewById<ImageButton>(R.id.browseSearchClear)
        assertFalse(clear.isVisible)

        search.setText("desk")

        assertEquals(listOf("desk"), changes)
        assertTrue(clear.isVisible)
    }

    @Test
    fun theClearButtonEmptiesTheFieldAndReportsIt() {
        val activity = screen()
        val changes = mutableListOf<String>()
        activity.wireSearchBox(R.id.browseSearch, R.id.browseSearchClear, "lamp") { changes.add(it) }
        val search = activity.findViewById<EditText>(R.id.browseSearch)
        val clear = activity.findViewById<ImageButton>(R.id.browseSearchClear)

        clear.performClick()

        assertEquals("", search.text.toString())
        assertEquals(listOf(""), changes)
        assertFalse(clear.isVisible)
    }
}
