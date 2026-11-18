
package com.example.noxapp.data

import com.example.noxapp.models.Listing
import com.example.noxapp.models.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.lang.Exception

object ListingRepo {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val listingsRef by lazy {
        db.collection("listings")
    }

    // --------------------------------------------------
    // LOAD ALL LISTINGS
    // Used by My Profile, including sold listings.
    // --------------------------------------------------

    fun loadAllListings(
        onSuccess: (List<Listing>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        listingsRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.mapNotNull {
                        it.toListing()
                    }
                )
            }
            .addOnFailureListener(onError)
    }

    // --------------------------------------------------
    // NEW: LOAD PUBLIC LISTINGS
    // Hide sold items from Home, Browse, and Map.
    // --------------------------------------------------

    fun loadPublicListings(
        onSuccess: (List<Listing>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        loadAllListings(
            onSuccess = { listings ->
                onSuccess(
                    listings.filter { listing ->
                        !listing.sold && !listing.salePending
                    }
                )
            },
            onError = onError
        )
    }

    // --------------------------------------------------
    // GET ONE LISTING
    // --------------------------------------------------

    /** Reads one listing for the edit form. */
    fun getListing(
        listingId: String,
        onResult: (Listing?) -> Unit
    ) {
        listingsRef.document(listingId).get()
            .addOnSuccessListener { doc ->
                onResult(doc.toListing())
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // --------------------------------------------------
    // UPDATE LISTING
    // --------------------------------------------------

    suspend fun updateListing(listing: Listing) {
        listingsRef.document(listing.id)
            .set(listing)
            .await()
    }

    // --------------------------------------------------
    // CREATE LISTING
    // --------------------------------------------------

    /**
     * Creates a listing with a server-generated creation time
     * and returns its document id.
     *
     * Listing.createdAt is a Long (epoch millis), and a Long
     * cannot hold the FieldValue.serverTimestamp sentinel.
     *
     * The document is written as an explicit field map
     * instead of serialising the data class.
     * Edits keep using updateListing.
     */
    suspend fun createListing(listing: Listing): String {

        val docRef = listingsRef.document()

        docRef.set(
            mapOf(
                "id" to docRef.id,
                "title" to listing.title,
                "price" to listing.price,
                "category" to listing.category,
                "condition" to listing.condition,
                "description" to listing.description,
                "pickupLocation" to listing.pickupLocation,
                "imageUrls" to listing.imageUrls,
                "sellerId" to listing.sellerId,
                "sellerName" to listing.sellerName,
                "featured" to listing.featured,
                "sold" to listing.sold,
                "salePending" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "latitude" to listing.latitude,
                "longitude" to listing.longitude
            )
        ).await()

        return docRef.id
    }

    // --------------------------------------------------
    // INCREMENT VIEW COUNT
    // --------------------------------------------------

    /**
     * Records one open of a listing with a server-side
     * increment, so concurrent viewers add up instead
     * of overwriting each other.
     *
     * Fire-and-forget: the detail sheet already shows
     * the new count optimistically.
     */
    fun incrementViewCount(listingId: String) {

        // A listing opened before its ID loaded
        // has nothing to count.
        if (listingId.isBlank()) return

        listingsRef.document(listingId)
            .update(
                "viewCount",
                FieldValue.increment(1)
            )
            .addOnFailureListener { }
    }

    // --------------------------------------------------
    // DELETE LISTING
    // --------------------------------------------------

    /**
     * Deletes a listing and its uploaded photos.
     * The document goes first so the listing disappears
     * from every feed even if a storage object is gone.
     */
    fun deleteListing(
        listing: Listing,
        onComplete: (Boolean) -> Unit
    ) {
        listingsRef.document(listing.id).delete()
            .addOnSuccessListener {

                val storage = FirebaseStorage.getInstance()

                listing.imageUrls.forEach { url ->
                    runCatching {
                        storage.getReferenceFromUrl(url).delete()
                    }
                }

                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    // --------------------------------------------------
    // CONVERT FIRESTORE DOCUMENT TO LISTING
    // --------------------------------------------------

    // Documents written before the model merge
    // use name/location/imageUrl.
    // Keep these fallbacks for older listings.

    private fun DocumentSnapshot.toListing(): Listing? {

        val raw = data ?: return null

        val listing = deserializeListing(raw)
            ?: return null

        return listing.copy(
            id = id,

            title = listing.title.ifBlank {
                getString("name").orEmpty()
            },

            pickupLocation =
                getString("location")
                    ?: listing.pickupLocation,

            imageUrls =
                listing.imageUrls.ifEmpty {
                    listOfNotNull(getString("imageUrl"))
                }
        )
    }

    // --------------------------------------------------
    // DESERIALIZE LISTING
    // --------------------------------------------------

    /**
     * Normalizes createdAt to epoch milliseconds.
     *
     * New documents store a Firestore Timestamp,
     * but Listing.createdAt expects a Long.
     */
    private fun DocumentSnapshot.deserializeListing(
        raw: Map<String, Any>
    ): Listing? {

        val normalised = HashMap(raw)

        val createdAt =
            ListingTimestamps.toMillis(
                normalised["createdAt"]
            )

        if (createdAt != null) {
            normalised["createdAt"] = createdAt
        } else {
            normalised.remove("createdAt")
        }

        return CustomClassMapper.convertToCustomClass(
            normalised,
            Listing::class.java,
            reference
        )
    }

    // --------------------------------------------------
    // GET USER PROFILE
    // --------------------------------------------------

    fun getUserProfile(
        userId: String,
        onResult: (UserProfile?) -> Unit
    ) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->

                if (doc.exists()) {

                    val profile =
                        doc.toObject(UserProfile::class.java)

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

    // --------------------------------------------------
    // SAVE / UNSAVE LISTINGS
    // --------------------------------------------------

    fun toggleSaveItem(
        userId: String,
        itemId: String,
        shouldSave: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        val userDoc =
            db.collection("users").document(userId)

        val updateOperation = if (shouldSave) {

            FieldValue.arrayUnion(itemId)

        } else {

            FieldValue.arrayRemove(itemId)
        }

        userDoc.update(
            "savedItemIds",
            updateOperation
        )
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    // --------------------------------------------------
    // GET SAVED LISTING IDS
    // --------------------------------------------------

    fun getSavedItemIds(
        userId: String,
        onResult: (List<String>) -> Unit
    ) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->

                @Suppress("UNCHECKED_CAST")
                val ids =
                    doc.get("savedItemIds") as? List<String>
                        ?: emptyList()

                onResult(ids)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // --------------------------------------------------
    // DELETE ACCOUNT DATA
    // --------------------------------------------------

    /** Removes the student's listings and profile
     * document before Firebase Auth deletes the user.
     */
    suspend fun deleteAccountData(userId: String) {

        val snapshot = listingsRef
            .whereEqualTo("sellerId", userId)
            .get()
            .await()

        val batch = db.batch()

        snapshot.documents.forEach {
            batch.delete(it.reference)
        }

        batch.delete(
            db.collection("users").document(userId)
        )

        batch.commit().await()
    }
}
