package com.example.noxapp.core

import android.app.Activity
import com.example.noxapp.R
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.Listing
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

/**
 * Optimistic save/unsave shared by Home, Browse, Profile and the seller profile.
 *
 * The heart moves before the write lands, a second tap while one is in flight is ignored, and a
 * failed write rolls the heart back. [applyLocally] stays with the screen because each one keeps
 * its own saved-id set and refreshes its own adapters.
 */
class SaveToggle(
    private val activity: Activity,
    private val rootId: Int,
    private val anchorId: Int?,
    private val savedMessage: Int,
    private val unsavedMessage: Int,
    private val applyLocally: (String, Boolean) -> Unit
) {
    private val pending = mutableSetOf<String>()

    fun toggle(listing: Listing, shouldSave: Boolean, showUndo: Boolean = true) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!pending.add(listing.id)) return

        applyLocally(listing.id, shouldSave)
        ListingRepo.toggleSaveItem(uid, listing.id, shouldSave) { success ->
            pending.remove(listing.id)
            if (activity.isDestroyed) return@toggleSaveItem
            if (!success) {
                applyLocally(listing.id, !shouldSave)
                snackbar(R.string.save_failed).show()
            } else if (showUndo) {
                snackbar(if (shouldSave) savedMessage else unsavedMessage)
                    .setAction(R.string.undo) { toggle(listing, !shouldSave, showUndo = false) }
                    .show()
            }
        }
    }

    private fun snackbar(message: Int): Snackbar {
        val bar = Snackbar.make(activity.findViewById(rootId), message, Snackbar.LENGTH_LONG)
        anchorId?.let { bar.setAnchorView(it) }
        return bar
    }
}
