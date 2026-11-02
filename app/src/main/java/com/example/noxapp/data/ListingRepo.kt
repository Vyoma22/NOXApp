package com.example.noxapp.data

import com.example.noxapp.models.Listing
import com.example.noxapp.models.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.lang.Exception

object ListingRepo {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val listingsRef by lazy { db.collection("listings") }

    fun loadAllListings(onSuccess: (List<Listing>) -> Unit, onError: (Exception) -> Unit) {
        listingsRef.orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { it.toListing() })
            }
            .addOnFailureListener(onError)
    }

    /** Reads one listing for the edit form. */
    fun getListing(listingId: String, onResult: (Listing?) -> Unit) {
        listingsRef.document(listingId).get()
            .addOnSuccessListener { doc -> onResult(doc.toListing()) }
            .addOnFailureListener { onResult(null) }
    }

    suspend fun updateListing(listing: Listing) {
        listingsRef.document(listing.id).set(listing).await()
    }

    /**
     * Deletes a listing and its uploaded photos. The document goes first so the listing
     * disappears from every feed even if a storage object is already gone.
     */
    fun deleteListing(listing: Listing, onComplete: (Boolean) -> Unit) {
        listingsRef.document(listing.id).delete()
            .addOnSuccessListener {
                val storage = FirebaseStorage.getInstance()
                listing.imageUrls.forEach { url ->
                    runCatching { storage.getReferenceFromUrl(url).delete() }
                }
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    // ponytail: documents written before the model merge use name/location/imageUrl.
    // Delete these fallbacks once the old listings have been re-posted.
    private fun DocumentSnapshot.toListing(): Listing? {
        val listing = toObject(Listing::class.java) ?: return null
        return listing.copy(
            id = id,
            title = listing.title.ifBlank { getString("name").orEmpty() },
            pickupLocation = getString("location") ?: listing.pickupLocation,
            imageUrls = listing.imageUrls.ifEmpty { listOfNotNull(getString("imageUrl")) }
        )
    }

    // Fetch complete user profile from Firestore
    fun getUserProfile(userId: String, onResult: (UserProfile?) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val profile = doc.toObject(UserProfile::class.java)
                    profile?.uid = doc.id
                    onResult(profile)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // Toggle save/favorite state in the user's Firestore document
    fun toggleSaveItem(userId: String, itemId: String, shouldSave: Boolean, onComplete: (Boolean) -> Unit) {
        val userDoc = db.collection("users").document(userId)
        val updateOperation = if (shouldSave) {
            FieldValue.arrayUnion(itemId)
        } else {
            FieldValue.arrayRemove(itemId)
        }

        userDoc.update("savedItemIds", updateOperation)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Fetch user's saved item IDs
    fun getSavedItemIds(userId: String, onResult: (List<String>) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ids = doc.get("savedItemIds") as? List<String> ?: emptyList()
                onResult(ids)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    /** Removes the student's listings and profile document before Firebase Auth deletes the user. */
    suspend fun deleteAccountData(userId: String) {
        val snapshot = listingsRef.whereEqualTo("sellerId", userId).get().await()
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.delete(db.collection("users").document(userId))
        batch.commit().await()
    }

}