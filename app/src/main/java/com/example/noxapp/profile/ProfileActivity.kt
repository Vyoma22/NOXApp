package com.example.noxapp.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.R
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.SaveToggle
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.wireTabBar
import com.example.noxapp.listing.ItemDetailDialog
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.adapters.ListingAdapter
import com.example.noxapp.settings.SettingsActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileAdapter: ListingAdapter
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var avatarInitial: TextView
    private lateinit var nameText: TextView
    private lateinit var schoolText: TextView
    private lateinit var listingsCountText: TextView
    private lateinit var savedCountText: TextView
    private lateinit var emptyNotice: TextView
    private lateinit var errorState: View
    private lateinit var tabMyListings: Button
    private lateinit var tabSaved: Button
    private lateinit var tabSold: Button

    private var allListings: List<Listing> = emptyList()
    private var userSavedIds: Set<String> = emptySet()
    private var currentTab = Tab.MY_LISTINGS
    private var accessJob: Job? = null
    private var listingsLoaded = false
    private var listingsError = false

    private val saveToggle = SaveToggle(
        this, R.id.profileRoot, R.id.profileBottomNav,
        R.string.browse_saved, R.string.browse_unsaved, ::applySavedLocally
    )

    private enum class Tab { MY_LISTINGS, SAVED, SOLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        applyEdgeToEdgeInsets(R.id.profileRoot, R.id.profileBottomNav, R.id.profileScroll)

        savedInstanceState?.getString(STATE_TAB)?.let { saved ->
            currentTab = Tab.entries.firstOrNull { it.name == saved } ?: Tab.MY_LISTINGS
        }

        bindViews()
        bindImmediateAuthHeader()
        setupRecyclerView()
        setupTabs()
        styleTabs()
        setupBottomNav()
        setupRefresh()

        findViewById<ImageButton>(R.id.profileSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        accessJob?.cancel()
        accessJob = checkAccessOnStart { loadUserProfile() }
    }

    override fun onStop() {
        accessJob?.cancel()
        super.onStop()
    }

    private fun bindViews() {
        refreshLayout = findViewById(R.id.profileRefresh)
        avatarInitial = findViewById(R.id.profileAvatarInitial)
        nameText = findViewById(R.id.profileNameText)
        schoolText = findViewById(R.id.profileSchoolText)
        listingsCountText = findViewById(R.id.statListingsCount)
        savedCountText = findViewById(R.id.statSavedCount)
        emptyNotice = findViewById(R.id.profileEmptyNotice)
        errorState = findViewById(R.id.profileErrorState)
        tabMyListings = findViewById(R.id.tabMyListings)
        tabSaved = findViewById(R.id.tabSaved)
        tabSold = findViewById(R.id.tabSold)
    }

    /** Populates header immediately from FirebaseAuth so the name/avatar never flashes blank. */
    private fun bindImmediateAuthHeader() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val initialName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@").orEmpty()
        if (initialName.isNotBlank() && nameText.text.isNullOrEmpty()) {
            nameText.text = initialName
            avatarInitial.text = initialName.take(1).uppercase()
        }
    }

    private fun setupRecyclerView() {
        val recycler = findViewById<RecyclerView>(R.id.profileRecyclerView)
        profileAdapter = ListingAdapter(
            emptyList(),
            userSavedIds,
            onFavoriteClick = { listing, shouldSave -> saveToggle.toggle(listing, shouldSave) },
            onItemClick = { listing ->
                ItemDetailDialog.show(this, listing) { loadUserProfile() }
            }
        )
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = profileAdapter
    }

    private fun applySavedLocally(listingId: String, saved: Boolean) {
        userSavedIds = if (saved) userSavedIds + listingId else userSavedIds - listingId
        profileAdapter.updateSavedIds(userSavedIds)
        savedCountText.text = userSavedIds.size.toString()
        if (currentTab == Tab.SAVED) renderCurrentTab()
    }

    private fun setupRefresh() {
        refreshLayout.setOnRefreshListener { loadUserProfile() }
        findViewById<Button>(R.id.profileErrorRetry).setOnClickListener { loadUserProfile() }
    }

    private fun loadUserProfile() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid == null) {
            refreshLayout.isRefreshing = false
            return
        }

        listingsError = false
        errorState.isVisible = false
        if (!listingsLoaded) emptyNotice.isVisible = false

        ListingRepo.getUserProfile(uid) { profile ->
            if (isActivityGone()) return@getUserProfile

            val fallbackName = currentUser.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser.email?.substringBefore("@").orEmpty()
            val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName

            nameText.text = name
            avatarInitial.text = name.take(1).uppercase()

            val school = profile?.school.orEmpty()
            val gradInfo = profile?.gradYear?.let { " • Class of $it" }.orEmpty()
            schoolText.text = "$school$gradInfo"

            if (profile != null) {
                userSavedIds = profile.savedItemIds.toSet()
                profileAdapter.updateSavedIds(userSavedIds)
                savedCountText.text = userSavedIds.size.toString()
            }

            loadListings(uid)
        }
    }

    private fun loadListings(uid: String) {
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                if (isActivityGone()) return@loadAllListings
                allListings = listings.sortedByDescending { it.createdAt }
                listingsLoaded = true
                listingsError = false
                refreshLayout.isRefreshing = false
                errorState.isVisible = false
                listingsCountText.text = allListings.count { it.sellerId == uid }.toString()
                renderCurrentTab()
            },
            onError = {
                if (isActivityGone()) return@loadAllListings
                allListings = emptyList()
                listingsLoaded = false
                listingsError = true
                profileAdapter.updateList(emptyList())
                emptyNotice.isVisible = false
                errorState.isVisible = true
                refreshLayout.isRefreshing = false
            }
        )
    }

    private fun setupTabs() {
        tabMyListings.setOnClickListener { selectTab(Tab.MY_LISTINGS) }
        tabSaved.setOnClickListener { selectTab(Tab.SAVED) }
        tabSold.setOnClickListener { selectTab(Tab.SOLD) }
    }

    private fun selectTab(tab: Tab) {
        if (currentTab == tab) return
        currentTab = tab
        styleTabs()
        renderCurrentTab()
    }

    private fun styleTabs() {
        val tabs = listOf(
            tabMyListings to Tab.MY_LISTINGS,
            tabSaved to Tab.SAVED,
            tabSold to Tab.SOLD
        )
        tabs.forEach { (button, tab) ->
            val selected = tab == currentTab
            button.backgroundTintList = ContextCompat.getColorStateList(
                this, if (selected) R.color.nox_primary_green else R.color.nox_chip_bg
            )
            button.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.white else R.color.nox_dark_green)
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun renderCurrentTab() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val itemsToDisplay = when (currentTab) {
            Tab.MY_LISTINGS -> allListings.filter { it.sellerId == uid && !it.sold }
            Tab.SAVED -> allListings.filter { userSavedIds.contains(it.id) }
            Tab.SOLD -> allListings.filter { it.sellerId == uid && it.sold }
        }

        profileAdapter.updateList(itemsToDisplay)
        emptyNotice.isVisible = listingsLoaded && !listingsError && itemsToDisplay.isEmpty()
    }

    private fun setupBottomNav() {
        wireTabBar(R.id.profileBottomNav, NavTab.PROFILE, onReselect = {
            selectTab(Tab.MY_LISTINGS)
            findViewById<NestedScrollView>(R.id.profileScroll)?.smoothScrollTo(0, 0)
        })
    }

    private fun isActivityGone(): Boolean = isDestroyed || isFinishing

    companion object {
        private const val STATE_TAB = "profile.tab"
    }
}