package com.example.noxapp.models

import com.example.noxapp.R

/**
 * The one category list the whole app reads from.
 *
 * Home, Browse, and the post spinner use these values so a category can always be
 * filtered wherever it can be chosen.
 */
object CategoryCatalog {
    val ALL = CategoryItem("All", R.drawable.ic_cat_all, isSelected = true)

    val CATEGORIES: List<CategoryItem> = listOf(
        CategoryItem("Books", R.drawable.ic_cat_books),
        CategoryItem("Electronics", R.drawable.ic_cat_electronics),
        CategoryItem("Furniture", R.drawable.ic_cat_furniture),
        CategoryItem("Clothing", R.drawable.ic_cat_clothing),
        CategoryItem("Decor", R.drawable.ic_cat_decor),
        CategoryItem("Accessories", R.drawable.ic_cat_accessories),
        CategoryItem("Tickets", R.drawable.ic_cat_tickets),
        CategoryItem("Household", R.drawable.ic_cat_household),
        CategoryItem("Sporting", R.drawable.ic_cat_sporting),
        CategoryItem("Tools", R.drawable.ic_cat_tools),
        CategoryItem("Miscellaneous", R.drawable.ic_cat_misc)
    )

    val NAMES: List<String> = CATEGORIES.map { it.name }

    /** The icon for a category name, falling back to the All icon for unknown values. */
    fun iconFor(name: String?): Int =
        if (name == null) ALL.iconResId
        else CATEGORIES.firstOrNull { it.name.equals(name, ignoreCase = true) }?.iconResId
            ?: ALL.iconResId

    /** A fresh row for Home, with All selected and every category reachable by scrolling. */
    fun homeRow(): List<CategoryItem> =
        listOf(ALL.copy(isSelected = true)) + CATEGORIES.map { it.copy() }
}
