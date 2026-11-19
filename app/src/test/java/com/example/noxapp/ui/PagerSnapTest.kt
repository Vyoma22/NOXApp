package com.example.noxapp.ui

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PagerSnapTest {

    private class PageAdapter(private val count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val page = TextView(parent.context)
            page.layoutParams = ViewGroup.LayoutParams(200, 200)
            return object : RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as TextView).text = "page $position"
        }

        override fun getItemCount(): Int = count
    }

    /**
     * The pager has to sit in a visible window: a detached view queues its animation callbacks
     * instead of posting them, so the smooth scroller would never run.
     */
    private fun laidOutPager(pages: Int): Pair<RecyclerView, LinearLayoutManager> {
        val activity = Robolectric.buildActivity(Activity::class.java).create().visible().get()
        val recycler = RecyclerView(activity)
        val manager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        recycler.layoutManager = manager
        recycler.adapter = PageAdapter(pages)
        activity.setContentView(recycler, ViewGroup.LayoutParams(200, 200))
        shadowOf(Looper.getMainLooper()).idle()
        return recycler to manager
    }

    @Test
    fun onPageSnappedInstallsAPagerSnapHelperAsTheFlingListener() {
        val (recycler, manager) = laidOutPager(3)
        assertNull(recycler.onFlingListener)

        recycler.onPageSnapped(manager) { }

        assertTrue(recycler.onFlingListener is PagerSnapHelper)
    }

    @Test
    fun settlingOnAPageReportsThatPosition() {
        val (recycler, manager) = laidOutPager(3)
        val reported = mutableListOf<Int>()
        recycler.onPageSnapped(manager) { reported.add(it) }

        recycler.smoothScrollToPosition(2)
        // The scroller reposts itself on every animation frame, so the clock has to advance for it
        // to reach rest; idle() alone only runs tasks that are already due.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertEquals(listOf(2), reported)
    }
}
