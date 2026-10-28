package com.example.noxapp.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R
import com.example.noxapp.models.Listing
import androidx.recyclerview.widget.LinearLayoutManager

class ListingAdapter(
    private var items: List<Listing>,
    private var savedIds: Set<String> = emptySet(),
    private val onFavoriteClick: (Listing, Boolean) -> Unit,
    private val onItemClick: (Listing) -> Unit
) : RecyclerView.Adapter<ListingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.listingCardContainer)
        val image: ImageView = view.findViewById(R.id.itemImageView)
        val title: TextView = view.findViewById(R.id.itemTitleTextView)
        val price: TextView = view.findViewById(R.id.itemPriceTextView)
        val favoriteBtn: ImageView = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listing_card, parent, false)

        // If parent is a horizontal RecyclerView (Home feed), set a fixed width of 150dp
        if (parent is RecyclerView && (parent.layoutManager as? LinearLayoutManager)?.orientation == LinearLayoutManager.HORIZONTAL) {
            val widthInPx = (150 * parent.context.resources.displayMetrics.density).toInt()
            view.layoutParams = RecyclerView.LayoutParams(widthInPx, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * parent.context.resources.displayMetrics.density).toInt()
            }
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.title.text = item.title
        holder.price.text = "$${item.price.toInt()}"

        // Alternate colors
        val bgColor = if (position % 2 == 0) R.color.nox_card_pink else R.color.nox_card_green
        holder.card.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))

        // Check if item is saved
        // Check if item is saved and set proper heart icon
        val isSaved = savedIds.contains(item.id)
        if (isSaved) {
            holder.favoriteBtn.setImageResource(R.drawable.ic_heart_filled)
            holder.favoriteBtn.setColorFilter(ContextCompat.getColor(context, R.color.nox_primary_green))
        } else {
            holder.favoriteBtn.setImageResource(R.drawable.ic_heart_outline)
            holder.favoriteBtn.setColorFilter(ContextCompat.getColor(context, R.color.nox_dark_green))
        }

        // Heart click event
        holder.favoriteBtn.setOnClickListener {
            val newSavedState = !savedIds.contains(item.id)
            onFavoriteClick(item, newSavedState)
        }

        // Card click event
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<Listing>) {
        items = newList
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSavedIds(newSavedIds: Set<String>) {
        savedIds = newSavedIds
        notifyDataSetChanged()
    }
}