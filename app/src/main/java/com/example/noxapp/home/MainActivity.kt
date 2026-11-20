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
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.SaveToggle
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.wireTabBar
import com.example.noxapp.home.adapters.CategoryAdapter
import com.example.noxapp.listing.CategoryCatalog
import com.example.noxapp.listing.ItemDetailDialog
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.adapters.ListingAdapter
import com.example.noxapp.postlisting.PostListing
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var featuredAdapter: ListingAdapter
    private lateinit var recentAdapter: ListingAdapter

    private lateinit var mainScroll: NestedScrollView
    private lateinit var homeRefresh: SwipeRefreshLayout
    private lateinit var searchBar: EditText
    private lateinit var featuredSection: ViewGroup
    private lateinit var recentSection: ViewGroup
    private lateinit var homeLoading: View
    private lateinit var homeErrorState: View
    private lateinit var homeEmptyState: ViewGroup

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

    private val voiceSearchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                searchBar.setText(spoken)
                searchBar.setSelection(spoken.length)
            } else {
                Toast.makeText(this, "No speech recognized.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets(R.id.main, R.id.bottomNavBar, R.id.mainScroll)

        mainScroll = findViewById(R.id.mainScroll)
        homeRefresh = findViewById(R.id.homeRefresh)
        searchBar = findViewById(R.id.searchBar)
        featuredSection = findViewById(R.id.featuredSection)
        recentSection = findViewById(R.id.recentSection)
        homeLoading = findViewById(R.id.homeLoading)
        homeErrorState = findViewById(R.id.homeErrorState)
        homeEmptyState = findViewById(R.id.homeEmptyState)

        setupUserGreeting()
        setupCategories()
        setupListingFeeds()
        setupNavigationAndActions()
        setupLiveSearch()

        restoreState(savedInstanceState)
        loadListingData()
    }

    private fun setupUserGreeting() {
        val welcomeTextView = findViewById<TextView>(R.id.welcomeTextView)
        val user = FirebaseAuth.getInstance().currentUser ?: return
        ListingRepo.getUserProfile(user.uid) { profile ->
            val name = profile?.displayName?.substringBefore(" ")
                ?: user.email?.substringBefore("@")
                ?: "Student"
            welcomeTextView.text = getString(R.string.home_greeting, name)
        }
    }

    private fun setupCategories() {
        categoryAdapter = CategoryAdapter(CategoryCatalog.homeRow()) { selected ->
            currentCategory = selected.name
            applyFilters()
        }
        findViewById<RecyclerView>(R.id.categoryRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupListingFeeds() {
        val onHeartToggle: (Listing, Boolean) -> Unit = { listing, shouldSave ->
            saveToggle.toggle(listing, shouldSave)
        }
        val onItemClick: (Listing) -> Unit = { listing ->
            ItemDetailDialog.show(this, listing) { loadListingData(showSpinner = false) }
        }

        featuredAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle, onItemClick)
        recentAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle, onItemClick)

        findViewById<RecyclerView>(R.id.featuredRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
        }
        findViewById<RecyclerView>(R.id.recentRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentAdapter
        }
    }

    private fun setupNavigationAndActions() {
        homeRefresh.setOnRefreshListener { loadListingData(showSpinner = false) }
        findViewById<Button>(R.id.homeRetry).setOnClickListener { loadListingData() }
        findViewById<Button>(R.id.homeEmptyAction).setOnClickListener {
            if (allMasterListings.isEmpty()) loadListingData() else clearFilters()
        }

        findViewById<TextView>(R.id.featuredSeeAll).setOnClickListener { openBrowse() }
        findViewById<TextView>(R.id.recentSeeAll).setOnClickListener { openBrowse() }
        findViewById<MaterialCardView>(R.id.browseCard).setOnClickListener { openBrowse() }
        findViewById<MaterialCardView>(R.id.postCard).setOnClickListener {
            startActivity(Intent(this, PostListing::class.java))
        }

        wireTabBar(R.id.bottomNavBar, NavTab.HOME, onReselect = {
            clearFilters()
            mainScroll.smoothScrollTo(0, 0)
        })
    }

    private fun setupLiveSearch() {
        val clear = findViewById<ImageButton>(R.id.searchClear)
        val voice = findViewById<ImageButton>(R.id.searchVoice)

        searchBar.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim().orEmpty()
            clear.isVisible = currentSearchQuery.isNotEmpty()
            voice.isVisible = currentSearchQuery.isEmpty()
            applyFilters()
        }

        voice.setOnClickListener {
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

        clear.setOnClickListener { searchBar.setText("") }
        searchBar.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                WindowCompat.getInsetsController(window, searchBar).hide(WindowInsetsCompat.Type.ime())
                searchBar.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun applyFilters() {
        if (!listingsLoaded) return

        val filtered = allMasterListings.filter { listing ->
            val matchesCategory = currentCategory == "All" ||
                    listing.category.equals(currentCategory, ignoreCase = true)
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    listing.title.contains(currentSearchQuery, ignoreCase = true) ||
                    listing.description.contains(currentSearchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        val featured = filtered.filter { it.featured }
        val recent = filtered.sortedByDescending { it.createdAt }

        featuredAdapter.updateList(featured)
        recentAdapter.updateList(recent)
        featuredSection.isVisible = featured.isNotEmpty()
        recentSection.isVisible = recent.isNotEmpty()

        updateEmptyState(noListings = allMasterListings.isEmpty(), noMatches = filtered.isEmpty())
    }

    private fun updateEmptyState(noListings: Boolean, noMatches: Boolean) {
        homeEmptyState.isVisible = noListings || noMatches
        if (!homeEmptyState.isVisible) return

        val title = findViewById<TextView>(R.id.homeEmptyTitle)
        val hint = findViewById<TextView>(R.id.homeEmptyHint)
        val action = findViewById<Button>(R.id.homeEmptyAction)
        if (noListings) {
            title.setText(R.string.home_empty_title)
            hint.setText(R.string.home_empty_hint)
            action.setText(R.string.home_refresh)
        } else {
            title.setText(R.string.home_no_matches_title)
            hint.setText(R.string.home_no_matches_hint)
            action.setText(R.string.clear_filters)
        }
    }

    private fun clearFilters() {
        currentCategory = "All"
        categoryAdapter.setSelected(0)
        searchBar.setText("")
        currentSearchQuery = ""
        applyFilters()
    }

    private fun applySavedLocally(listingId: String, saved: Boolean) {
        if (saved) userSavedIds.add(listingId) else userSavedIds.remove(listingId)
        featuredAdapter.updateSavedIds(userSavedIds)
        recentAdapter.updateSavedIds(userSavedIds)
    }

    private fun openBrowse() {
        val intent = Intent(this, BrowseActivity::class.java).apply {
            if (currentSearchQuery.isNotEmpty()) putExtra("query", currentSearchQuery)
            if (currentCategory != "All") putExtra("category", currentCategory)
        }
        startActivity(intent)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) return
        state.getString(STATE_CATEGORY)?.let { category ->
            val index = CategoryCatalog.homeRow().indexOfFirst { it.name == category }
            if (index >= 0) {
                currentCategory = category
                categoryAdapter.setSelected(index)
            }
        }
        state.getString(STATE_QUERY)?.let { searchBar.setText(it) }
        pendingScrollY = state.getInt(STATE_SCROLL_Y, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, currentCategory)
        outState.putString(STATE_QUERY, currentSearchQuery)
        outState.putInt(STATE_SCROLL_Y, pendingScrollY ?: mainScroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    private fun loadListingData(showSpinner: Boolean = true) {
        homeErrorState.isVisible = false
        if (showSpinner) {
            homeLoading.isVisible = true
            homeEmptyState.isVisible = false
            featuredSection.isVisible = false
            recentSection.isVisible = false
        }
        ListingRepo.loadPublicListings(
            onSuccess = { listings ->
                if (isDestroyed || isFinishing) return@loadPublicListings
                allMasterListings = listings
                listingsLoaded = true
                homeLoading.isVisible = false
                homeRefresh.isRefreshing = false
                applyFilters()
                pendingScrollY?.let { scrollY ->
                    mainScroll.doOnNextLayout { mainScroll.scrollTo(0, scrollY) }
                    mainScroll.requestLayout()
                    pendingScrollY = null
                }
            },
            onError = {
                if (isDestroyed || isFinishing) return@loadPublicListings
                listingsLoaded = false
                homeLoading.isVisible = false
                homeRefresh.isRefreshing = false
                homeEmptyState.isVisible = false
                featuredSection.isVisible = false
                recentSection.isVisible = false
                homeErrorState.isVisible = true
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (listingsLoaded) loadListingData(showSpinner = false)
    }

    override fun onStart() {
        super.onStart()
        accessJob?.cancel()
        accessJob = checkAccessOnStart {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@checkAccessOnStart
            ListingRepo.ensureSellerProfile(uid)
            ListingRepo.getSavedItemIds(uid) { savedList ->
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

    companion object {
        private const val STATE_CATEGORY = "home.category"
        private const val STATE_QUERY = "home.query"
        private const val STATE_SCROLL_Y = "home.scrollY"
    }
}