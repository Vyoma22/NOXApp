package com.example.noxapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.data.SellerListingFilters
import com.example.noxapp.models.CategoryCatalog
import com.example.noxapp.models.Listing
import com.example.noxapp.models.UserProfile
import com.example.noxapp.ui.ItemDetailDialog
import com.example.noxapp.ui.animateChildrenIn
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.fadeVisibility
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

/**
 * Read-only profile for the student who posted a listing: identity header plus their Listings and
 * Sold tabs. Buyers reach it from the seller row of [ItemDetailDialog]; the signed-in student's own
 * profile keeps using [ProfileActivity].
 */
class SellerProfileActivity : AppCompatActivity() {

    private lateinit var listingsAdapter: ListingAdapter
    private lateinit var sellerId: String
    private lateinit var fallbackName: String

    private var allListings: List<Listing> = emptyList()
    private var userSavedIds: Set<String> = emptySet()
    private val pendingSaves = mutableSetOf<String>()
    private var currentTab = Tab.LISTINGS
    private var query = ""
    private var category: String? = null
    private var listingsLoading = false
    private var listingsLoaded = false
    private var listingsError = false
    private var resultsAnimated = false

    private enum class Tab { LISTINGS, SOLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sellerId = intent.getStringExtra(EXTRA_SELLER_ID).orEmpty().trim()
        if (sellerId.isBlank()) {
            finish()
            return
        }
        // The header must not wait on Firestore for a name: the entry point already passed one.
        fallbackName = intent.getStringExtra(EXTRA_SELLER_NAME)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.detail_seller_fallback)
        restoreState(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_seller_profile)
        applyEdgeToEdgeInsets(R.id.sellerProfileRoot, scrollId = R.id.sellerProfileScroll)

        setupRecyclerView()
        setupTabs()
        styleTabs()
        setupSearch()
        setupCategorySpinner()
        bindHeader(null)
        findViewById<ImageButton>(R.id.sellerProfileBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.sellerErrorRetry).setOnClickListener { loadListings() }

