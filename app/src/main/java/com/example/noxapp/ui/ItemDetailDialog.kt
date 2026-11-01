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
import android.widget.TextView
import android.widget.Toast
import com.example.noxapp.R
import com.example.noxapp.models.Listing

object ItemDetailDialog {
    fun show(activity: Activity, listing: Listing) {
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
        dialog.findViewById<ImageButton>(R.id.detailClose).setOnClickListener { dialog.dismiss() }
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
