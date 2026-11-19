
package com.example.noxapp.listing

import com.example.noxapp.listing.Listing
import com.example.noxapp.profile.SellerProfile
import com.example.noxapp.profile.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    /** The only account fields another student may read. Mirrors the sellerProfiles rules. */
    private val publicSellerFields =
        listOf("displayName", "school", "gradYear", "rating", "ratingCount")

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

    /**
     * Writes only the fields the edit form owns.
     *
     * A whole-document write would push the form's stale snapshot back over everything,
     * restoring the viewCount from before the edit and undoing a sale that completed while
     * the seller was typing. Counters and workflow state stay with the server.
     */
    suspend fun updateListing(listing: Listing) {
        listingsRef.document(listing.id)
            .update(
                mapOf(
                    "title" to listing.title,
                    "price" to listing.price,
                    "category" to listing.category,
                    "condition" to listing.condition,
                    "description" to listing.description,
                    "pickupLocation" to listing.pickupLocation,
                    "imageUrls" to listing.imageUrls,
                    "latitude" to listing.latitude,
                    "longitude" to listing.longitude,
                    "previousPrice" to listing.previousPrice
                )
            )
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

    /**
     * Reads a listing through [ListingFields], which takes each value only if it has the expected
     * type. The typed getters and convertToCustomClass both throw on a malformed document, which
     * would crash inside the feed's success listener and take every other listing down with it.
     */
    private fun DocumentSnapshot.toListing(): Listing? {
        if (!exists()) return null
        return ListingFields.toListing(id, data.orEmpty())
    }

    // --------------------------------------------------
    // GET USER PROFILE
    // --------------------------------------------------

    /** Reads the signed-in student's own account document. Other students' are not readable. */
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
    // GET PUBLIC SELLER PROFILE
    // --------------------------------------------------

    /**
     * Reads the seller card another student is allowed to see.
     *
     * users/{uid} is owner-only, so this reads the public projection instead. A missing
     * document answers null and the caller keeps the name already on the listing.
     */
    fun getSellerProfile(
        userId: String,
        onResult: (SellerProfile?) -> Unit
    ) {
        db.collection("sellerProfiles").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                val defaults = SellerProfile()
                onResult(
                    SellerProfile(
                        uid = doc.id,
                        displayName = doc.get("displayName") as? String,
                        school = doc.get("school") as? String,
                        gradYear = (doc.get("gradYear") as? Number)?.toLong(),
                        rating = (doc.get("rating") as? Number)?.toDouble() ?: defaults.rating,
                        ratingCount = (doc.get("ratingCount") as? Number)?.toLong()
                            ?: defaults.ratingCount
                    )
                )
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    /**
     * Publishes the owner's public fields when the projection is missing.
     *
     * Accounts created before the read split only have users/{uid}, which other students can
     * no longer read. One copy per account restores their seller card; after that this is a
     * single read that finds the document and stops.
     *
     * The values are copied raw, not through [UserProfile], because the rules compare the
     * projection against the account document field by field. A default filled in here (0.0
     * for a rating the account never stored) would not match the absent field over there.
     */
    fun ensureSellerProfile(userId: String) {
        val publicRef = db.collection("sellerProfiles").document(userId)
        publicRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) return@addOnSuccessListener
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { account ->
                        val fields = account.data ?: return@addOnSuccessListener
                        val public = HashMap<String, Any>()
                        publicSellerFields.forEach { key ->
                            val value = fields[key]
                            if (value != null) public[key] = value
                        }
                        publicRef.set(public).addOnFailureListener { }
                    }
                    .addOnFailureListener { }
            }
            .addOnFailureListener { }
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

    /** Removes the student's listings and profile documents before Firebase Auth deletes the user.
     *
     * The profile delete is only accepted alongside an accountDeletions marker written in the
     * same batch. That marker is what authorizes the teardown, and it is permanent, so the
     * profile cannot be recreated afterwards with a later graduation date.
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
            db.collection("sellerProfiles").document(userId)
        )

        batch.delete(
            db.collection("users").document(userId)
        )

        batch.set(
            db.collection("accountDeletions").document(userId),
            mapOf(
                "uid" to userId,
                "requestedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit().await()
    }
}
