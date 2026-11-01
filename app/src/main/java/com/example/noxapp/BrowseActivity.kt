package com.example.noxapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.adapters.PillChipAdapter
import com.example.noxapp.auth.AccessState
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.data.BrowseSession
import com.example.noxapp.data.CampusLocations
import com.example.noxapp.data.ListingFilters
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.data.Prefs
import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.models.PriceRange
import com.example.noxapp.models.SortOption
import com.example.noxapp.ui.ItemDetailDialog
import com.example.noxapp.ui.animateChildrenIn
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.fadeVisibility
import com.example.noxapp.ui.goTo
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowseActivity : AppCompatActivity() {
    private var filters = BrowseFilters()
    private var categoriesExpanded = false
    private var mapVisitInProgress = false
    private var favoritesGeneration = 0
    private var accessAllowed = false
    private var accessJob: Job? = null
    private var allListings: List<Listing> = emptyList()
    private var savedIds = mutableSetOf<String>()
    private val pendingSaves = mutableSetOf<String>()
    private var listingsLoading = false
    private var listingsLoaded = false
    private var listingsError = false
    private var resultsAnimated = false
    private var pendingScrollY: Int? = null
    private var loadErrorSnackbar: Snackbar? = null
    private var userCampus: Pair<Double, Double>? = null
    private lateinit var categoryAdapter: PillChipAdapter
    private lateinit var listingAdapter: ListingAdapter

    private val mapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        mapVisitInProgress = false
        val data = result.data
        if (result.resultCode == RESULT_OK && data?.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false) == true) {
            val lat = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, Double.NaN)
            val lng = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LNG, Double.NaN)
            val radius = data.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, Double.NaN)
            if (validArea(lat, lng, radius)) {
                filters = filters.copy(centerLat = lat, centerLng = lng, radiusKm = radius)
                renderState()
            }
        }
        if (result.resultCode == RESULT_OK) {
            when (data?.getStringExtra(MapActivity.EXTRA_NAVIGATION)) {
                MapActivity.NAV_HOME -> goHome()
                MapActivity.NAV_PROFILE -> openProfile()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_browse)
        applyEdgeToEdgeInsets(R.id.browseRoot, R.id.bottomNavBar, R.id.browseScroll)
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
        findViewById<View>(R.id.browseEmptyAction).setOnClickListener { onEmptyAction() }
        findViewById<SwipeRefreshLayout>(R.id.browseRefresh).setOnRefreshListener { loadListings() }
        setupSearch()
        renderState()
    }

    override fun onStart() {
        super.onStart()
        accessAllowed = false
        accessJob?.cancel()
        accessJob = lifecycleScope.launch {
            val state = try {
                AuthRepo.checkAccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            // A signed-out result can be synchronous, before ON_START is dispatched.
            // onStop cancels this job, so no lifecycle-state check may skip the gate here.
            if (isFinishing || isDestroyed) return@launch
            if (state != AccessState.Allowed) {
                goTo(LoginActivity::class.java)
                return@launch
            }
            accessAllowed = true
            loadFavorites()
            loadUserCampus()
            if (!listingsLoaded && !listingsLoading) loadListings()
        }
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
        listingAdapter = ListingAdapter(emptyList(), savedIds,
            onFavoriteClick = { listing, shouldSave -> toggleSave(listing, shouldSave) },
            onItemClick = { listing -> ItemDetailDialog.show(this, listing) }
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
        val search = findViewById<EditText>(R.id.browseSearch)
        val clear = findViewById<ImageButton>(R.id.browseSearchClear)

        search.setText(filters.query)
        clear.isVisible = filters.query.isNotEmpty()
        search.addTextChangedListener { text ->
            filters = filters.copy(query = text?.toString().orEmpty())
            clear.isVisible = !text.isNullOrEmpty()
            renderResults()
            saveSession()
        }
        clear.setOnClickListener { search.setText("") }
        search.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                WindowCompat.getInsetsController(window, search)
                    .hide(WindowInsetsCompat.Type.ime())
                search.clearFocus()
                true
            } else {
                false
            }
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
            val color = ColorStateList.valueOf(ContextCompat.getColor(
                this@BrowseActivity, if (hasArea) R.color.nox_accent_text else R.color.nox_dark_green
            ))
            iconTint = color
            setTextColor(color)
        }

        saveSession()
        renderResults()
    }

    private fun renderFilterLabel(viewId: Int, labelId: Int, value: String?) {
        findViewById<TextView>(viewId).apply {
            text = if (value == null) getString(labelId) else getString(R.string.browse_active_filter, getString(labelId), value)
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
        findViewById<View>(R.id.browseEmptyState)
            .fadeVisibility(listingsLoaded && !listingsLoading && !listingsError && results.isEmpty())

        val noListings = allListings.isEmpty()
        findViewById<TextView>(R.id.browseEmptyTitle)
            .setText(if (noListings) R.string.browse_empty_no_items_title else R.string.browse_empty_title)
        findViewById<TextView>(R.id.browseEmptyHint)
            .setText(if (noListings) R.string.browse_empty_no_items_hint else R.string.browse_empty_hint)
        findViewById<TextView>(R.id.browseEmptyAction)
            .setText(if (noListings) R.string.browse_empty_action_refresh else R.string.browse_empty_action_clear)
    }

    private fun onEmptyAction() {
        if (allListings.isEmpty()) loadListings() else clearAllFilters()
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
        findViewById<EditText>(R.id.browseSearch).setText("")
        renderState()
    }

    private fun loadListings() {
        listingsLoading = true
        listingsError = false
        loadErrorSnackbar?.dismiss()
        renderResults()
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                if (!isDestroyed && !isFinishing) {
                    allListings = listings
                    listingsLoading = false
                    listingsLoaded = true
                    findViewById<SwipeRefreshLayout>(R.id.browseRefresh).isRefreshing = false
                    renderResults()
                    if (!resultsAnimated) {
                        resultsAnimated = true
                        findViewById<RecyclerView>(R.id.browseResults).animateChildrenIn(staggerMs = 30L)
                    }
                    pendingScrollY?.let { scrollY ->
                        // The first restored layout can precede Firestore data and otherwise clamp to zero.
                        val scroll = findViewById<NestedScrollView>(R.id.browseScroll)
                        scroll.doOnNextLayout { scroll.scrollTo(0, scrollY) }
                        scroll.requestLayout()
                        pendingScrollY = null
                    }
                }
            },
            onError = {
                if (!isDestroyed && !isFinishing) {
                    allListings = emptyList()
                    listingsLoading = false
                    listingsLoaded = true
                    listingsError = true
                    findViewById<SwipeRefreshLayout>(R.id.browseRefresh).isRefreshing = false
                    renderResults()
                    loadErrorSnackbar = Snackbar.make(findViewById(R.id.browseRoot), R.string.browse_load_error, Snackbar.LENGTH_INDEFINITE)
                        .setAnchorView(R.id.bottomNavBar)
                        .setAction(R.string.browse_retry) { loadListings() }
                    loadErrorSnackbar?.show()
                }
            }
        )
    }

    private fun loadFavorites() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val generation = ++favoritesGeneration
        ListingRepo.getSavedItemIds(uid) { ids ->
            if (!isDestroyed && generation == favoritesGeneration && FirebaseAuth.getInstance().currentUser?.uid == uid) {
                savedIds = ids.toMutableSet()
                listingAdapter.updateSavedIds(savedIds)
            }
        }
    }

    private fun loadUserCampus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ListingRepo.getUserProfile(uid) { profile ->
            userCampus = CampusLocations.forSchool(profile?.school)
        }
    }

    private fun toggleSave(listing: Listing, shouldSave: Boolean, showUndo: Boolean = true) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!pendingSaves.add(listing.id)) return

        applySavedLocally(listing.id, shouldSave)
        ListingRepo.toggleSaveItem(uid, listing.id, shouldSave) { success ->
            pendingSaves.remove(listing.id)
            if (isDestroyed) return@toggleSaveItem
            if (!success) {
                applySavedLocally(listing.id, !shouldSave)
                Snackbar.make(findViewById(R.id.browseRoot), R.string.save_failed, Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.bottomNavBar)
                    .show()
            } else if (showUndo) {
                Snackbar.make(
                    findViewById(R.id.browseRoot),
                    if (shouldSave) R.string.browse_saved else R.string.browse_unsaved,
                    Snackbar.LENGTH_LONG
                )
                    .setAnchorView(R.id.bottomNavBar)
                    .setAction(R.string.undo) { toggleSave(listing, !shouldSave, showUndo = false) }
                    .show()
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
        findViewById<BottomNavigationView>(R.id.bottomNavBar).apply {
            selectedItemId = R.id.navBrowse
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navHome -> goHome()
                    R.id.navPost -> startActivity(Intent(this@BrowseActivity, com.example.noxapp.postlisting.PostListing::class.java))
                    R.id.navChat -> android.widget.Toast.makeText(
                        this@BrowseActivity, R.string.browse_chat_toast, android.widget.Toast.LENGTH_SHORT
                    ).show()
                    R.id.navProfile -> openProfile()
                }
                item.itemId == R.id.navBrowse
            }
            setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navBrowse) {
                    findViewById<NestedScrollView>(R.id.browseScroll).smoothScrollTo(0, 0)
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            restoreFilters(state)
            return
        }
        val restored = BrowseSession.restore(FirebaseAuth.getInstance().currentUser?.uid)
        if (restored != null) {
            filters = restored.first
            categoriesExpanded = restored.second
        } else {
            filters = BrowseFilters(sort = Prefs.defaultSort(this))
        }
    }

    private fun restoreFilters(state: Bundle) {
        filters = BundleCompat.getParcelable(state, "browse.filters", BrowseFilters::class.java) ?: BrowseFilters()
        categoriesExpanded = state.getBoolean("browse.expanded")
        mapVisitInProgress = state.getBoolean("browse.mapVisit")
        pendingScrollY = state.getInt("browse.scrollY")
    }

    private fun saveSession() {
        BrowseSession.save(FirebaseAuth.getInstance().currentUser?.uid, filters, categoriesExpanded)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("browse.filters", filters)
        outState.putBoolean("browse.expanded", categoriesExpanded)
        outState.putBoolean("browse.mapVisit", mapVisitInProgress)
        outState.putInt("browse.scrollY", pendingScrollY ?: findViewById<NestedScrollView>(R.id.browseScroll).scrollY)
        super.onSaveInstanceState(outState)
    }

    private fun validArea(lat: Double?, lng: Double?, radius: Double?): Boolean =
        lat != null && lng != null && radius != null && lat.isFinite() && lng.isFinite() &&
            radius.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && radius > 0.0
}
