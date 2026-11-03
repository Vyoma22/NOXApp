package com.example.noxapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.noxapp.R

/**
 * Pages a listing's photos into a snapped horizontal [RecyclerView].
 *
 * The item detail sheet and the fullscreen viewer share this adapter; the caller supplies the
 * page layout (rounded crop in the sheet, letterboxed on black in the viewer) and which string
 * describes a page to screen readers.
 */
class MediaPagerAdapter(
    imageUrls: List<String>,
    @LayoutRes private val pageLayout: Int = R.layout.item_media_page,
    private val descriptionFormat: Int = R.string.detail_media_photo_description,
    @ColorRes private val imageBackgroundColor: Int = R.color.nox_field_bg,
    private val onPhotoClick: ((position: Int) -> Unit)? = null
) : RecyclerView.Adapter<MediaPagerAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.mediaPageImage)
    }

    private val imageUrls: List<String> = imageUrls.filter { it.isNotBlank() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(pageLayout, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val context = holder.itemView.context
        val url = imageUrls[position]

        // A recycled page must never keep the previous photo's description or bitmap.
        holder.image.contentDescription = context.getString(
            descriptionFormat,
            position + 1,
            imageUrls.size
        )
        holder.image.load(url) {
            crossfade(true)
            placeholder(imageBackgroundColor)
            error(imageBackgroundColor)
        }
        holder.image.setOnClickListener(
            onPhotoClick?.let { click -> View.OnClickListener { click(position) } }
        )
    }

    override fun getItemCount(): Int = imageUrls.size
}
