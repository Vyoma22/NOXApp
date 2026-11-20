package com.example.noxapp.listing.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.core.dp
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingSignals
import com.example.noxapp.listing.MediaHelper
import com.example.noxapp.listing.formatPrice
import com.google.android.material.card.MaterialCardView

class ListingAdapter(
    private var items: List<Listing>,
    savedIds: Set<String> = emptySet(),
    private val onFavoriteClick: (Listing, Boolean) -> Unit,
    private val onItemClick: (Listing) -> Unit
) : RecyclerView.Adapter<ListingAdapter.ViewHolder>() {

    private var savedIds: Set<String> = savedIds.toSet()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.listingCardContainer)
        val image: ImageView = view.findViewById(R.id.itemImageView)
        val title: TextView = view.findViewById(R.id.itemTitleTextView)
        val price: TextView = view.findViewById(R.id.itemPriceTextView)
        val favoriteBtn: View = view.findViewById(R.id.favoriteButton)
        val favoriteIcon: ImageView = view.findViewById(R.id.favoriteIcon)
        val badgeRow: View = view.findViewById(R.id.cardBadgeRow)
        val popularBadge: TextView = view.findViewById(R.id.cardPopularBadge)
        val priceDropBadge: TextView = view.findViewById(R.id.cardPriceDropBadge)

        var carouselGapPx: Int = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listing_card, parent, false)
        val holder = ViewHolder(view)

        if (parent is RecyclerView && (parent.layoutManager as? LinearLayoutManager)?.orientation == LinearLayoutManager.HORIZONTAL) {
            val widthInPx = parent.context.dp(150)
            holder.carouselGapPx = parent.context.dp(12)
            view.layoutParams = RecyclerView.LayoutParams(widthInPx, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = holder.carouselGapPx
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.title.text = item.title
        holder.price.text = "$${formatPrice(item.price)}"
        bindBadges(holder, item)

        if (holder.carouselGapPx > 0) {
            val lp = holder.itemView.layoutParams as RecyclerView.LayoutParams
            val endGap = if (position == items.lastIndex) 0 else holder.carouselGapPx
            if (lp.marginEnd != endGap) {
                lp.marginEnd = endGap
                holder.itemView.layoutParams = lp
            }
        }

        // Loads both image URLs and video thumbnails
        MediaHelper.loadThumbnail(holder.image, item.imageUrls.firstOrNull())

        val bgColor = if (position % 2 == 0) R.color.nox_card_pink else R.color.nox_card_green
        holder.card.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))

        val isSaved = savedIds.contains(item.id)
        if (isSaved) {
            holder.favoriteIcon.setImageResource(R.drawable.ic_heart_filled)
            holder.favoriteIcon.setColorFilter(ContextCompat.getColor(context, R.color.nox_accent_text))
        } else {
            holder.favoriteIcon.setImageResource(R.drawable.ic_heart_outline)
            holder.favoriteIcon.setColorFilter(ContextCompat.getColor(context, R.color.nox_dark_green))
        }
        holder.favoriteBtn.contentDescription =
            context.getString(if (isSaved) R.string.saved_item else R.string.save_item)

        holder.favoriteBtn.setOnClickListener {
            onFavoriteClick(item, !savedIds.contains(item.id))
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    private fun bindBadges(holder: ViewHolder, item: Listing) {
        val context = holder.itemView.context
        val popular = ListingSignals.isPopular(item)
        val priceDrop = ListingSignals.priceDropAmount(item)
        holder.badgeRow.isVisible = popular || priceDrop != null
        holder.popularBadge.isVisible = popular
        holder.priceDropBadge.isVisible = priceDrop != null
        if (popular) {
            holder.popularBadge.contentDescription =
                context.getString(R.string.listing_badge_popular_description)
        }
        if (priceDrop != null) {
            holder.priceDropBadge.text =
                context.getString(R.string.listing_badge_price_drop, priceDrop)
            holder.priceDropBadge.contentDescription =
                context.getString(R.string.listing_badge_price_drop_description, priceDrop)
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<Listing>) {
        items = newList
        notifyDataSetChanged()
    }

    fun updateSavedIds(newSavedIds: Set<String>) {
        val old = savedIds
        val updated = newSavedIds.toSet()
        savedIds = updated
        items.forEachIndexed { index, item ->
            if (old.contains(item.id) != updated.contains(item.id)) {
                notifyItemChanged(index)
            }
        }
    }
}