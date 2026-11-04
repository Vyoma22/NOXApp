package com.example.noxapp.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.noxapp.MapActivity
import com.example.noxapp.ProfileActivity
import com.example.noxapp.R
import com.example.noxapp.SellerProfileActivity
import com.example.noxapp.data.ListingLocation
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.Listing
import com.example.noxapp.postlisting.PostListing
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

object ItemDetailDialog {
    fun show(
        activity: Activity,
        listing: Listing,
        onListingChanged: (() -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_item_details)
        dialog.window?.apply {
            // Details rise from the bottom edge as a sheet, so the card the user tapped
            // stays connected to what opens over it.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(R.style.Animation_NOX_Sheet)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.findViewById<TextView>(R.id.detailTitle).text = listing.title
        dialog.findViewById<TextView>(R.id.detailPrice).text = "$${listing.price.toInt()}"
        dialog.findViewById<TextView>(R.id.detailCondition).text = listing.condition
        dialog.findViewById<TextView>(R.id.detailCategory).text = listing.category
        dialog.findViewById<TextView>(R.id.detailDescription).text = listing.description
        dialog.findViewById<TextView>(R.id.detailLocation).text = "Pickup: ${listing.pickupLocation}"
        bindPickupLocation(activity, dialog, listing)

        // Listings can carry up to five photos; the gallery handles swiping, arrows and the viewer.
        MediaGallery(activity, dialog, listing.imageUrls).bind()

        dialog.findViewById<ImageButton>(R.id.detailClose).setOnClickListener { dialog.dismiss() }

        val isOwner = listing.sellerId.isNotBlank() &&
            listing.sellerId == FirebaseAuth.getInstance().currentUser?.uid
        dialog.findViewById<ViewGroup>(R.id.ownerActions).isVisible = isOwner

        dialog.findViewById<Button>(R.id.editListingButton).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(
                Intent(activity, PostListing::class.java)
                    .putExtra(PostListing.EXTRA_LISTING_ID, listing.id)
            )
        }

        dialog.findViewById<Button>(R.id.deleteListingButton).setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.listing_delete_title)
                .setMessage(R.string.listing_delete_message)
                .setPositiveButton(R.string.listing_delete_confirm) { _, _ ->
                    ListingRepo.deleteListing(listing) { success ->
                        if (success) {
                            dialog.dismiss()
                            Toast.makeText(activity, R.string.listing_deleted, Toast.LENGTH_SHORT).show()
                            onListingChanged?.invoke()
                        } else {
                            Toast.makeText(activity, R.string.listing_delete_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        dialog.findViewById<Button>(R.id.messageSellerButton).setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_SUBJECT, "Inquiry about: ${listing.title} on NOX")
                putExtra(Intent.EXTRA_TEXT, "Hi, I'm interested in buying your ${listing.title} for $${listing.price.toInt()}!")
            }
            runCatching {
                activity.startActivity(Intent.createChooser(emailIntent, "Contact Seller"))
            }.onFailure {
                Toast.makeText(activity, "Message sent to seller!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        bindSeller(activity, dialog, listing)
        dialog.show()
    }

    /**
     * The pickup line opens the map on this listing's pin, so a buyer can see where to meet.
     * Coordinates come from the same resolver the map uses, so listings saved before map pins
     * existed still land on the right campus.
     */
    private fun bindPickupLocation(activity: Activity, dialog: Dialog, listing: Listing) {
        val location = ListingLocation.resolve(listing)
        dialog.findViewById<View>(R.id.detailLocationRow).apply {
            contentDescription = activity.getString(R.string.detail_location_view_map)
            setOnClickListener {
                dialog.dismiss()
                activity.startActivity(
                    Intent(activity, MapActivity::class.java)
                        .putExtra(MapActivity.EXTRA_VIEW_LOCATION, true)
                        .putExtra(MapActivity.EXTRA_CENTER_LAT, location.latitude)
                        .putExtra(MapActivity.EXTRA_CENTER_LNG, location.longitude)
                        .putExtra(MapActivity.EXTRA_LOCATION_LABEL, location.label)
                        .putExtra(MapActivity.EXTRA_LOCATION_TITLE, location.title)
                )
            }
        }
    }

    /**
     * Fills the seller row from the listing, then upgrades it with the seller's profile document
     * so a renamed student or a rating appears without re-posting the listing.
     *
     * The row is a shortcut to that seller's read-only profile, except for the student's own
     * listing, which opens their editable [ProfileActivity] instead.
     */
    private fun bindSeller(activity: Activity, dialog: Dialog, listing: Listing) {
        val row = dialog.findViewById<View>(R.id.detailSeller)
        val nameView = dialog.findViewById<TextView>(R.id.detailSellerName)
        val initialView = dialog.findViewById<TextView>(R.id.detailSellerInitial)
        val schoolView = dialog.findViewById<TextView>(R.id.detailSellerSchool)
        val ratingView = dialog.findViewById<TextView>(R.id.detailSellerRating)

        val listingName = listing.sellerName.ifBlank {
            activity.getString(R.string.detail_seller_fallback)
        }
        showSellerName(activity, nameView, initialView, listingName)
        schoolView.isVisible = false
        ratingView.text = activity.getString(R.string.detail_seller_new)

        if (listing.sellerId.isBlank()) {
            // Nothing to open, so the row stays informational and is not announced as a button.
            row.isClickable = false
            row.isFocusable = false
            row.contentDescription = null
            dialog.findViewById<View>(R.id.detailSellerChevron).isVisible = false
            return
        }
        openSellerProfileOnTap(activity, dialog, row, listing, listingName)

        ListingRepo.getUserProfile(listing.sellerId) { profile ->
            // The sheet may have been dismissed while Firestore answered.
            if (!dialog.isShowing) return@getUserProfile
            profile?.displayName?.takeIf { it.isNotBlank() }
                ?.let {
                    showSellerName(activity, nameView, initialView, it)
                    row.contentDescription =
                        activity.getString(R.string.detail_view_seller_profile, it)
                }

            val school = profile?.school.orEmpty()
            schoolView.isVisible = school.isNotBlank()
            schoolView.text = school

            val ratings = profile?.ratingCount ?: 0L
            ratingView.text = if (ratings > 0) {
                val stars = "%.1f".format(profile?.rating ?: 0.0)
                activity.getString(R.string.detail_seller_rating, stars, ratings)
            } else {
                activity.getString(R.string.detail_seller_new)
            }
        }
    }

    /** Tapping the seller row opens their profile; a signed-in student's own listing opens theirs. */
    private fun openSellerProfileOnTap(
        activity: Activity,
        dialog: Dialog,
        row: View,
        listing: Listing,
        sellerName: String
    ) {
        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = activity.getString(R.string.detail_view_seller_profile, sellerName)
        row.setOnClickListener {
            dialog.dismiss()
            val isSelf = listing.sellerId == FirebaseAuth.getInstance().currentUser?.uid
            if (isSelf) {
                activity.startActivity(Intent(activity, ProfileActivity::class.java))
            } else {
                activity.startActivity(
                    SellerProfileActivity.intent(activity, sellerId = listing.sellerId, sellerName = sellerName)
                )
            }
        }
    }

    /** The name carries a green check because every NOX account is a verified student. */
    private fun showSellerName(
        activity: Activity,
        nameView: TextView,
        initialView: TextView,
        name: String
    ) {
        val label = "$name ✓"
        nameView.text = SpannableString(label).apply {
            setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(activity, R.color.nox_accent_text)
                ),
                label.length - 1,
                label.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        nameView.contentDescription =
            activity.getString(R.string.detail_seller_verified_name, name)
        initialView.text = name.take(1).uppercase()
    }
}
