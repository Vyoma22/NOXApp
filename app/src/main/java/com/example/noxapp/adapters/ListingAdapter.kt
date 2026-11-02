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
    savedIds: Set<String> = emptySet(),
    private val onFavoriteClick: (Listing, Boolean) -> Unit,
    private val onItemClick: (Listing) -> Unit
) : RecyclerView.Adapter<ListingAdapter.ViewHolder>() {

    // A private snapshot, so an optimistic mutation in the screen can still be diffed here.
    private var savedIds: Set<String> = savedIds.toSet()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.listingCardContainer)
        val image: ImageView = view.findViewById(R.id.itemImageView)
        val title: TextView = view.findViewById(R.id.itemTitleTextView)
        val price: TextView = view.findViewById(R.id.itemPriceTextView)
        val favoriteBtn: View = view.findViewById(R.id.favoriteButton)
        val favoriteIcon: ImageView = view.findViewById(R.id.favoriteIcon)

        /** Non-zero only for cards inside a horizontal carousel; 0 means "not a carousel item". */
        var carouselGapPx: Int = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listing_card, parent, false)
        val holder = ViewHolder(view)

        // If parent is a horizontal RecyclerView (Home feed), set a fixed width of 150dp
        if (parent is RecyclerView && (parent.layoutManager as? LinearLayoutManager)?.orientation == LinearLayoutManager.HORIZONTAL) {
            val widthInPx = (150 * parent.context.resources.displayMetrics.density).toInt()
            holder.carouselGapPx = (12 * parent.context.resources.displayMetrics.density).toInt()
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
        holder.price.text = "$${item.price.toInt()}"

        // The gap closes each card off, but on the last card it would widen the row's end inset,
        // so it is dropped there to mirror the padding before the first card.
        if (holder.carouselGapPx > 0) {
            val lp = holder.itemView.layoutParams as RecyclerView.LayoutParams
            val endGap = if (position == items.lastIndex) 0 else holder.carouselGapPx
            if (lp.marginEnd != endGap) {
                lp.marginEnd = endGap
                holder.itemView.layoutParams = lp
            }
        }

        holder.image.load(item.imageUrls.firstOrNull()) {
            crossfade(true)
            placeholder(R.color.nox_field_bg)
            error(R.color.nox_field_bg)
        }

        // Alternate colors
        val bgColor = if (position % 2 == 0) R.color.nox_card_pink else R.color.nox_card_green
        holder.card.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))

        // The saved state is read from the set the screen owns, so a tap updates both at once.
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

        // Heart click event
        holder.favoriteBtn.setOnClickListener {
            // Acknowledge the tap now; the screen owns the optimistic saved set.
            holder.favoriteIcon.pop()
            onFavoriteClick(item, !savedIds.contains(item.id))
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

    /** Rebinds only the hearts whose saved state changed, so photos and taps stay stable. */
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
