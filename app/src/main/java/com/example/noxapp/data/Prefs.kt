package com.example.noxapp.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.noxapp.R
import com.example.noxapp.models.SortOption
import java.text.NumberFormat
import java.util.Locale

/** Local, per-device settings. Account fields continue to live in Firestore. */
object Prefs {
    private const val FILE = "nox_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_SORT = "default_sort"

    enum class Theme(val labelRes: Int) {
        SYSTEM(R.string.settings_theme_system),
        LIGHT(R.string.settings_theme_light),
        DARK(R.string.settings_theme_dark)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun theme(context: Context): Theme = runCatching {
        Theme.valueOf(prefs(context).getString(KEY_THEME, Theme.SYSTEM.name)!!)
    }.getOrDefault(Theme.SYSTEM)

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

    fun defaultSort(context: Context): SortOption = runCatching {
        SortOption.valueOf(prefs(context).getString(KEY_SORT, SortOption.NEWEST.name)!!)
    }.getOrDefault(SortOption.NEWEST)

    fun setDefaultSort(context: Context, sort: SortOption) {
        prefs(context).edit().putString(KEY_SORT, sort.name).apply()
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
