package com.example.noxapp.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.noxapp.listing.ItemDetailDialog
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.SaveToggle
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.checkAccessOnStart
import com.example.noxapp.core.wireTabBar
import kotlinx.coroutines.Job
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.noxapp.R
import com.example.noxapp.listing.adapters.ListingAdapter
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.Listing
import com.example.noxapp.settings.SettingsActivity
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileAdapter: ListingAdapter
    private var allListings: List<Listing> = emptyList()
    private var userSavedIds: Set<String> = emptySet()
    private val saveToggle = SaveToggle(
        this, R.id.profileRoot, R.id.profileBottomNav,
        R.string.browse_saved, R.string.browse_unsaved, ::applySavedLocally
    )
    private var currentTab = Tab.MY_LISTINGS
    private var accessJob: Job? = null
    private var listingsLoaded = false
    private var listingsError = false

    private enum class Tab { MY_LISTINGS, SAVED, SOLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        applyEdgeToEdgeInsets(R.id.profileRoot, R.id.profileBottomNav, R.id.profileScroll)

        savedInstanceState?.getString(STATE_TAB)?.let { saved ->
            currentTab = Tab.entries.firstOrNull { it.name == saved } ?: Tab.MY_LISTINGS
        }
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
        findViewById<TextView>(R.id.statSavedCount).text = userSavedIds.size.toString()
        if (currentTab == Tab.SAVED) renderCurrentTab()
    }

    /** Pull-to-refresh re-reads the profile document and the listings it chains into. */
    private fun setupRefresh() {
        findViewById<SwipeRefreshLayout>(R.id.profileRefresh).setOnRefreshListener {
            loadUserProfile()
        }
        findViewById<Button>(R.id.profileErrorRetry).setOnClickListener { loadUserProfile() }
    }

    private fun loadUserProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Nothing to fetch for a signed-out account, so do not leave the spinner running.
            stopRefresh()
            return
        }
        // A pull or a retry starts clean: no stale error while loading and no empty notice.
        listingsLoaded = false
        listingsError = false
        hideErrorState()
        findViewById<TextView>(R.id.profileEmptyNotice).isVisible = false

        ListingRepo.getUserProfile(uid) { profile ->
            if (isDestroyed) return@getUserProfile
            // The signed-in account is always available, so the header never shows canned text
            // when the Firestore document is missing or has no displayName.
            val fallbackName = FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@").orEmpty()
            val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName

            findViewById<TextView>(R.id.profileNameText).text = name
            findViewById<TextView>(R.id.profileAvatarInitial).text = name.take(1).uppercase()

            val school = profile?.school.orEmpty()
            val gradInfo = profile?.gradYear?.let { " • Class of $it" }.orEmpty()
            findViewById<TextView>(R.id.profileSchoolText).text = "$school$gradInfo"

            if (profile != null) {
                userSavedIds = profile.savedItemIds.toSet()
                profileAdapter.updateSavedIds(userSavedIds)
                findViewById<TextView>(R.id.statSavedCount).text = userSavedIds.size.toString()
            }

            loadListings(uid)
        }
    }

    private fun loadListings(uid: String) {
        ListingRepo.loadAllListings(
            onSuccess = { listings ->
                if (isDestroyed || isFinishing) return@loadAllListings
                // Firestore orders Numbers before Timestamps, so legacy and new createdAt values
                // arrive interleaved by type; sort the tabs client-side instead.
                allListings = listings.sortedByDescending { it.createdAt }
                listingsLoaded = true
                listingsError = false
                stopRefresh()
                hideErrorState()
                val myListingsCount = allListings.count { it.sellerId == uid }
                findViewById<TextView>(R.id.statListingsCount).text = myListingsCount.toString()
                renderCurrentTab()
            },
            onError = {
                if (isDestroyed || isFinishing) return@loadAllListings
                // A retry state instead of a toast; the previous grid cannot be trusted offline.
                allListings = emptyList()
                listingsLoaded = false
                listingsError = true
                profileAdapter.updateList(emptyList())
                findViewById<TextView>(R.id.profileEmptyNotice).isVisible = false
                showErrorState()
                stopRefresh()
            }
        )
    }

    private fun stopRefresh() {
        findViewById<SwipeRefreshLayout>(R.id.profileRefresh).isRefreshing = false
    }

    private fun showErrorState() {
        findViewById<View>(R.id.profileErrorState).isVisible = true
    }

    private fun hideErrorState() {
        findViewById<View>(R.id.profileErrorState).isVisible = false
    }

    private fun setupTabs() {
        findViewById<Button>(R.id.tabMyListings).setOnClickListener {
            selectTab(Tab.MY_LISTINGS)
        }
        findViewById<Button>(R.id.tabSaved).setOnClickListener {
            selectTab(Tab.SAVED)
        }
        findViewById<Button>(R.id.tabSold).setOnClickListener {
            selectTab(Tab.SOLD)
        }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        styleTabs()
        renderCurrentTab()
    }

    private fun updateTabStyle(selected: Button, unselected: List<Button>) {
        selected.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_primary_green)
        selected.setTextColor(ContextCompat.getColor(this, R.color.white))
        unselected.forEach {
            it.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_chip_bg)
            it.setTextColor(ContextCompat.getColor(this, R.color.nox_dark_green))
        }
    }

    /** Re-applies the selected styling after rotation, when the tab was restored from state. */
    private fun styleTabs() {
        val ids = mapOf(
            Tab.MY_LISTINGS to R.id.tabMyListings,
            Tab.SAVED to R.id.tabSaved,
            Tab.SOLD to R.id.tabSold
        )
        val selected = findViewById<Button>(ids.getValue(currentTab))
        val unselected = ids.filterKeys { it != currentTab }.values.map { findViewById<Button>(it) }
        updateTabStyle(selected, unselected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun renderCurrentTab() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val emptyNotice = findViewById<TextView>(R.id.profileEmptyNotice)


        val itemsToDisplay = when (currentTab) {

            Tab.MY_LISTINGS -> allListings.filter {
                it.sellerId == uid && !it.sold
            }

            Tab.SAVED -> allListings.filter {
                userSavedIds.contains(it.id)
            }

            Tab.SOLD -> allListings.filter {
                it.sellerId == uid && it.sold
            }
        }


        profileAdapter.updateList(itemsToDisplay)
        // The notice only makes sense after a load succeeded, and never over the error state.
        emptyNotice.isVisible = listingsLoaded && !listingsError && itemsToDisplay.isEmpty()
    }

    private fun setupBottomNav() {
        wireTabBar(R.id.profileBottomNav, NavTab.PROFILE, onReselect = {
            // Tapping Profile again returns to its default tab.
            selectTab(Tab.MY_LISTINGS)
            findViewById<androidx.core.widget.NestedScrollView>(R.id.profileScroll)?.smoothScrollTo(0, 0)
        })
    }

    companion object {
        private const val STATE_TAB = "profile.tab"
    }
}