        loadProfile()
        loadSavedIds()
        loadListings()
    }

    override fun onResume() {
        super.onResume()
        // Editing the listing from the sheet or the edit form can change this seller's grid.
        if (listingsLoaded) loadListings()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        outState.putString(STATE_QUERY, query)
        outState.putString(STATE_CATEGORY, category)
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        state ?: return
        state.getString(STATE_TAB)?.let { saved ->
            currentTab = runCatching { Tab.valueOf(saved) }.getOrDefault(Tab.LISTINGS)
        }
        query = state.getString(STATE_QUERY).orEmpty()
        category = state.getString(STATE_CATEGORY)
    }

    private fun setupRecyclerView() {
        listingsAdapter = ListingAdapter(
            emptyList(),
            userSavedIds,
            onFavoriteClick = { listing, shouldSave -> toggleSave(listing, shouldSave) },
            onItemClick = { listing ->
                ItemDetailDialog.show(this, listing) { loadListings() }
            }
        )
        findViewById<RecyclerView>(R.id.sellerRecyclerView).apply {
            layoutManager = GridLayoutManager(this@SellerProfileActivity, 2)
            adapter = listingsAdapter
        }
    }

    private fun setupTabs() {
        findViewById<Button>(R.id.sellerTabListings).setOnClickListener { selectTab(Tab.LISTINGS) }
        findViewById<Button>(R.id.sellerTabSold).setOnClickListener { selectTab(Tab.SOLD) }
    }

    private fun selectTab(tab: Tab) {
        if (currentTab == tab) return
        currentTab = tab
        styleTabs()
        renderResults(animate = true)
    }

    /** Re-applies the selected pill styling after rotation, when the tab was restored from state. */
    private fun styleTabs() {
        val ids = mapOf(
            Tab.LISTINGS to R.id.sellerTabListings,
            Tab.SOLD to R.id.sellerTabSold
        )
        val selected = findViewById<Button>(ids.getValue(currentTab))
        val unselected = ids.filterKeys { it != currentTab }.values.map { findViewById<Button>(it) }
        selected.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_primary_green)
        selected.setTextColor(ContextCompat.getColor(this, R.color.white))
        unselected.forEach {
            it.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_chip_bg)
            it.setTextColor(ContextCompat.getColor(this, R.color.nox_dark_green))
        }
    }

    private fun setupSearch() {
        val search = findViewById<EditText>(R.id.sellerSearch)
        val clear = findViewById<ImageButton>(R.id.sellerSearchClear)

        search.setText(query)
        clear.isVisible = query.isNotEmpty()
        search.addTextChangedListener { text ->
            query = text?.toString().orEmpty()
            clear.isVisible = query.isNotEmpty()
            renderResults()
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

    private fun setupCategorySpinner() {
        val spinner = findViewById<Spinner>(R.id.sellerCategorySpinner)
        val names = listOf(CategoryCatalog.ALL.name) + CategoryCatalog.NAMES
        val spinnerAdapter = ArrayAdapter(this, R.layout.item_spinner, names)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinner.adapter = spinnerAdapter

        // Position 0 is "All", so a restored category has to be at least position 1.
        val restoredIndex = category?.let { saved ->
            names.indexOfFirst { it.equals(saved, ignoreCase = true) }
        }?.takeIf { it > 0 } ?: 0
        if (restoredIndex == 0) category = null
        spinner.setSelection(restoredIndex, false)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                category = if (position == 0) null else names.getOrNull(position)
                renderResults()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun loadProfile() {
        ListingRepo.getUserProfile(sellerId) { profile ->
            if (isDestroyed || isFinishing) return@getUserProfile
            bindHeader(profile)
        }
    }

    /** Binds the identity header, keeping the passed-in name until the profile document answers. */
    private fun bindHeader(profile: UserProfile?) {
        val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        val nameView = findViewById<TextView>(R.id.sellerNameText)
        val initialView = findViewById<TextView>(R.id.sellerAvatarInitial)

        // The green check is the same verified-student treatment the item detail sheet uses.
        val label = "$name ✓"
        nameView.text = SpannableString(label).apply {
            setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(this@SellerProfileActivity, R.color.nox_accent_text)
                ),
                label.length - 1,
                label.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        nameView.contentDescription = getString(R.string.detail_seller_verified_name, name)
        initialView.text = name.take(1).uppercase()
        initialView.contentDescription = getString(R.string.seller_profile_avatar_named, name)

        // The school and graduation year only exist for profiles that have filled them in.
        val school = profile?.school?.trim().orEmpty()
        val gradYear = profile?.gradYear
        val schoolView = findViewById<TextView>(R.id.sellerSchoolText)
        schoolView.text = when {
            school.isNotEmpty() && gradYear != null ->
                getString(R.string.seller_school_class, school, gradYear)
            school.isNotEmpty() -> school
            gradYear != null -> getString(R.string.seller_class_of, gradYear)
            else -> ""
        }
        schoolView.isVisible = schoolView.text.isNotEmpty()
    }

    private fun loadListings() {
        listingsLoading = true
        listingsError = false
        renderResults()
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                if (isDestroyed || isFinishing) return@loadAllListings
                // Firestore orders Numbers before Timestamps, so legacy and new createdAt values
                // arrive interleaved by type; sort the seller tabs client-side instead.
                allListings = listings.sortedByDescending { it.createdAt }
                listingsLoading = false
                listingsLoaded = true
                renderResults()
                if (!resultsAnimated && listingsAdapter.itemCount > 0) {
                    resultsAnimated = true
                    findViewById<RecyclerView>(R.id.sellerRecyclerView)
                        .animateChildrenIn(staggerMs = 30L)
                }
            },
            onError = {
                if (isDestroyed || isFinishing) return@loadAllListings
                allListings = emptyList()
                listingsLoading = false
                listingsLoaded = false
                listingsError = true
                renderResults()
            }
        )
    }

    private fun loadSavedIds() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ListingRepo.getSavedItemIds(uid) { ids ->
            if (isDestroyed || isFinishing) return@getSavedItemIds
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@getSavedItemIds
            userSavedIds = ids.toSet()
            listingsAdapter.updateSavedIds(userSavedIds)
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
                Snackbar.make(findViewById(R.id.sellerProfileRoot), R.string.save_failed, Snackbar.LENGTH_LONG)
                    .show()
            } else if (showUndo) {
                Snackbar.make(
                    findViewById(R.id.sellerProfileRoot),
                    if (shouldSave) R.string.browse_saved else R.string.browse_unsaved,
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.undo) { toggleSave(listing, !shouldSave, showUndo = false) }
                    .show()
            }
        }
    }

    private fun applySavedLocally(listingId: String, saved: Boolean) {
        userSavedIds = if (saved) userSavedIds + listingId else userSavedIds - listingId
        listingsAdapter.updateSavedIds(userSavedIds)
    }

    private fun renderResults(animate: Boolean = false) {
        val sellerListings = SellerListingFilters.splitBySold(
            SellerListingFilters.forSeller(allListings, sellerId)
        )
        val tabListings = when (currentTab) {
            Tab.LISTINGS -> sellerListings.available
            Tab.SOLD -> sellerListings.sold
        }
        val results = SellerListingFilters.apply(tabListings, query, category)
        listingsAdapter.updateList(results)

        val countView = findViewById<TextView>(R.id.sellerResultCount)
        countView.text = when {
            listingsLoading -> getString(R.string.browse_loading)
            listingsError -> ""
            else -> resources.getQuantityString(R.plurals.seller_result_count, results.size, results.size)
        }
        findViewById<View>(R.id.sellerLoading).isVisible = listingsLoading
        findViewById<View>(R.id.sellerErrorState).isVisible = listingsError
        findViewById<View>(R.id.sellerRecyclerView).isVisible = !listingsError && results.isNotEmpty()

        val emptyNotice = findViewById<TextView>(R.id.sellerEmptyNotice)
        emptyNotice.setText(
            if (tabListings.isEmpty()) emptyTabMessage() else R.string.seller_empty_search
        )
        emptyNotice.fadeVisibility(
            listingsLoaded && !listingsLoading && !listingsError && results.isEmpty()
        )

        if (animate && results.isNotEmpty()) {
            findViewById<RecyclerView>(R.id.sellerRecyclerView).animateChildrenIn(staggerMs = 30L)
        }
    }

    private fun emptyTabMessage(): Int = when (currentTab) {
        Tab.LISTINGS -> R.string.seller_empty_listings
        Tab.SOLD -> R.string.seller_empty_sold
    }

    companion object {
        const val EXTRA_SELLER_ID = "seller.id"
        const val EXTRA_SELLER_NAME = "seller.name"
        private const val STATE_TAB = "sellerProfile.tab"
        private const val STATE_QUERY = "sellerProfile.query"
        private const val STATE_CATEGORY = "sellerProfile.category"

        /** The read-only profile of one seller, opened from the item detail sheet. */
        fun intent(context: Context, sellerId: String, sellerName: String): Intent =
            Intent(context, SellerProfileActivity::class.java)
                .putExtra(EXTRA_SELLER_ID, sellerId)
                .putExtra(EXTRA_SELLER_NAME, sellerName)
    }
}
