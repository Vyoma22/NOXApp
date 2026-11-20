package com.example.noxapp.browse

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.R
import com.example.noxapp.browse.adapters.PillChipAdapter
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.PermissionHelper
import com.example.noxapp.core.Prefs
import com.example.noxapp.core.SaveToggle
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.openTab
import com.example.noxapp.core.wireSearchBox
import com.example.noxapp.core.wireTabBar
import com.example.noxapp.listing.BrowseFilters
import com.example.noxapp.listing.CampusLocations
import com.example.noxapp.listing.ItemDetailDialog
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingFilters
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.PriceRange
import com.example.noxapp.listing.SortOption
import com.example.noxapp.listing.adapters.ListingAdapter
import com.example.noxapp.map.MapActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import java.util.Locale

class BrowseActivity : AppCompatActivity() {

    private var filters = BrowseFilters()
    private var categoriesExpanded = false
    private var mapVisitInProgress = false
    private var favoritesGeneration = 0
    private var accessAllowed = false
    private var accessJob: Job? = null
    private var allListings: List<Listing> = emptyList()
    private var savedIds = mutableSetOf<String>()
    private var listingsLoading = false
    private var listingsLoaded = false
    private var listingsError = false
    private var pendingScrollY: Int? = null
    private var loadErrorSnackbar: Snackbar? = null
    private var userCampus: Pair<Double, Double>? = null

    private lateinit var browseScroll: NestedScrollView
    private lateinit var browseRefresh: SwipeRefreshLayout
    private lateinit var browseSearch: EditText
    private lateinit var categoryAdapter: PillChipAdapter
    private lateinit var listingAdapter: ListingAdapter

    private val saveToggle = SaveToggle(
        this, R.id.browseRoot, R.id.bottomNavBar,
        R.string.browse_saved, R.string.browse_unsaved, ::applySavedLocally
    )

