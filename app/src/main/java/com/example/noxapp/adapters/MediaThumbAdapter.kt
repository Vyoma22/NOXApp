package com.example.noxapp.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.noxapp.R

/**
 * Thumbnail strip under the item detail gallery. The selected photo keeps full opacity and a
 * green ring; the rest dim to about half so the current page reads at a glance.
 */
class MediaThumbAdapter(
    private val imageUrls: List<String>,
    private val onThumbClick: (position: Int) -> Unit
) : RecyclerView.Adapter<MediaThumbAdapter.ThumbViewHolder>() {

    class ThumbViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.mediaThumbImage)
    }

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_thumb, parent, false)
        return ThumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) {
        val context = holder.itemView.context
        val selected = position == selectedPosition

        holder.image.load(imageUrls[position]) {
            crossfade(true)
            placeholder(R.color.nox_field_bg)
            error(R.color.nox_field_bg)
        }
        holder.itemView.setBackgroundResource(
            if (selected) R.drawable.bg_media_thumb_selected else R.drawable.bg_media_thumb_normal
        )
        holder.image.alpha = if (selected) 1f else 0.55f
        holder.itemView.isSelected = selected
        holder.itemView.contentDescription =
            context.getString(R.string.detail_media_show_photo, position + 1)
        holder.itemView.setOnClickListener { onThumbClick(position) }
    }

    override fun getItemCount(): Int = imageUrls.size

    @SuppressLint("NotifyDataSetChanged")
    fun setSelected(position: Int) {
        if (position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(position)
    }
}
