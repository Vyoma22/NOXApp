package com.example.noxapp.listing

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.core.onPageSnapped
import com.example.noxapp.listing.adapters.MediaPagerAdapter
import com.example.noxapp.listing.adapters.MediaThumbAdapter

class MediaGallery(
    private val activity: Activity,
    private val dialog: Dialog,
    imageUrls: List<String>
) {
    private val imageUrls: List<String> = imageUrls.filter { it.isNotBlank() }
    private val pager: RecyclerView = dialog.findViewById(R.id.detailMediaPager)
    private val previousButton: ImageButton = dialog.findViewById(R.id.detailMediaPrev)
    private val nextButton: ImageButton = dialog.findViewById(R.id.detailMediaNext)
    private val counter: TextView = dialog.findViewById(R.id.detailMediaCounter)
    private val thumbnails: RecyclerView = dialog.findViewById(R.id.detailMediaThumbnails)
    private val emptyState: View = dialog.findViewById(R.id.detailMediaEmpty)

    private val pagerLayoutManager =
        LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
    private val thumbAdapter = MediaThumbAdapter(this.imageUrls) { position -> goTo(position) }
    private val pagerAdapter = MediaPagerAdapter(this.imageUrls) { position -> openViewer(position) }

    private var currentPosition = 0

    fun bind() {
        if (imageUrls.isEmpty()) {
            showEmptyState()
            return
        }
        pager.isVisible = true
        emptyState.isVisible = false
        pager.layoutManager = pagerLayoutManager
        pager.adapter = pagerAdapter
        pager.itemAnimator = null
        pager.onPageSnapped(pagerLayoutManager) { position ->
            if (position != currentPosition) showPosition(position)
        }
        thumbnails.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        thumbnails.adapter = thumbAdapter
        thumbnails.itemAnimator = null

        previousButton.setOnClickListener { goTo(currentPosition - 1) }
        nextButton.setOnClickListener { goTo(currentPosition + 1) }

        val multiple = imageUrls.size > 1
        previousButton.isVisible = multiple
        nextButton.isVisible = multiple
        counter.isVisible = multiple
        thumbnails.isVisible = multiple
        showPosition(0)
    }

    private fun goTo(position: Int) {
        if (imageUrls.isEmpty()) return
        val target = position.coerceIn(0, imageUrls.lastIndex)
        showPosition(target)
        pager.smoothScrollToPosition(target)
    }

    private fun showPosition(position: Int) {
        if (imageUrls.isEmpty()) return
        currentPosition = position.coerceIn(0, imageUrls.lastIndex)
        counter.text =
            activity.getString(R.string.detail_media_counter, currentPosition + 1, imageUrls.size)
        counter.contentDescription = activity.getString(
            R.string.detail_media_counter_description,
            currentPosition + 1,
            imageUrls.size
        )

        previousButton.isEnabled = currentPosition > 0
        nextButton.isEnabled = currentPosition < imageUrls.lastIndex
        previousButton.alpha = if (previousButton.isEnabled) 1f else 0.35f
        nextButton.alpha = if (nextButton.isEnabled) 1f else 0.35f

        thumbAdapter.setSelected(currentPosition)
        thumbnails.scrollToPosition(currentPosition)
    }

    private fun showEmptyState() {
        emptyState.isVisible = true
        pager.isVisible = false
        previousButton.isVisible = false
        nextButton.isVisible = false
        counter.isVisible = false
        thumbnails.isVisible = false
    }

    private fun openViewer(position: Int) {
        val url = imageUrls.getOrNull(position) ?: return
        if (MediaHelper.isVideoUrl(url)) {
            MediaHelper.playVideo(activity, url)
            return
        }

        activity.startActivity(
            Intent(activity, MediaViewerActivity::class.java)
                .putStringArrayListExtra(
                    MediaViewerActivity.EXTRA_IMAGE_URLS,
                    ArrayList(imageUrls)
                )
                .putExtra(MediaViewerActivity.EXTRA_START_INDEX, position)
        )
    }
}