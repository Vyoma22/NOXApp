package com.example.noxapp.data

import com.example.noxapp.models.Listing
import com.example.noxapp.models.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.lang.Exception

object ListingRepo {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val listingsRef by lazy { db.collection("listings") }

    // Fetch listings for Home; seeds dummy items if database listings collection is empty
    fun loadHomeFeed(
        onSuccess: (featured: List<Listing>, recent: List<Listing>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        listingsRef.orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    seedInitialData {
                        loadHomeFeed(onSuccess, onError)
                    }
                } else {
                    val allListings = snapshot.toObjects(Listing::class.java)
                    val featured = allListings.filter { it.featured }
                    val recent = allListings.take(10)
                    onSuccess(featured, recent)
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
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

    private fun seedInitialData(onComplete: () -> Unit) {
        val sampleItems = listOf(
            Listing(
                title = "MacBook Air",
                price = 550.0,
                category = "Electronics",
                condition = "Like New",
                description = "M1 2020, 8GB RAM, 256GB SSD in great condition.",
                pickupLocation = "Ontario Tech (North Campus)",
                featured = true,
                createdAt = System.currentTimeMillis() - 10000
            ),
            Listing(
                title = "Desk Chair",
                price = 45.0,
                category = "Furniture",
                condition = "Good",
                description = "Ergonomic office chair, lightly used.",
                pickupLocation = "Durham College",
                featured = true,
                createdAt = System.currentTimeMillis() - 20000
            ),
            Listing(
                title = "Textbook Set",
                price = 40.0,
                category = "Books",
                condition = "Used",
                description = "Engineering and software textbook bundle.",
                pickupLocation = "Ontario Tech (North Campus)",
                featured = true,
                createdAt = System.currentTimeMillis() - 30000
            ),
            Listing(
                title = "Tote Bag",
                price = 20.0,
                category = "Accessories",
                condition = "Like New",
                description = "Sturdy canvas tote bag.",
                pickupLocation = "Ontario Tech (North Campus)",
                featured = false,
                createdAt = System.currentTimeMillis() - 40000
            ),
            Listing(
                title = "Houseplant",
                price = 15.0,
                category = "Decor",
                condition = "New",
                description = "Potted succulent ready for dorm desk.",
                pickupLocation = "Durham College",
                featured = false,
                createdAt = System.currentTimeMillis() - 50000
            ),
            Listing(
                title = "Winter Jacket",
                price = 35.0,
                category = "Clothing",
                condition = "Good",
                description = "Warm blue winter coat, size Medium.",
                pickupLocation = "Ontario Tech (North Campus)",
                featured = false,
                createdAt = System.currentTimeMillis() - 60000
            )
        )

        val batch = db.batch()
        for (item in sampleItems) {
            val docRef = listingsRef.document()
            item.id = docRef.id
            batch.set(docRef, item)
        }

        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }
}