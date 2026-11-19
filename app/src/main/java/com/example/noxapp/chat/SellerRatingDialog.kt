package com.example.noxapp.chat

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.noxapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SellerRatingDialog {

    fun show(
        context: Context,
        onSubmit: (
            stars: Int,
            review: String,
            dialog: AlertDialog
        ) -> Unit
    ) {

        // Load the rating layout we created.
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_rate_seller, null)

        val ratingBar =
            view.findViewById<RatingBar>(
                R.id.sellerRatingBar
            )

        val reviewInput =
            view.findViewById<EditText>(
                R.id.sellerReviewInput
            )

        val requiredText =
            view.findViewById<TextView>(
                R.id.ratingRequiredText
            )

        // Create the popup.
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setNegativeButton("Back", null)
            .setPositiveButton(
                "Submit Rating & Finish",
                null
            )
            .create()

        dialog.setOnShowListener {

            val submitButton = dialog.getButton(
                DialogInterface.BUTTON_POSITIVE
            )

            submitButton.setOnClickListener {

                val stars = ratingBar.rating.toInt()
                val review = reviewInput.text
                    .toString()
                    .trim()

                // A rating is required.
                if (stars !in 1..5) {

                    requiredText.text =
                        "Please select 1 to 5 stars"

                    requiredText.setTextColor(
                        android.graphics.Color.RED
                    )

                    return@setOnClickListener
                }

                // Pass the results to the caller.
                // The caller will eventually save to Firebase.
                onSubmit(stars, review, dialog)

                // Don't close automatically.
                // Later, we'll close only after Firebase
                // confirms the rating was saved.
            }
        }

        dialog.show()
    }
}
