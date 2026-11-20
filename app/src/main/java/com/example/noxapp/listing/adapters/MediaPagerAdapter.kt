package com.example.noxapp.listing.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.listing.MediaHelper

class MediaPagerAdapter(
    imageUrls: List<String>,
    private val pageLayout: Int = R.layout.item_media_page,
    private val descriptionFormat: Int = R.string.detail_media_photo_description,
    private val imageBackgroundColor: Int = R.color.nox_field_bg,
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

        holder.image.contentDescription = context.getString(
            descriptionFormat,
            position + 1,
            imageUrls.size
        )
        MediaHelper.loadThumbnail(holder.image, url)
        holder.image.setOnClickListener(
            onPhotoClick?.let { click -> View.OnClickListener { click(position) } }
        )
    }

    override fun getItemCount(): Int = imageUrls.size
}