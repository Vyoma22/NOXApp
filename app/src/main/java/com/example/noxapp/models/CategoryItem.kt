package com.example.noxapp.models

data class CategoryItem(
    val name: String,
    val iconResId: Int,
    var isSelected: Boolean = false
)