package com.example.noxapp.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.adapters.MediaPagerAdapter

/**
 * Fullscreen photo viewer opened from the item detail gallery.
 *
 * The sheet passes the listing's photos and the tapped page, so the viewer opens on the photo the
 * user was already looking at. Swiping or the snapped [RecyclerView] moves through the rest.
 */
class MediaViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URLS = "extra_image_urls"
        const val EXTRA_START_INDEX = "extra_start_index"
    }

    private val snapHelper = PagerSnapHelper()
    private lateinit var pager: RecyclerView
    private lateinit var pagerLayoutManager: LinearLayoutManager
    private lateinit var counter: TextView
    private var imageUrls: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The viewer is always dark, so the bar icons stay light even when the app is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )
        imageUrls = intent.getStringArrayListExtra(EXTRA_IMAGE_URLS)
            .orEmpty()
            .filter { it.isNotBlank() }
        if (imageUrls.isEmpty()) {
            // Nothing to show: never open a blank black screen.
            finish()
            return
        }
        setContentView(R.layout.activity_media_viewer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        pager = findViewById(R.id.viewerPager)
        counter = findViewById(R.id.viewerCounter)
        pagerLayoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pager.layoutManager = pagerLayoutManager
        pager.adapter = MediaPagerAdapter(
            imageUrls = imageUrls,
            pageLayout = R.layout.item_media_viewer_page,
            descriptionFormat = R.string.viewer_photo_description,
            imageBackgroundColor = R.color.black
        )
        pager.itemAnimator = null
        snapHelper.attachToRecyclerView(pager)
        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val snapped = snapHelper.findSnapView(pagerLayoutManager) ?: return
                val position = pagerLayoutManager.getPosition(snapped)
                if (position != RecyclerView.NO_POSITION) showPosition(position)
            }
        })

        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            .coerceIn(0, imageUrls.lastIndex)
        showPosition(startIndex)
        pager.scrollToPosition(startIndex)

        findViewById<ImageButton>(R.id.viewerClose).setOnClickListener { finish() }
        applySystemBarInsets()
    }

    private fun showPosition(position: Int) {
        val current = position.coerceIn(0, imageUrls.lastIndex)
        counter.text = getString(R.string.detail_media_counter, current + 1, imageUrls.size)
        counter.contentDescription =
            getString(R.string.viewer_photo_description, current + 1, imageUrls.size)
    }

    /** Keeps the close button and counter clear of the status and navigation bars. */
    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.viewerRoot)
        val close = findViewById<ImageButton>(R.id.viewerClose)
        val closeParams = close.layoutParams as FrameLayout.LayoutParams
        val counterParams = counter.layoutParams as FrameLayout.LayoutParams
        val closeTop = closeParams.topMargin
        val closeEnd = closeParams.marginEnd
        val counterBottom = counterParams.bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            closeParams.topMargin = closeTop + bars.top
            closeParams.marginEnd = closeEnd + bars.right
            close.layoutParams = closeParams
            counterParams.bottomMargin = counterBottom + bars.bottom
            counter.layoutParams = counterParams
            insets
        }
    }
}
