package com.example.noxapp

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.noxapp.ui.goTo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Sign out
        findViewById<Button>(R.id.signOutButton).setOnClickListener {
            userSavedIds.clear() // Clear in-memory favorites before logging out, can comment out later to save cache
            AuthRepo.signOut()
            goTo(LoginActivity::class.java)
        }

        // 2. User Greeting
        setupUserGreeting()

        // 3. Category & Feeds RecyclerViews
        setupCategories()
        setupListingFeeds()

        // 4. Action Cards & Bottom Nav
        setupActionCards()
        setupBottomNavigation()

        // 5. Live Search Filter
        setupLiveSearch()

        // 6. Load Data from Firestore
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
            showItemDetailDialog(listing)
        }
        recentAdapter = ListingAdapter(emptyList(), userSavedIds, onHeartToggle) { listing ->
            showItemDetailDialog(listing)
        }

        featuredRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        featuredRecycler.adapter = featuredAdapter
        recentRecycler.adapter = recentAdapter

        // Load saved state from Cloud Firestore
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null) {
            ListingRepo.getSavedItemIds(currentUid) { savedList ->
                userSavedIds = savedList.toMutableSet()
                featuredAdapter.updateSavedIds(userSavedIds)
                recentAdapter.updateSavedIds(userSavedIds)
            }
        }
    }

    private fun setupLiveSearch() {
        val searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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

    private fun showItemDetailDialog(listing: Listing) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_item_details)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.detailTitle).text = listing.title
        dialog.findViewById<TextView>(R.id.detailPrice).text = "$${listing.price.toInt()}"
        dialog.findViewById<TextView>(R.id.detailCondition).text = listing.condition
        dialog.findViewById<TextView>(R.id.detailCategory).text = listing.category
        dialog.findViewById<TextView>(R.id.detailDescription).text = listing.description
        dialog.findViewById<TextView>(R.id.detailLocation).text = "Pickup: ${listing.pickupLocation}"

        dialog.findViewById<ImageView>(R.id.detailClose).setOnClickListener {
            dialog.dismiss()
        }

        // Implicit intent to send an email / message to the seller [lecture on implicit intents]
        dialog.findViewById<Button>(R.id.messageSellerButton).setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_SUBJECT, "Inquiry about: ${listing.title} on NOX")
                putExtra(Intent.EXTRA_TEXT, "Hi, I'm interested in buying your ${listing.title} for $${listing.price.toInt()}!")
            }
            runCatching {
                startActivity(Intent.createChooser(emailIntent, "Contact Seller"))
            }.onFailure {
                Toast.makeText(this, "Message sent to seller!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupActionCards() {
        findViewById<CardView>(R.id.browseCard).setOnClickListener {
            categoryAdapter.setSelected(0)
            currentCategory = "All"
            applyFilters()
            Toast.makeText(this, "Browsing all campus listings", Toast.LENGTH_SHORT).show()
        }

        findViewById<CardView>(R.id.postCard).setOnClickListener {
            Toast.makeText(this, "Sell feature coming on Post branch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            // Already home
        }
        findViewById<LinearLayout>(R.id.navBrowse).setOnClickListener {
            Toast.makeText(this, "Browse Tab (Maps & Filter page)", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navPost).setOnClickListener {
            Toast.makeText(this, "Post an Item Tab", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navChat).setOnClickListener {
            Toast.makeText(this, "Chat Inbox Tab", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadListingData() {
        FirebaseFirestore.getInstance().collection("listings")
            .get()
            .addOnSuccessListener { snapshot ->
                allMasterListings = snapshot.toObjects(Listing::class.java)
                applyFilters()
            }
            .addOnFailureListener {
                ListingRepo.loadHomeFeed(
                    onSuccess = { featured, recent ->
                        allMasterListings = (featured + recent).distinctBy { it.id }
                        applyFilters()
                    },
                    onError = {
                        Toast.makeText(this, "Failed to connect to listings", Toast.LENGTH_SHORT).show()
                    }
                )
            }
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