    private val voiceSearchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                browseSearch.setText(spoken)
                browseSearch.setSelection(spoken.length)
            } else {
                Toast.makeText(this, "No speech recognized.", Toast.LENGTH_SHORT).show()
            }
        }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchVoiceSearch()
            } else {
                val permanent = PermissionHelper.isPermanentlyDenied(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO)
                )
                PermissionHelper.showDeniedDialog(
                    activity = this,
                    title = "Microphone Permission Needed",
                    message = if (permanent) {
                        "Microphone access is disabled. Enable it in Settings to use voice search, or type your search instead."
                    } else {
                        "NOX needs microphone access to search listings with your voice."
                    },
                    permanentlyDenied = permanent,
                    onRetry = { startVoiceSearchWithPermission() }
                )
            }
        }

    private val mapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            mapVisitInProgress = false
            val data = result.data ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            if (data.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false)) {
                val lat = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, Double.NaN)
                val lng = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LNG, Double.NaN)
                val radius = data.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, Double.NaN)
                if (validArea(lat, lng, radius)) {
                    filters = filters.copy(centerLat = lat, centerLng = lng, radiusKm = radius)
                    renderState()
                }
            }
            when (data.getStringExtra(MapActivity.EXTRA_NAVIGATION)) {
                MapActivity.NAV_HOME -> openTab(NavTab.HOME)
                MapActivity.NAV_PROFILE -> openTab(NavTab.PROFILE)
                MapActivity.NAV_CHAT -> openTab(NavTab.CHAT)
                MapActivity.NAV_POST -> openTab(NavTab.POST)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_browse)
        applyEdgeToEdgeInsets(R.id.browseRoot, R.id.bottomNavBar)

        browseScroll = findViewById(R.id.browseScroll)
        browseRefresh = findViewById(R.id.browseRefresh)
        browseSearch = findViewById(R.id.browseSearch)

        restoreState(savedInstanceState)
        setupCategories()
        setupResults()
        setupFilterMenus()
        setupBottomNavigation()

        findViewById<View>(R.id.browseBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.browseMapButton).setOnClickListener { openMap() }
        findViewById<View>(R.id.browseClearRadius).setOnClickListener { clearRadius() }
        findViewById<View>(R.id.browseClearAll).setOnClickListener { clearAllFilters() }
        findViewById<View>(R.id.browseErrorRetry).setOnClickListener { loadListings() }
        findViewById<View>(R.id.browseEmptyAction).setOnClickListener {
            if (allListings.isEmpty()) loadListings() else clearAllFilters()
        }
        browseRefresh.setOnRefreshListener { loadListings() }

        setupSearch()
        renderState()
    }

    override fun onStart() {
        super.onStart()
        accessAllowed = false
        accessJob?.cancel()
        accessJob = checkAccessOnStart {
            accessAllowed = true
            loadFavorites()
            loadUserCampus()
            if (!listingsLoaded && !listingsLoading) loadListings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (listingsLoaded) loadListings()
    }

    override fun onStop() {
        accessAllowed = false
        accessJob?.cancel()
        favoritesGeneration++
        super.onStop()
    }

    private fun setupCategories() {
        categoryAdapter = PillChipAdapter(
            onCategorySelected = { category ->
                filters = filters.copy(category = category)
                renderState()
            },
            onExpansionToggle = {
                categoriesExpanded = !categoriesExpanded
                categoryAdapter.updateState(filters.category, categoriesExpanded)
                saveSession()
            }
        )
        findViewById<RecyclerView>(R.id.browseCategories).apply {
            layoutManager = LinearLayoutManager(this@BrowseActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupResults() {
        listingAdapter = ListingAdapter(
            emptyList(),
            savedIds,
            onFavoriteClick = { listing, shouldSave -> saveToggle.toggle(listing, shouldSave) },
            onItemClick = { listing -> ItemDetailDialog.show(this, listing) { loadListings() } }
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        findViewById<RecyclerView>(R.id.browseResults).apply {
            layoutManager = GridLayoutManager(this@BrowseActivity, 2)
            isNestedScrollingEnabled = false
            adapter = listingAdapter
        }
    }

    private fun setupSearch() {
        val voice = findViewById<ImageButton>(R.id.browseSearchVoice)
        val clear = findViewById<ImageButton>(R.id.browseSearchClear)

        fun syncSearchIcons(query: String) {
            val hasText = query.isNotEmpty()
            voice.isVisible = !hasText
            clear.isVisible = hasText
        }

        syncSearchIcons(filters.query)
        wireSearchBox(R.id.browseSearch, R.id.browseSearchClear, filters.query) { text ->
            syncSearchIcons(text)
            filters = filters.copy(query = text)
            renderResults()
            saveSession()
        }

        voice.setOnClickListener { startVoiceSearchWithPermission() }
    }

    private fun startVoiceSearchWithPermission() {
        if (PermissionHelper.hasAll(this, Manifest.permission.RECORD_AUDIO)) {
            launchVoiceSearch()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        if (intent.resolveActivity(packageManager) != null) {
            voiceSearchLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Speech recognition is not available.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFilterMenus() {
        findViewById<View>(R.id.browsePriceFilter).setOnClickListener { anchor ->
            showFilterMenu(anchor, PriceRange.entries.map { it.label }, filters.priceRange?.label) { label ->
                filters = filters.copy(priceRange = PriceRange.entries.firstOrNull { it.label == label })
                renderState()
            }
        }
        findViewById<View>(R.id.browseConditionFilter).setOnClickListener { anchor ->
            showFilterMenu(anchor, resources.getStringArray(R.array.browse_conditions).toList(), filters.condition) { condition ->
                filters = filters.copy(condition = condition)
                renderState()
            }
        }
        findViewById<View>(R.id.browseSortFilter).setOnClickListener { anchor ->
            showSortMenu(anchor)
        }
    }

    private fun showFilterMenu(anchor: View, options: List<String>, current: String?, onSelect: (String?) -> Unit) {
        val values = listOf<String?>(null) + options
        PopupMenu(this, anchor).apply {
            values.forEachIndexed { index, value ->
                menu.add(0, index, index, value ?: getString(R.string.browse_any)).apply {
                    isCheckable = true
                    isChecked = value.equals(current, ignoreCase = true)
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                onSelect(values[item.itemId])
                true
            }
            show()
        }
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            SortOption.entries.forEachIndexed { index, option ->
                menu.add(0, index, index, getString(Prefs.sortLabelRes(option))).apply {
                    isCheckable = true
                    isChecked = option == filters.sort
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                filters = filters.copy(sort = SortOption.entries[item.itemId])
                renderState()
                true
            }
            show()
        }
    }

    private fun renderState() {
        categoryAdapter.updateState(filters.category, categoriesExpanded)
        renderFilterLabel(R.id.browsePriceFilter, R.string.browse_price, filters.priceRange?.label)
        renderFilterLabel(R.id.browseConditionFilter, R.string.browse_condition, filters.condition)

        val sortOption = filters.sort
        findViewById<TextView>(R.id.browseSortFilter).apply {
            text = getString(Prefs.sortLabelRes(sortOption))
            isSelected = sortOption != SortOption.NEWEST
        }

        val hasArea = validArea(filters.centerLat, filters.centerLng, filters.radiusKm)
        findViewById<View>(R.id.browseRadiusPill).apply {
            isVisible = hasArea
            isSelected = hasArea
        }
        if (hasArea) {
            val distance = Prefs.formatDistance(filters.radiusKm!!)
            findViewById<TextView>(R.id.browseRadiusLabel).text =
                getString(R.string.browse_radius, distance, Prefs.distanceUnit(this))
        }

        val hasFilters = filters.query.isNotBlank() || filters.category != null ||
                filters.priceRange != null || filters.condition != null || hasArea
        findViewById<View>(R.id.browseActiveRow).isVisible = hasFilters
        findViewById<View>(R.id.browseClearAll).isVisible = hasFilters

        findViewById<MaterialButton>(R.id.browseMapButton).apply {
            isSelected = hasArea
            val color = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@BrowseActivity,
                    if (hasArea) R.color.nox_accent_text else R.color.nox_dark_green
                )
            )
            iconTint = color
            setTextColor(color)
        }

        saveSession()
        renderResults()
    }

    private fun renderFilterLabel(viewId: Int, labelId: Int, value: String?) {
        findViewById<TextView>(viewId).apply {
            text = if (value == null) getString(labelId)
            else getString(R.string.browse_active_filter, getString(labelId), value)
            isSelected = value != null
        }
    }

    private fun renderResults() {
        val results = ListingFilters.apply(allListings, filters)
        listingAdapter.updateList(results)

        findViewById<TextView>(R.id.browseResultCount).text = if (listingsLoading) {
            getString(R.string.browse_loading)
        } else {
            resources.getQuantityString(R.plurals.browse_result_count, results.size, results.size)
        }
        findViewById<View>(R.id.browseLoading).isVisible = listingsLoading
        findViewById<View>(R.id.browseErrorState).isVisible = listingsError
        findViewById<View>(R.id.browseResults).isVisible = results.isNotEmpty() && !listingsError
        findViewById<View>(R.id.browseEmptyState).isVisible =
            listingsLoaded && !listingsLoading && !listingsError && results.isEmpty()

        val noListings = allListings.isEmpty()
        findViewById<TextView>(R.id.browseEmptyTitle)
            .setText(if (noListings) R.string.browse_empty_no_items_title else R.string.browse_empty_title)
        findViewById<TextView>(R.id.browseEmptyHint)
            .setText(if (noListings) R.string.browse_empty_no_items_hint else R.string.browse_empty_hint)
        findViewById<TextView>(R.id.browseEmptyAction)
            .setText(if (noListings) R.string.browse_empty_action_refresh else R.string.browse_empty_action_clear)
    }

    private fun clearAllFilters() {
        filters = filters.copy(
            query = "",
            category = null,
            priceRange = null,
            condition = null,
            centerLat = null,
            centerLng = null,
            radiusKm = null
        )
        categoriesExpanded = false
        browseSearch.setText("")
        renderState()
    }

    private fun loadListings() {
        listingsLoading = true
        listingsError = false
        loadErrorSnackbar?.dismiss()
        renderResults()

        ListingRepo.loadPublicListings(
            onSuccess = { listings ->
                if (isActivityGone()) return@loadPublicListings
                allListings = listings
                listingsLoading = false
                listingsLoaded = true
                browseRefresh.isRefreshing = false
                renderResults()
                pendingScrollY?.let { scrollY ->
                    browseScroll.doOnNextLayout { browseScroll.scrollTo(0, scrollY) }
                    browseScroll.requestLayout()
                    pendingScrollY = null
                }
            },
            onError = {
                if (isActivityGone()) return@loadPublicListings
                allListings = emptyList()
                listingsLoading = false
                listingsLoaded = true
                listingsError = true
                browseRefresh.isRefreshing = false
                renderResults()
                loadErrorSnackbar = Snackbar.make(
                    findViewById(R.id.browseRoot),
                    R.string.browse_load_error,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAnchorView(R.id.bottomNavBar)
                    .setAction(R.string.browse_retry) { loadListings() }
                loadErrorSnackbar?.show()
            }
        )
    }

    private fun loadFavorites() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val generation = ++favoritesGeneration
        ListingRepo.getSavedItemIds(uid) { ids ->
            if (!isActivityGone() &&
                generation == favoritesGeneration &&
                FirebaseAuth.getInstance().currentUser?.uid == uid
            ) {
                savedIds = ids.toMutableSet()
                listingAdapter.updateSavedIds(savedIds)
            }
        }
    }

    private fun loadUserCampus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ListingRepo.getUserProfile(uid) { profile ->
            if (!isActivityGone()) {
                userCampus = CampusLocations.forSchool(profile?.school)
            }
        }
    }

    private fun applySavedLocally(listingId: String, saved: Boolean) {
        if (saved) savedIds.add(listingId) else savedIds.remove(listingId)
        listingAdapter.updateSavedIds(savedIds)
    }

    private fun openMap() {
        mapVisitInProgress = true
        val center = if (filters.centerLat != null && filters.centerLng != null) {
            filters.centerLat!! to filters.centerLng!!
        } else {
            userCampus ?: CampusLocations.ONTARIO_TECH
        }
        mapLauncher.launch(Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_CENTER_LAT, center.first)
            putExtra(MapActivity.EXTRA_CENTER_LNG, center.second)
            putExtra(MapActivity.EXTRA_RADIUS_KM, filters.radiusKm ?: 2.0)
            putExtra(MapActivity.EXTRA_QUERY, filters.query)
            putExtra(MapActivity.EXTRA_CATEGORY, filters.category)
            putExtra(MapActivity.EXTRA_PRICE, filters.priceRange?.name)
            putExtra(MapActivity.EXTRA_CONDITION, filters.condition)
        })
    }

    private fun clearRadius() {
        filters = filters.copy(centerLat = null, centerLng = null, radiusKm = null)
        renderState()
    }

    private fun setupBottomNavigation() {
        wireTabBar(R.id.bottomNavBar, NavTab.BROWSE, onReselect = {
            clearAllFilters()
            browseScroll.smoothScrollTo(0, 0)
        })
    }

    private fun restoreState(state: Bundle?) {
        val restored = Prefs.browseSession(this, FirebaseAuth.getInstance().currentUser?.uid)
        if (restored != null) {
            filters = restored.first
            categoriesExpanded = restored.second
        } else {
            filters = BrowseFilters(sort = Prefs.defaultSort(this))
        }
        intent.getStringExtra("query")?.takeIf { it.isNotBlank() }?.let {
            filters = filters.copy(query = it)
        }
        intent.getStringExtra("category")?.takeIf { it.isNotBlank() }?.let {
            filters = filters.copy(category = it)
        }
        mapVisitInProgress = state?.getBoolean("browse.mapVisit") ?: false
        pendingScrollY = state?.getInt("browse.scrollY")
    }

    private fun saveSession() {
        Prefs.saveBrowseSession(
            this,
            FirebaseAuth.getInstance().currentUser?.uid,
            filters,
            categoriesExpanded
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("browse.mapVisit", mapVisitInProgress)
        outState.putInt("browse.scrollY", pendingScrollY ?: browseScroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    private fun validArea(lat: Double?, lng: Double?, radius: Double?): Boolean =
        lat != null && lng != null && radius != null && lat.isFinite() && lng.isFinite() &&
                radius.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && radius > 0.0

    private fun isActivityGone(): Boolean = isDestroyed || isFinishing
}