package com.example.noxapp.postlisting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.MainActivity
import com.example.noxapp.ProfileActivity
import com.example.noxapp.R
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.CategoryCatalog
import com.example.noxapp.models.Listing
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class PostListing : AppCompatActivity() {

    private val imagesAndVideos: MutableList<Uri> = mutableListOf()
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var cameraTempUri: Uri? = null

    // Multiple Gallery Media Picker (capped so total does not exceed 5)
    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
            if (uris.isNotEmpty()) {
                val availableSlots = 5 - imagesAndVideos.size
                if (availableSlots <= 0) {
                    Toast.makeText(this, "Maximum 5 files reached", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                val toAdd = uris.take(availableSlots)
                imagesAndVideos.addAll(toAdd)

                updateMediaSubtitle()
                Toast.makeText(this, "Added ${toAdd.size} file(s). Total: ${imagesAndVideos.size}/5", Toast.LENGTH_SHORT).show()
            }
        }

    // Camera Capture Launcher
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraTempUri != null) {
                if (imagesAndVideos.size < 5) {
                    imagesAndVideos.add(cameraTempUri!!)
                    updateMediaSubtitle()
                    Toast.makeText(this, "Photo added! (${imagesAndVideos.size}/5)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Maximum 5 files reached", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_post_listing)

        val backBtn: ImageButton = findViewById(R.id.backBtn)
        val addImagesCard: View = findViewById(R.id.btnAddImage)
        val postListingBtn: Button = findViewById(R.id.btnPostList)
        val itmName: EditText = findViewById(R.id.etItmName)
        val itmCategory: Spinner = findViewById(R.id.spiCategory)
        val itmPrice: EditText = findViewById(R.id.etPrice)
        val itmConditions: RadioGroup = findViewById(R.id.radgroupCondition)
        val itmDesc: EditText = findViewById(R.id.etDescription)
        val listingLocation: Spinner = findViewById(R.id.spiLocation)

        // Dropdown adapters
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, CategoryCatalog.NAMES)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itmCategory.adapter = categoryAdapter

        val locationAdapter = ArrayAdapter.createFromResource(this, R.array.locations, android.R.layout.simple_spinner_item)
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        listingLocation.adapter = locationAdapter

        // Back button
        backBtn.setOnClickListener { finish() }

        // Trigger Bottom Sheet when tapping the media box
        addImagesCard.setOnClickListener {
            if (imagesAndVideos.size >= 5) {
                Toast.makeText(this, "Maximum 5 files allowed.", Toast.LENGTH_SHORT).show()
            } else {
                showMediaSourceDialog()
            }
        }

        // Post Listing (Database & Storage write)
        postListingBtn.setOnClickListener {
            val name = itmName.text.toString().trim()
            val category = itmCategory.selectedItem?.toString().orEmpty()
            val price = itmPrice.text.toString().trim().toDoubleOrNull()
            val desc = itmDesc.text.toString().trim()
            val location = listingLocation.selectedItem?.toString().orEmpty()
            val conditionID = itmConditions.checkedRadioButtonId

            if (imagesAndVideos.isEmpty()) {
                Toast.makeText(this, "Please attach at least one photo or video.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (name.isBlank() || price == null || price < 0 || category.isBlank() || desc.isBlank() || location.isBlank() || conditionID == -1) {
                Toast.makeText(this, "Please complete all required fields.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "You must be signed in to post a listing.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val condition = findViewById<RadioButton>(conditionID).text.toString()

            // Lock the button while network calls run
            postListingBtn.isEnabled = false
            postListingBtn.text = "Uploading..."

            ListingRepo.getUserProfile(uid) { profile ->
                val displayName = profile?.displayName ?: "Student"

                lifecycleScope.launch {
                    try {
                        // 1. Upload all media files to Firebase Storage in parallel
                        val uploadedUrls = imagesAndVideos.map { uri ->
                            async { uploadMediaFile(uri) }
                        }.awaitAll()

                        // 2. Prepare Firestore document
                        val listingsCollection = firestore.collection("listings")
                        val newDocRef = listingsCollection.document()

                        val listing = Listing(
                            id = newDocRef.id,
                            sellerId = uid,
                            sellerName = displayName,
                            title = name,
                            category = category,
                            price = price,
                            description = desc,
                            pickupLocation = location,
                            condition = condition,
                            imageUrls = uploadedUrls
                        )

                        // 3. Write document to Firestore
                        newDocRef.set(listing).await()

                        Log.d("PostListing", "Listing successfully written to Firestore: ${newDocRef.id}")
                        Toast.makeText(this@PostListing, "Listing posted successfully!", Toast.LENGTH_SHORT).show()
                        finish()

                    } catch (e: Exception) {
                        Log.e("PostListing", "Failed to complete listing submission", e)
                        Toast.makeText(this@PostListing, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                        postListingBtn.isEnabled = true
                        postListingBtn.text = "Post Listing"
                    }
                }
            }
        }

        setupBottomNavigation()

        val root = findViewById<View>(R.id.rootContainer) ?: findViewById(R.id.formScrollView)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            findViewById<View>(R.id.bottomNavBar)?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun showMediaSourceDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_media_picker, null)

        sheetView.findViewById<LinearLayout>(R.id.optionCamera).setOnClickListener {
            dialog.dismiss()
            launchCamera()
        }

        sheetView.findViewById<LinearLayout>(R.id.optionGallery).setOnClickListener {
            dialog.dismiss()
            pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun launchCamera() {
        val tempFile = File.createTempFile("captured_media_", ".jpg", externalCacheDir)
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            tempFile
        )
        cameraTempUri = uri
        takePhotoLauncher.launch(uri)
    }

    private suspend fun uploadMediaFile(uri: Uri): String {
        val fileId = UUID.randomUUID().toString()
        val path = "listings/$fileId.jpg"
        val ref = storage.reference.child(path)

        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private fun updateMediaSubtitle() {
        findViewById<TextView>(R.id.btnText2)?.text = "${imagesAndVideos.size} of 5 files selected"
    }

    private fun setupBottomNavigation() {
        findViewById<BottomNavigationView>(R.id.bottomNavBar).apply {
            selectedItemId = R.id.navPost

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navHome -> {
                        startActivity(
                            Intent(this@PostListing, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        finish()
                        true
                    }
                    R.id.navBrowse -> {
                        startActivity(
                            Intent(this@PostListing, com.example.noxapp.BrowseActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        finish()
                        true
                    }
                    R.id.navPost -> true
                    R.id.navChat -> {
                        Toast.makeText(this@PostListing, "Chat inbox coming soon", Toast.LENGTH_SHORT).show()
                        false
                    }
                    R.id.navProfile -> {
                        startActivity(Intent(this@PostListing, ProfileActivity::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }

            setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navPost) {
                    findViewById<android.widget.ScrollView>(R.id.formScrollView)?.smoothScrollTo(0, 0)
                }
            }
        }
    }
}