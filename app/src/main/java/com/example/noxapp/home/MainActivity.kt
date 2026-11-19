package com.example.noxapp.home

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.R
import com.example.noxapp.browse.BrowseActivity
import com.example.noxapp.home.adapters.CategoryAdapter
import com.example.noxapp.listing.adapters.ListingAdapter
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.CategoryCatalog
import com.example.noxapp.listing.Listing
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.listing.ItemDetailDialog
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.SaveToggle
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.wireTabBar
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var featuredAdapter: ListingAdapter
    private lateinit var recentAdapter: ListingAdapter

    // Keep cached lists in memory to enable instant search and category filtering
    private var allMasterListings: List<Listing> = emptyList()
    private var currentCategory: String = "All"
    private var currentSearchQuery: String = ""
    private var userSavedIds: MutableSet<String> = mutableSetOf()
    private val saveToggle = SaveToggle(
        this, R.id.main, R.id.bottomNavBar,
        R.string.saved_to_profile, R.string.removed_from_saved, ::applySavedLocally
    )
    private var listingsLoaded = false
    private var pendingScrollY: Int? = null
    private var accessJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets(R.id.main, R.id.bottomNavBar, R.id.mainScroll)

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
            saveToggle.toggle(listing, shouldSave)
        }

        featuredAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle) { listing ->
            ItemDetailDialog.show(this, listing) { loadListingData(showSpinner = false) }
        }
        recentAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle) { listing ->
            ItemDetailDialog.show(this, listing) { loadListingData(showSpinner = false) }
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
        val voice = findViewById<ImageButton>(R.id.searchVoice)

        search.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim().orEmpty()
            clear.isVisible = !text.isNullOrEmpty()
            voice.isVisible = text.isNullOrEmpty()
            applyFilters()
        }

        voice.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
            if (intent.resolveActivity(packageManager) != null) {
                activityResultLauncher.launch(intent)
            } else {
                Toast.makeText(
                    this,
                    "Speech recognition is not available.",
                    Toast.LENGTH_LONG
                ).show()
            }
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
        findViewById<MaterialCardView>(R.id.browseCard).setOnClickListener { openBrowse() }

        findViewById<MaterialCardView>(R.id.postCard).setOnClickListener {
            startActivity(Intent(this, PostListing::class.java))
        }
    }

    // The "See all" links and the Browse card all land on the full browse screen
    private fun openBrowse() {
        startActivity(Intent(this, BrowseActivity::class.java))
    }

    private fun setupBottomNavigation() {
        wireTabBar(R.id.bottomNavBar, NavTab.HOME, onReselect = {
            // Tapping Home again restores the unfiltered feed.
            clearFilters()
            // The content view can already be detached during a task transition.
            findViewById<NestedScrollView>(R.id.mainScroll)?.smoothScrollTo(0, 0)
        })
    }

    private fun loadListingData(showSpinner: Boolean = true) {
        findViewById<View>(R.id.homeErrorState).isVisible = false
        if (showSpinner) {
            findViewById<View>(R.id.homeLoading).isVisible = true
            findViewById<View>(R.id.homeEmptyState).isVisible = false
            findViewById<View>(R.id.featuredSection).isVisible = false
            findViewById<View>(R.id.recentSection).isVisible = false
        }
        ListingRepo.loadPublicListings(
            onSuccess = { listings ->
                if (isDestroyed || isFinishing) return@loadPublicListings
                allMasterListings = listings
                listingsLoaded = true
                findViewById<View>(R.id.homeLoading).isVisible = false
                findViewById<SwipeRefreshLayout>(R.id.homeRefresh).isRefreshing = false
                findViewById<View>(R.id.homeErrorState).isVisible = false
                applyFilters()
                pendingScrollY?.let { scrollY ->
                    val scroll = findViewById<NestedScrollView>(R.id.mainScroll)
                    scroll.doOnNextLayout { scroll.scrollTo(0, scrollY) }
                    scroll.requestLayout()
                    pendingScrollY = null
                }
            },
            onError = {
                if (isDestroyed || isFinishing) return@loadPublicListings
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

    override fun onResume() {
        super.onResume()
        // Returning from the edit form should show the saved title, price, and photos.
        if (listingsLoaded) loadListingData(showSpinner = false)
    }

    override fun onStart() {
        super.onStart()
        accessJob?.cancel()
        accessJob = checkAccessOnStart {
            // Re-sync active user's saved IDs
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return@checkAccessOnStart
            // Accounts predating the public/private profile split still need their seller card
            // copied into sellerProfiles before other students can see it.
            ListingRepo.ensureSellerProfile(currentUid)
            ListingRepo.getSavedItemIds(currentUid) { savedList ->
                userSavedIds = savedList.toMutableSet()
                featuredAdapter.updateSavedIds(userSavedIds)
                recentAdapter.updateSavedIds(userSavedIds)
            }
        }
    }

    override fun onStop() {
        accessJob?.cancel()
        super.onStop()
    }
	
	private val activityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val speechResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val recognizedText = speechResults?.firstOrNull() //get first recognized phrase
                if (!recognizedText.isNullOrBlank()) {
                    val search = findViewById<EditText>(R.id.searchBar)
                    search.setText(recognizedText.trim()) //insert speech into the search bar
                    search.setSelection(search.text.length) //position cursor to end of text
                } else {
                    Toast.makeText(
                        this,
                        "No speech recognized.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    companion object {
        private const val STATE_CATEGORY = "home.category"
        private const val STATE_QUERY = "home.query"
        private const val STATE_SCROLL_Y = "home.scrollY"
    }
}
