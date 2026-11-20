package com.example.noxapp.listing

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.example.noxapp.R
import com.example.noxapp.chat.ChatConversationActivity
import com.example.noxapp.chat.ChatRepository
import com.example.noxapp.map.MapActivity
import com.example.noxapp.postlisting.PostListing
import com.example.noxapp.profile.ProfileActivity
import com.example.noxapp.profile.SellerProfileActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

object ItemDetailDialog {
    fun show(
        activity: Activity,
        listing: Listing,
        onListingChanged: (() -> Unit)? = null
    ) {
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(R.layout.dialog_item_details)
        dialog.setCanceledOnTouchOutside(true)

        val scrollView = dialog.findViewById<ScrollView>(R.id.detailScroll)
        if (scrollView != null) {
            val baseBottomPadding = scrollView.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                view.updatePadding(bottom = baseBottomPadding + navBarBottom)
                insets
            }
        }

        dialog.setOnShowListener {
            dialog.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.findViewById<TextView>(R.id.detailTitle)?.text = listing.title
        bindPrice(activity, dialog, listing)
        dialog.findViewById<TextView>(R.id.detailCondition)?.text = listing.condition
        dialog.findViewById<TextView>(R.id.detailCategory)?.text = listing.category
        dialog.findViewById<TextView>(R.id.detailDescription)?.text = listing.description
        dialog.findViewById<TextView>(R.id.detailLocation)?.text = "Pickup: ${listing.pickupLocation}"
        bindPickupLocation(activity, dialog, listing)

        MediaGallery(activity, dialog, listing.imageUrls).bind()

        dialog.findViewById<ImageButton>(R.id.detailClose)?.setOnClickListener { dialog.dismiss() }

        val isOwner = listing.sellerId.isNotBlank() &&
                listing.sellerId == FirebaseAuth.getInstance().currentUser?.uid
        dialog.findViewById<ViewGroup>(R.id.ownerActions)?.isVisible = isOwner
        bindViewCount(activity, dialog, listing, isOwner)

        val reportButton = dialog.findViewById<Button>(R.id.reportListingButton)
        reportButton?.isVisible = !isOwner
        reportButton?.setOnClickListener {
            dialog.dismiss()
            sendListingReport(activity, listing)
        }

        dialog.findViewById<Button>(R.id.editListingButton)?.setOnClickListener {
            dialog.dismiss()
            activity.startActivity(
                Intent(activity, PostListing::class.java)
                    .putExtra(PostListing.EXTRA_LISTING_ID, listing.id)
            )
        }

        dialog.findViewById<Button>(R.id.deleteListingButton)?.setOnClickListener {
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

        val messageSellerButton = dialog.findViewById<Button>(R.id.messageSellerButton)
        messageSellerButton?.isVisible = !isOwner
        messageSellerButton?.setOnClickListener {
            if (listing.id.isBlank()) {
                Toast.makeText(
                    activity,
                    "This listing cannot open a chat",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            messageSellerButton.isEnabled = false

            ChatRepository.createOrOpenConversation(
                listingId = listing.id,
                onSuccess = { conversationId ->
                    messageSellerButton.isEnabled = true
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        val intent = Intent(
                            activity,
                            ChatConversationActivity::class.java
                        ).apply {
                            putExtra("listingId", listing.id)
                            putExtra("conversationId", conversationId)
                        }
                        activity.startActivity(intent)
                        dialog.dismiss()
                    }
                },
                onError = { error ->
                    messageSellerButton.isEnabled = true
                    Toast.makeText(
                        activity,
                        "Unable to open chat: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        bindSeller(activity, dialog, listing)
        dialog.show()
    }

    private fun sendListingReport(activity: Activity, listing: Listing) {
        val report = ListingReport(
            subjectFormat = activity.getString(R.string.listing_report_subject_format),
            bodyFormat = activity.getString(R.string.listing_report_body_format)
        )
        val inbox = activity.getString(R.string.settings_report_email)
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$inbox")
            putExtra(Intent.EXTRA_SUBJECT, report.subject(listing))
            putExtra(Intent.EXTRA_TEXT, report.body(listing))
        }
        runCatching {
            activity.startActivity(
                Intent.createChooser(emailIntent, activity.getString(R.string.listing_report_chooser))
            )
        }.onFailure {
            Toast.makeText(activity, R.string.listing_report_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun bindPrice(activity: Activity, dialog: Dialog, listing: Listing) {
        val newPrice = "$${formatPrice(listing.price)}"
        dialog.findViewById<TextView>(R.id.detailPrice).text = newPrice

        val previousPriceView = dialog.findViewById<TextView>(R.id.detailPreviousPrice)
        val previousPrice = listing.previousPrice
        if (previousPrice == null || previousPrice <= listing.price) {
            previousPriceView.isVisible = false
            return
        }
        val oldPrice = "$${formatPrice(previousPrice)}"
        previousPriceView.text = oldPrice
        previousPriceView.paintFlags =
            previousPriceView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        previousPriceView.contentDescription =
            activity.getString(R.string.detail_previous_price, oldPrice, newPrice)
        previousPriceView.isVisible = true
    }

    private fun bindViewCount(activity: Activity, dialog: Dialog, listing: Listing, isOwner: Boolean) {
        val views = ListingSignals.displayedViewCount(listing, isOwner)
        dialog.findViewById<TextView>(R.id.detailViewCount).text = if (views == 0L) {
            activity.getString(R.string.detail_views_none)
        } else {
            activity.resources.getQuantityString(
                R.plurals.detail_views,
                views.toInt(),
                views.toInt()
            )
        }
        if (!isOwner) {
            ListingRepo.incrementViewCount(listing.id)
        }
    }

    private fun bindPickupLocation(activity: Activity, dialog: Dialog, listing: Listing) {
        val (latitude, longitude) = CampusLocations.resolve(listing)
        dialog.findViewById<View>(R.id.detailLocationRow).apply {
            contentDescription = activity.getString(R.string.detail_location_view_map)
            setOnClickListener {
                dialog.dismiss()
                activity.startActivity(
                    Intent(activity, MapActivity::class.java)
                        .putExtra(MapActivity.EXTRA_VIEW_LOCATION, true)
                        .putExtra(MapActivity.EXTRA_CENTER_LAT, latitude)
                        .putExtra(MapActivity.EXTRA_CENTER_LNG, longitude)
                        .putExtra(MapActivity.EXTRA_LOCATION_LABEL, listing.pickupLocation)
                        .putExtra(MapActivity.EXTRA_LOCATION_TITLE, listing.title)
                )
            }
        }
    }

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
            row.isClickable = false
            row.isFocusable = false
            row.contentDescription = null
            dialog.findViewById<View>(R.id.detailSellerChevron).isVisible = false
            return
        }
        openSellerProfileOnTap(activity, dialog, row, listing, listingName)

        ListingRepo.getSellerProfile(listing.sellerId) { profile ->
            if (!dialog.isShowing) return@getSellerProfile
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