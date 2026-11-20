package com.example.noxapp.listing

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import com.example.noxapp.R
import com.example.noxapp.core.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object MediaHelper {

    private val videoExtensions = listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".3gp")
    private val frameCache = LruCache<String, Bitmap>(40)

    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = url.substringBefore('?').lowercase(Locale.getDefault())
        return videoExtensions.any { path.endsWith(it) }
    }

    private fun isLocalVideoUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime?.startsWith("video/") == true) return true
        val lower = uri.toString().lowercase(Locale.getDefault())
        return videoExtensions.any { lower.contains(it) }
    }

    /** Loads images via Coil, extracts local video frames immediately, and loads/falls back for remote videos. */
    fun loadThumbnail(imageView: ImageView, source: Any?) {
        imageView.tag = source
        if (source == null || (source is String && source.isBlank())) {
            imageView.setImageResource(R.color.nox_field_bg)
            return
        }

        // 1. Local Uri (PostListing preview strip before upload)
        if (source is Uri) {
            if (isLocalVideoUri(imageView.context, source)) {
                imageView.setImageResource(R.color.nox_field_bg)
                extractFrameAsync(imageView, source)
            } else {
                imageView.load(source) {
                    crossfade(true)
                    placeholder(R.color.nox_field_bg)
                    error(R.color.nox_field_bg)
                }
            }
            return
        }

        // 2. Remote URL
        val url = source.toString()
        if (!isVideoUrl(url)) {
            imageView.load(url) {
                crossfade(true)
                placeholder(R.color.nox_field_bg)
                error(R.color.nox_field_bg)
            }
            return
        }

        // Try companion "_thumb.jpg" first; if missing (older videos), fall back to extracting frame from video URL
        val thumbUrl = url.replaceFirst(
            Regex("\\.(mp4|mov|webm|mkv|avi|3gp)(?=\\?|$)", RegexOption.IGNORE_CASE),
            "_thumb.jpg"
        )
        imageView.load(thumbUrl) {
            crossfade(true)
            placeholder(R.color.nox_field_bg)
            listener(onError = { _, _ ->
                if (imageView.tag == source) {
                    extractFrameAsync(imageView, url)
                }
            })
        }
    }

    private fun extractFrameAsync(imageView: ImageView, source: Any) {
        val key = source.toString()
        frameCache.get(key)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        val appContext = imageView.context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                when (source) {
                    is Uri -> retriever.setDataSource(appContext, source)
                    is String -> retriever.setDataSource(source, emptyMap())
                }
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame
            }.getOrNull()

            if (bitmap != null) {
                frameCache.put(key, bitmap)
                withContext(Dispatchers.Main) {
                    if (imageView.tag == source) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    /** Streams local Uris and remote Firebase URLs immediately using Media3 ExoPlayer. */
    @OptIn(UnstableApi::class)
    fun playVideo(context: Context, source: Any) {
        val uri = when (source) {
            is Uri -> source
            is String -> Uri.parse(source)
            else -> return
        }

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val spinner = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    spinner.isVisible = (state == Player.STATE_BUFFERING)
                }

                override fun onPlayerError(error: PlaybackException) {
                    spinner.isVisible = false
                    Toast.makeText(context, "Unable to play video", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            })
            prepare()
            playWhenReady = true
        }

        val playerView = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.player = player
            useController = true
        }

        val closeBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.TOP or Gravity.END).apply {
                topMargin = context.dp(16)
                marginEnd = context.dp(16)
            }
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setOnDismissListener {
            playerView.player = null
            player.release()
        }

        container.addView(playerView)
        container.addView(spinner)
        container.addView(closeBtn)
        dialog.setContentView(container)
        dialog.show()
    }
}