package com.example.noxapp

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.Listing
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileAdapter: ListingAdapter
    private var allListings: List<Listing> = emptyList()
    private var userSavedIds: Set<String> = emptySet()
    private var currentTab = Tab.MY_LISTINGS

    private enum class Tab { MY_LISTINGS, SAVED, SOLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupTabs()
        setupBottomNav()
        loadUserProfile()
    }

    private fun setupRecyclerView() {
        val recycler = findViewById<RecyclerView>(R.id.profileRecyclerView)
        profileAdapter = ListingAdapter(
            emptyList(),
            userSavedIds,
            onFavoriteClick = { listing, shouldSave ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@ListingAdapter
                ListingRepo.toggleSaveItem(uid, listing.id, shouldSave) {
                    val updated = if (shouldSave) userSavedIds + listing.id else userSavedIds - listing.id
                    userSavedIds = updated
                    profileAdapter.updateSavedIds(userSavedIds)
                    findViewById<TextView>(R.id.statSavedCount).text = userSavedIds.size.toString()
                    if (currentTab == Tab.SAVED) renderCurrentTab()
                }
            },
            onItemClick = { listing ->
                // Opens the item detail dialog when tapped
                showItemDetailDialog(listing)
            }
        )

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = profileAdapter
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

    private fun loadUserProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        ListingRepo.getUserProfile(uid) { profile ->
            if (profile != null) {
                val name = profile.displayName ?: "Student"
                findViewById<TextView>(R.id.profileNameText).text = name
                findViewById<TextView>(R.id.profileAvatarInitial).text = name.take(1).uppercase()

                val school = profile.school ?: "Campus Member"
                val gradInfo = if (profile.gradYear != null) " • Class of ${profile.gradYear}" else ""
                findViewById<TextView>(R.id.profileSchoolText).text = "$school$gradInfo"

                userSavedIds = profile.savedItemIds.toSet()
                profileAdapter.updateSavedIds(userSavedIds)
                findViewById<TextView>(R.id.statSavedCount).text = userSavedIds.size.toString()

                loadListings(uid)
            }
        }
    }

    private fun loadListings(uid: String) {
        FirebaseFirestore.getInstance().collection("listings").get()
            .addOnSuccessListener { snapshot ->
                allListings = snapshot.toObjects(Listing::class.java)
                val myListingsCount = allListings.count { it.sellerId == uid }
                findViewById<TextView>(R.id.statListingsCount).text = myListingsCount.toString()
                renderCurrentTab()
            }
    }

    private fun setupTabs() {
        val btnMyListings = findViewById<Button>(R.id.tabMyListings)
        val btnSaved = findViewById<Button>(R.id.tabSaved)
        val btnSold = findViewById<Button>(R.id.tabSold)

        btnMyListings.setOnClickListener {
            currentTab = Tab.MY_LISTINGS
            updateTabStyle(btnMyListings, listOf(btnSaved, btnSold))
            renderCurrentTab()
        }

        btnSaved.setOnClickListener {
            currentTab = Tab.SAVED
            updateTabStyle(btnSaved, listOf(btnMyListings, btnSold))
            renderCurrentTab()
        }

        btnSold.setOnClickListener {
            currentTab = Tab.SOLD
            updateTabStyle(btnSold, listOf(btnMyListings, btnSaved))
            renderCurrentTab()
        }
    }

    private fun updateTabStyle(selected: Button, unselected: List<Button>) {
        selected.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_primary_green)
        selected.setTextColor(ContextCompat.getColor(this, R.color.white))
        unselected.forEach {
            it.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nox_chip_bg)
            it.setTextColor(ContextCompat.getColor(this, R.color.nox_dark_green))
        }
    }

    private fun renderCurrentTab() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val emptyNotice = findViewById<TextView>(R.id.profileEmptyNotice)

        val itemsToDisplay = when (currentTab) {
            Tab.MY_LISTINGS -> allListings.filter { it.sellerId == uid }
            Tab.SAVED -> allListings.filter { userSavedIds.contains(it.id) }
            Tab.SOLD -> emptyList()
        }

        profileAdapter.updateList(itemsToDisplay)
        emptyNotice.visibility = if (itemsToDisplay.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.pNavHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }
}