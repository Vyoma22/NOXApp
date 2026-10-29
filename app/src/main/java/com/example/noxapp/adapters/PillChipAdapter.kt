package com.example.noxapp.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.R

class PillChipAdapter(
    private val onCategorySelected: (String?) -> Unit,
    private val onExpansionToggle: () -> Unit
) : RecyclerView.Adapter<PillChipAdapter.ViewHolder>() {
    private var selectedCategory: String? = null
    private var expanded = false

    class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_pill_chip, parent, false) as TextView
    )

    override fun getItemCount(): Int = if (expanded) CATEGORIES.size + 2 else 5

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = holder.text
        val context = text.context
        val isToggle = position == itemCount - 1
        val category = if (position == 0 || isToggle) null else CATEGORIES[position - 1]
        val hiddenSelection = !expanded && CATEGORIES.drop(3).any {
            it.equals(selectedCategory, ignoreCase = true)
        }
        val selected = when {
            isToggle -> hiddenSelection
            category == null -> selectedCategory == null
            else -> category.equals(selectedCategory, ignoreCase = true)
        }
        val label = when {
            isToggle -> context.getString(if (expanded) R.string.browse_less else R.string.browse_more)
            category == null -> context.getString(R.string.browse_all)
            else -> category
        }
        text.text = label
        text.isSelected = selected
        text.contentDescription = if (isToggle && hiddenSelection) {
            context.getString(R.string.browse_more_selected, label, selectedCategory)
        } else label
        text.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(
            context, if (selected) R.color.nox_primary_green else R.color.nox_field_bg
        ))
        text.setTextColor(ContextCompat.getColor(
            context, if (selected) R.color.white else R.color.nox_dark_green
        ))
        text.setOnClickListener {
            if (isToggle) onExpansionToggle() else onCategorySelected(category)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateState(category: String?, isExpanded: Boolean) {
        selectedCategory = category
        expanded = isExpanded
        notifyDataSetChanged()
    }

    companion object {
        // Canonical Listing.category values, shared by the inline row and Category popup.
        val CATEGORIES = listOf("Books", "Electronics", "Furniture", "Clothing", "Decor", "Accessories", "Tickets", "Other")
    }
}
