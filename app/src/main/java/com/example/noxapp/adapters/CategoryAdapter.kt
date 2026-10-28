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
import com.example.noxapp.models.CategoryItem

class CategoryAdapter(
    private var categories: List<CategoryItem>,
    private val onCategoryClick: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.categoryIconCard)
        val icon: ImageView = view.findViewById(R.id.categoryIconView)
        val name: TextView = view.findViewById(R.id.categoryNameView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = categories[position]
        holder.name.text = item.name
        holder.icon.setImageResource(item.iconResId)

        val context = holder.itemView.context
        if (item.isSelected) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.nox_primary_green))
            holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.white))
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.nox_primary_green))
        } else {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.nox_chip_bg))
            holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.nox_dark_green))
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.nox_dark_green))
        }

        holder.itemView.setOnClickListener {
            setSelected(position)
            onCategoryClick(item)
        }
    }

    override fun getItemCount(): Int = categories.size

    @SuppressLint("NotifyDataSetChanged")
    fun setSelected(position: Int) {
        categories.forEachIndexed { index, cat ->
            cat.isSelected = (index == position)
        }
        notifyDataSetChanged()
    }
}