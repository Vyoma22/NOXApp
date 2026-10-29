package com.example.noxapp.data

import com.example.noxapp.models.Listing
import com.example.noxapp.models.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.lang.Exception

object ListingRepo {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val listingsRef by lazy { db.collection("listings") }

    fun loadAllListings(onSuccess: (List<Listing>) -> Unit, onError: (Exception) -> Unit) {
        listingsRef.orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Listing::class.java)?.apply { id = doc.id }
                })
            }
            .addOnFailureListener(onError)
    }

    fun saveGeoPreference(
        userId: String, lat: Double, lng: Double, radiusKm: Double,
        onComplete: (Boolean) -> Unit
    ) {
        db.collection("users").document(userId).set(
            mapOf("preferredLat" to lat, "preferredLng" to lng, "preferredRadiusKm" to radiusKm),
            SetOptions.merge()
        ).addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun clearGeoPreference(userId: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(userId).update(
            mapOf(
                "preferredLat" to FieldValue.delete(),
                "preferredLng" to FieldValue.delete(),
                "preferredRadiusKm" to FieldValue.delete()
            )
        ).addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
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
    fun toggleSaveItem(userId: String, itemId: String, shouldSave: Boolean, onComplete: () -> Unit) {
        val userDoc = db.collection("users").document(userId)
        val updateOperation = if (shouldSave) {
            FieldValue.arrayUnion(itemId)
        } else {
            FieldValue.arrayRemove(itemId)
        }

        userDoc.update("savedItemIds", updateOperation)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
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

}