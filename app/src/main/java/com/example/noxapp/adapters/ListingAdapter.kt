package com.example.noxapp.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.noxapp.R
import com.example.noxapp.models.Listing
import com.example.noxapp.ui.pop
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

        holder.image.load(item.imageUrls.firstOrNull()) {
            crossfade(true)
            placeholder(R.color.nox_field_bg)
            error(R.color.nox_field_bg)
        }

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
            // Acknowledge the tap now; the icon flips when Firestore answers.
            holder.favoriteBtn.pop()
            val newSavedState = !savedIds.contains(item.id)
            onFavoriteClick(item, newSavedState)
        }

        // Card click event
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // The card dips under the finger so a tap feels physical before the dialog opens.
        holder.itemView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
            }
            false
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