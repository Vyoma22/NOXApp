package com.example.noxapp.postlisting
import com.example.noxapp.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.data.ListingRepo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PostListing : AppCompatActivity() {

    var imagesAndVideos: List<Uri> = emptyList()
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val pickMultipleMedia =
        registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(5)
        ) { uris ->

            imagesAndVideos = uris

            if (imagesAndVideos.isNotEmpty()) {
                imagesAndVideos.forEach { uri ->
                    println("Selected image: $uri")
                }

                Toast.makeText(
                    this,
                    "Selected ${imagesAndVideos.size} images",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "No images selected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    suspend fun uploadImage(imageUri: Uri): String {
        val imageId = UUID.randomUUID().toString()
        val imagePath = "images/$imageId.jpg"

        val imageRef = FirebaseStorage.getInstance()
            .reference.child(imagePath)

        //Upload the image
        imageRef.putFile(imageUri).await()
        Log.d("PostListing", "Upload succeeded: $imagePath")

        //Get the download URL
        val imageUrl = imageRef.downloadUrl.await().toString()
        Log.d("PostListing", "Download URL: $imageUrl")

        return imageUrl
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_post_listing)

        val cameraBtn: ImageButton = findViewById(R.id.btnCamera)
        val addImagesBtn: ImageButton = findViewById(R.id.btnAddImage)
        val postListing: Button = findViewById(R.id.btnPostList)
        val itmName: EditText = findViewById(R.id.etItmName)
        val itmCategory: Spinner = findViewById(R.id.spiCategory)
        val itmPrice: EditText = findViewById(R.id.etPrice)
        val itmConditions: RadioGroup = findViewById(R.id.radgroupCondition)
        val itmDesc: EditText = findViewById(R.id.etDescription)
        val listingLocation: Spinner = findViewById(R.id.spiLocation)

        var adapter= ArrayAdapter.createFromResource(this, R.array.categories, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itmCategory.adapter = adapter

        adapter = ArrayAdapter.createFromResource(this, R.array.locations, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        listingLocation.adapter = adapter

        cameraBtn.setOnClickListener {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            startActivity(intent)
        }

        addImagesBtn.setOnClickListener {
            pickMultipleMedia.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts
                        .PickVisualMedia.ImageOnly
                )
            )
        }

        postListing.setOnClickListener {
            val name = itmName.text.toString()
            val category = itmCategory.selectedItem.toString()
            val price = itmPrice.getText().toString().toDoubleOrNull()
            val desc = itmDesc.text.toString()
            val location = listingLocation.selectedItem.toString()
            val conditionID = itmConditions.checkedRadioButtonId

            if (imagesAndVideos.isEmpty() || price == null || price < 0 || name.isBlank()
                || category.isBlank() || desc.isBlank() || location.isBlank()) {
                Toast.makeText(this, "Invalid Input(s)", Toast.LENGTH_LONG).show()
            }
            else {
                val listingsRef = firestore.collection("listings")
                val condition = findViewById<RadioButton>(conditionID)
                val conditionTxt = condition.text.toString()

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val imageURLS = mutableListOf<String>()

                if (uid != null) {
                    ListingRepo.getUserProfile(uid) { profile ->
                        if (profile != null) {
                            val displayName = profile.displayName
                            if (displayName != null) {
                                lifecycleScope.launch {
                                    try {
                                        val uploadedUrls = imagesAndVideos.map { imageUri ->
                                            async {
                                                uploadImage(imageUri)
                                            }
                                        }.awaitAll()

                                        imageURLS.clear()
                                        imageURLS.addAll(uploadedUrls)

                                        Log.d("PostListing", "All image URLs: $imageURLS")

                                        val listing = Listing(
                                            sellerId = uid,
                                            sellerName = displayName,
                                            name = name,
                                            category = category,
                                            price = price,
                                            description = desc,
                                            location = location,
                                            condition = conditionTxt,
                                            imageUrls = imageURLS
                                        )
                                        val listingRef = listingsRef.document()
                                        val listingWithId = listing.copy(id = listingRef.id)
                                        listingRef.set(listingWithId)
                                            .addOnSuccessListener {
                                                Log.d("Firestore", "Listing added successfully")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("Firestore", "Error adding listing", e)
                                            }

                                    } catch (e: Exception) {
                                        Log.e("PostListing", "Failed to upload images", e)
                                    }
                                }

                            }
                            else {
                                Toast.makeText(this, "Invalid display name", Toast.LENGTH_LONG).show()
                            }
                        }
                        else {
                            Toast.makeText(this, "Invalid profile", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                else {
                    Toast.makeText(this, "Invalid user ID", Toast.LENGTH_LONG).show()
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

}