package com.example.noxapp.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.noxapp.R
import com.example.noxapp.listing.BrowseFilters
import com.example.noxapp.listing.PriceRange
import com.example.noxapp.listing.SortOption
import java.text.NumberFormat
import java.util.Locale

/** Local, per-device settings. Account fields continue to live in Firestore. */
object Prefs {
    private const val FILE = "nox_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_SORT = "default_sort"
    private const val KEY_BROWSE_USER = "browse_user"
    private const val KEY_BROWSE_QUERY = "browse_query"
    private const val KEY_BROWSE_CATEGORY = "browse_category"
    private const val KEY_BROWSE_PRICE = "browse_price"
    private const val KEY_BROWSE_CONDITION = "browse_condition"
    private const val KEY_BROWSE_LAT = "browse_lat"
    private const val KEY_BROWSE_LNG = "browse_lng"
    private const val KEY_BROWSE_RADIUS = "browse_radius"
    private const val KEY_BROWSE_SORT = "browse_sort"
    private const val KEY_BROWSE_EXPANDED = "browse_expanded"

    enum class Theme(val labelRes: Int) {
        SYSTEM(R.string.settings_theme_system),
        LIGHT(R.string.settings_theme_light),
        DARK(R.string.settings_theme_dark)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun theme(context: Context): Theme =
        Theme.entries.firstOrNull { it.name == prefs(context).getString(KEY_THEME, null) } ?: Theme.SYSTEM

    fun setTheme(context: Context, theme: Theme) {
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()
        applyTheme(context)
    }

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(context)) {
                Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    fun defaultSort(context: Context): SortOption =
        SortOption.entries.firstOrNull { it.name == prefs(context).getString(KEY_SORT, null) } ?: SortOption.NEWEST

    fun setDefaultSort(context: Context, sort: SortOption) {
        prefs(context).edit().putString(KEY_SORT, sort.name).apply()
    }

    /**
     * The Browse tab's filters and category expansion, kept per account so signing in as a
     * different student does not inherit the previous search. Null when nothing is stored for
     * [userId].
     */
    fun browseSession(context: Context, userId: String?): Pair<BrowseFilters, Boolean>? {
        if (userId == null) return null
        val stored = prefs(context)
        if (stored.getString(KEY_BROWSE_USER, null) != userId) return null
        val priceName = stored.getString(KEY_BROWSE_PRICE, null)
        val sortName = stored.getString(KEY_BROWSE_SORT, null)
        val filters = BrowseFilters(
            query = stored.getString(KEY_BROWSE_QUERY, "").orEmpty(),
            category = stored.getString(KEY_BROWSE_CATEGORY, null),
            priceRange = PriceRange.entries.firstOrNull { it.name == priceName },
            condition = stored.getString(KEY_BROWSE_CONDITION, null),
            centerLat = stored.getString(KEY_BROWSE_LAT, null)?.toDoubleOrNull(),
            centerLng = stored.getString(KEY_BROWSE_LNG, null)?.toDoubleOrNull(),
            radiusKm = stored.getString(KEY_BROWSE_RADIUS, null)?.toDoubleOrNull(),
            sort = SortOption.entries.firstOrNull { it.name == sortName } ?: SortOption.NEWEST
        )
        return filters to stored.getBoolean(KEY_BROWSE_EXPANDED, false)
    }

    // Coordinates are stored as text: SharedPreferences has no Double, and Float would round them.
    fun saveBrowseSession(
        context: Context,
        userId: String?,
        filters: BrowseFilters,
        categoriesExpanded: Boolean
    ) {
        if (userId == null) return
        prefs(context).edit()
            .putString(KEY_BROWSE_USER, userId)
            .putString(KEY_BROWSE_QUERY, filters.query)
            .putString(KEY_BROWSE_CATEGORY, filters.category)
            .putString(KEY_BROWSE_PRICE, filters.priceRange?.name)
            .putString(KEY_BROWSE_CONDITION, filters.condition)
            .putString(KEY_BROWSE_LAT, filters.centerLat?.toString())
            .putString(KEY_BROWSE_LNG, filters.centerLng?.toString())
            .putString(KEY_BROWSE_RADIUS, filters.radiusKm?.toString())
            .putString(KEY_BROWSE_SORT, filters.sort.name)
            .putBoolean(KEY_BROWSE_EXPANDED, categoriesExpanded)
            .apply()
    }

    fun clearBrowseSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_BROWSE_USER)
            .remove(KEY_BROWSE_QUERY)
            .remove(KEY_BROWSE_CATEGORY)
            .remove(KEY_BROWSE_PRICE)
            .remove(KEY_BROWSE_CONDITION)
            .remove(KEY_BROWSE_LAT)
            .remove(KEY_BROWSE_LNG)
            .remove(KEY_BROWSE_RADIUS)
            .remove(KEY_BROWSE_SORT)
            .remove(KEY_BROWSE_EXPANDED)
            .apply()
    }

    fun sortLabelRes(sort: SortOption): Int = when (sort) {
        SortOption.NEWEST -> R.string.sort_newest
        SortOption.PRICE_LOW -> R.string.sort_price_low
        SortOption.PRICE_HIGH -> R.string.sort_price_high
        SortOption.NEAREST -> R.string.sort_nearest
    }

    /** Distances are always shown in kilometres. */
    fun formatDistance(km: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
        }.format(km)

    fun distanceUnit(context: Context): String = context.getString(R.string.unit_km)
}
