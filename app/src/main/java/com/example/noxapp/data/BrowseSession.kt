package com.example.noxapp.data

import com.example.noxapp.models.BrowseFilters

/** Keeps the Browse tab's filters alive while the user visits Home or Profile. */
object BrowseSession {
    private var userId: String? = null
    private var filters: BrowseFilters = BrowseFilters()
    private var categoriesExpanded = false

    fun save(userId: String?, filters: BrowseFilters, categoriesExpanded: Boolean) {
        if (userId == null) return
        if (this.userId != userId) {
            this.userId = userId
            this.filters = BrowseFilters()
            this.categoriesExpanded = false
        }
        this.filters = filters
        this.categoriesExpanded = categoriesExpanded
    }

    fun restore(userId: String?): Pair<BrowseFilters, Boolean>? {
        if (userId == null || userId != this.userId) return null
        return filters to categoriesExpanded
    }

    fun clear() {
        userId = null
        filters = BrowseFilters()
        categoriesExpanded = false
    }
}
