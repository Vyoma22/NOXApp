package com.example.noxapp.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import coil.load
import com.example.noxapp.R
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
        dialog.findViewById<ImageView>(R.id.detailImage).load(listing.imageUrls.firstOrNull()) {
            placeholder(R.color.nox_field_bg)
            error(R.color.nox_field_bg)
        }
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
        dialog.show()
    }
}
