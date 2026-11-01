package com.example.noxapp

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.CategoryAdapter
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.auth.AccessState
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.CategoryItem
import com.example.noxapp.models.Listing
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.ui.ItemDetailDialog
import com.example.noxapp.ui.animateChildrenIn
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.goTo
import com.google.android.material.bottomnavigation.BottomNavigationView
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
    private var feedsAnimated = false

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

        // 4. Live Search Filter
        setupLiveSearch()

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
            welcomeTextView.text = "Welcome, $name!"
        }
    }

    private fun setupCategories() {
        val categoryRecycler = findViewById<RecyclerView>(R.id.categoryRecyclerView)
        val categories = listOf(
            CategoryItem("All", android.R.drawable.ic_menu_agenda, isSelected = true),
            CategoryItem("Books", android.R.drawable.ic_menu_save),
            CategoryItem("Electronics", android.R.drawable.ic_menu_preferences),
            CategoryItem("Furniture", android.R.drawable.ic_menu_view)
        )

        categoryAdapter = CategoryAdapter(categories) { selectedCategory ->
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
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                ListingRepo.toggleSaveItem(uid, listing.id, shouldSave) {
                    if (shouldSave) userSavedIds.add(listing.id) else userSavedIds.remove(listing.id)
                    featuredAdapter.updateSavedIds(userSavedIds)
                    recentAdapter.updateSavedIds(userSavedIds)
                    val msg = if (shouldSave) "Saved to your profile!" else "Removed from saved"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun setupLiveSearch() {
        findViewById<EditText>(R.id.searchBar).addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim().orEmpty()
            applyFilters()
        }
    }

    private fun applyFilters() {
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
                    R.id.navPost -> {
                        startActivity(Intent(this@MainActivity, PostListing::class.java))
                        Toast.makeText(this@MainActivity, "Post an Item Tab", Toast.LENGTH_SHORT).show()
                    }
                    R.id.navChat -> Toast.makeText(this@MainActivity, "Chat Inbox Tab", Toast.LENGTH_SHORT).show()
                    R.id.navProfile -> startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                }
                item.itemId == R.id.navHome
            }
        }
    }

    private fun loadListingData() {
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                allMasterListings = listings
                applyFilters()
                if (!feedsAnimated) {
                    feedsAnimated = true
                    findViewById<RecyclerView>(R.id.featuredRecyclerView).animateChildrenIn()
                    findViewById<RecyclerView>(R.id.recentRecyclerView).animateChildrenIn()
                }
            },
            onError = {
                Toast.makeText(this, "Failed to connect to listings", Toast.LENGTH_SHORT).show()
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
}