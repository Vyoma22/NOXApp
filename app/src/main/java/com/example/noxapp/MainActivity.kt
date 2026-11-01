package com.example.noxapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.adapters.CategoryAdapter
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.auth.AccessState
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.CategoryCatalog
import com.example.noxapp.models.Listing
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.ui.ItemDetailDialog
import com.example.noxapp.ui.animateChildrenIn
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.goTo
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var featuredAdapter: ListingAdapter
    private lateinit var recentAdapter: ListingAdapter

    // Keep cached lists in memory to enable instant search and category filtering
    private var allMasterListings: List<Listing> = emptyList()
    private var currentCategory: String = "All"
    private var currentSearchQuery: String = ""
    private var userSavedIds: MutableSet<String> = mutableSetOf()
    private val pendingSaves = mutableSetOf<String>()
    private var feedsAnimated = false
    private var listingsLoaded = false
    private var pendingScrollY: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets(R.id.main, R.id.bottomNavBar, R.id.mainScroll)

        // The page assembles itself on first frame instead of landing all at once.
        findViewById<ViewGroup>(R.id.homeContent).animateChildrenIn(staggerMs = 25L, riseDp = 10f)

        // 1. User Greeting
        setupUserGreeting()

        // 2. Category & Feeds RecyclerViews
        setupCategories()
        setupListingFeeds()

        // 3. See-all links, Action Cards & Bottom Nav
        setupHeaderLinks()
        setupActionCards()
        setupBottomNavigation()
        setupRefresh()

        // 4. Live Search Filter
        setupLiveSearch()

        restoreState(savedInstanceState)

        // 5. Load Data from Firestore
        loadListingData()
    }

    private fun setupUserGreeting() {
        val welcomeTextView = findViewById<TextView>(R.id.welcomeTextView)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        ListingRepo.getUserProfile(uid) { profile ->
            val name = profile?.displayName?.substringBefore(" ")
                ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                ?: "Student"
            welcomeTextView.text = getString(R.string.home_greeting, name)
        }
    }

    private fun setupCategories() {
        val categoryRecycler = findViewById<RecyclerView>(R.id.categoryRecyclerView)

        categoryAdapter = CategoryAdapter(CategoryCatalog.homeRow()) { selectedCategory ->
            currentCategory = selectedCategory.name
            applyFilters()
        }

        categoryRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryRecycler.adapter = categoryAdapter
    }

    private fun setupListingFeeds() {
        val featuredRecycler = findViewById<RecyclerView>(R.id.featuredRecyclerView)
        val recentRecycler = findViewById<RecyclerView>(R.id.recentRecyclerView)

        val onHeartToggle: (Listing, Boolean) -> Unit = { listing, shouldSave ->
            toggleSave(listing, shouldSave)
        }

        featuredAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle) { listing ->
            ItemDetailDialog.show(this, listing)
        }
        recentAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle) { listing ->
            ItemDetailDialog.show(this, listing)
        }

        featuredRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        featuredRecycler.adapter = featuredAdapter
        recentRecycler.adapter = recentAdapter
    }

    private fun setupRefresh() {
        findViewById<SwipeRefreshLayout>(R.id.homeRefresh).setOnRefreshListener {
            loadListingData(showSpinner = false)
        }
        findViewById<Button>(R.id.homeRetry).setOnClickListener { loadListingData() }
        findViewById<Button>(R.id.homeEmptyAction).setOnClickListener {
            if (allMasterListings.isEmpty()) {
                loadListingData()
            } else {
                clearFilters()
            }
        }
    }

    private fun setupLiveSearch() {
        val search = findViewById<EditText>(R.id.searchBar)
        val clear = findViewById<ImageButton>(R.id.searchClear)

        search.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim().orEmpty()
            clear.isVisible = !text.isNullOrEmpty()
            applyFilters()
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

    private fun applyFilters() {
        if (!listingsLoaded) return

        val filteredList = allMasterListings.filter { listing ->
            val matchesCategory = (currentCategory == "All") || listing.category.equals(currentCategory, ignoreCase = true)
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    listing.title.contains(currentSearchQuery, ignoreCase = true) ||
                    listing.description.contains(currentSearchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        val featured = filteredList.filter { it.featured }
        val recent = filteredList.sortedByDescending { it.createdAt }

        featuredAdapter.updateList(featured)
        recentAdapter.updateList(recent)
        findViewById<ViewGroup>(R.id.featuredSection).isVisible = featured.isNotEmpty()
        findViewById<ViewGroup>(R.id.recentSection).isVisible = recent.isNotEmpty()

        val noListings = allMasterListings.isEmpty()
        val noMatches = filteredList.isEmpty()
        showEmptyState(noListings, noMatches)
    }

    private fun showEmptyState(noListings: Boolean, noMatches: Boolean) {
        val empty = findViewById<ViewGroup>(R.id.homeEmptyState)
        val title = findViewById<TextView>(R.id.homeEmptyTitle)
        val hint = findViewById<TextView>(R.id.homeEmptyHint)
        val action = findViewById<Button>(R.id.homeEmptyAction)

        when {
            noListings -> {
                empty.isVisible = true
                title.setText(R.string.home_empty_title)
                hint.setText(R.string.home_empty_hint)
                action.setText(R.string.home_refresh)
            }
            noMatches -> {
                empty.isVisible = true
                title.setText(R.string.home_no_matches_title)
                hint.setText(R.string.home_no_matches_hint)
                action.setText(R.string.clear_filters)
            }
            else -> empty.isVisible = false
        }
    }

    private fun clearFilters() {
        currentCategory = "All"
        categoryAdapter.setSelected(0)
        findViewById<EditText>(R.id.searchBar).setText("")
        currentSearchQuery = ""
        applyFilters()
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
                Snackbar.make(findViewById(R.id.main), R.string.save_failed, Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.bottomNavBar)
                    .show()
            } else if (showUndo) {
                Snackbar.make(
                    findViewById(R.id.main),
                    if (shouldSave) R.string.saved_to_profile else R.string.removed_from_saved,
                    Snackbar.LENGTH_LONG
                )
                    .setAnchorView(R.id.bottomNavBar)
                    .setAction(R.string.undo) { toggleSave(listing, !shouldSave, showUndo = false) }
                    .show()
            }
        }
    }

    private fun applySavedLocally(listingId: String, saved: Boolean) {
        if (saved) userSavedIds.add(listingId) else userSavedIds.remove(listingId)
        featuredAdapter.updateSavedIds(userSavedIds)
        recentAdapter.updateSavedIds(userSavedIds)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) return
        val category = state.getString(STATE_CATEGORY)
        if (category != null) {
            val index = CategoryCatalog.homeRow().indexOfFirst { it.name == category }
            if (index >= 0) {
                currentCategory = category
                categoryAdapter.setSelected(index)
            }
        }
        state.getString(STATE_QUERY)?.let { query ->
            findViewById<EditText>(R.id.searchBar).setText(query)
        }
        pendingScrollY = state.getInt(STATE_SCROLL_Y, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, currentCategory)
        outState.putString(STATE_QUERY, currentSearchQuery)
        outState.putInt(
            STATE_SCROLL_Y,
            pendingScrollY ?: findViewById<NestedScrollView>(R.id.mainScroll).scrollY
        )
        super.onSaveInstanceState(outState)
    }

    private fun setupHeaderLinks() {
        findViewById<TextView>(R.id.featuredSeeAll).setOnClickListener { openBrowse() }
        findViewById<TextView>(R.id.recentSeeAll).setOnClickListener { openBrowse() }
    }

    private fun setupActionCards() {
        findViewById<CardView>(R.id.browseCard).setOnClickListener { openBrowse() }

        findViewById<CardView>(R.id.postCard).setOnClickListener {
            startActivity(Intent(this, PostListing::class.java))
        }
    }

    // The "See all" links and the Browse card all land on the full browse screen
    private fun openBrowse() {
        startActivity(Intent(this, BrowseActivity::class.java))
    }

    private fun setupBottomNavigation() {
        findViewById<BottomNavigationView>(R.id.bottomNavBar).apply {
            selectedItemId = R.id.navHome
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navBrowse -> startActivity(Intent(this@MainActivity, BrowseActivity::class.java))
                    R.id.navPost -> startActivity(Intent(this@MainActivity, PostListing::class.java))
                    R.id.navChat -> Toast.makeText(this@MainActivity, R.string.map_chat_toast, Toast.LENGTH_SHORT).show()
                    R.id.navProfile -> startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                }
                item.itemId == R.id.navHome
            }
            setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navHome) {
                    findViewById<NestedScrollView>(R.id.mainScroll).smoothScrollTo(0, 0)
                }
            }
        }
    }

    private fun loadListingData(showSpinner: Boolean = true) {
        findViewById<View>(R.id.homeErrorState).isVisible = false
        if (showSpinner) {
            findViewById<View>(R.id.homeLoading).isVisible = true
            findViewById<View>(R.id.homeEmptyState).isVisible = false
            findViewById<View>(R.id.featuredSection).isVisible = false
            findViewById<View>(R.id.recentSection).isVisible = false
        }
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                if (isDestroyed || isFinishing) return@loadAllListings
                allMasterListings = listings
                listingsLoaded = true
                findViewById<View>(R.id.homeLoading).isVisible = false
                findViewById<SwipeRefreshLayout>(R.id.homeRefresh).isRefreshing = false
                findViewById<View>(R.id.homeErrorState).isVisible = false
                applyFilters()
                if (!feedsAnimated) {
                    feedsAnimated = true
                    findViewById<RecyclerView>(R.id.featuredRecyclerView).animateChildrenIn()
                    findViewById<RecyclerView>(R.id.recentRecyclerView).animateChildrenIn()
                }
                pendingScrollY?.let { scrollY ->
                    val scroll = findViewById<NestedScrollView>(R.id.mainScroll)
                    scroll.doOnNextLayout { scroll.scrollTo(0, scrollY) }
                    scroll.requestLayout()
                    pendingScrollY = null
                }
            },
            onError = {
                if (isDestroyed || isFinishing) return@loadAllListings
                listingsLoaded = false
                findViewById<View>(R.id.homeLoading).isVisible = false
                findViewById<SwipeRefreshLayout>(R.id.homeRefresh).isRefreshing = false
                findViewById<View>(R.id.homeEmptyState).isVisible = false
                findViewById<View>(R.id.featuredSection).isVisible = false
                findViewById<View>(R.id.recentSection).isVisible = false
                findViewById<View>(R.id.homeErrorState).isVisible = true
            }
        )
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val state = runCatching { AuthRepo.checkAccess() }.getOrNull()
            if (state != null && state != AccessState.Allowed) {
                goTo(LoginActivity::class.java)
            } else {
                // Re-sync active user's saved IDs
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUid != null) {
                    ListingRepo.getSavedItemIds(currentUid) { savedList ->
                        userSavedIds = savedList.toMutableSet()
                        featuredAdapter.updateSavedIds(userSavedIds)
                        recentAdapter.updateSavedIds(userSavedIds)
                    }
                }
            }
        }
    }

    companion object {
        private const val STATE_CATEGORY = "home.category"
        private const val STATE_QUERY = "home.query"
        private const val STATE_SCROLL_Y = "home.scrollY"
    }
}
