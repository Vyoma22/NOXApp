package com.example.noxapp.postlisting

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.noxapp.MainActivity
import com.example.noxapp.MapActivity
import com.example.noxapp.ProfileActivity
import com.example.noxapp.R
import com.example.noxapp.data.CampusLocations
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.CategoryCatalog
import com.example.noxapp.models.Listing
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale
import java.util.UUID

class PostListing : AppCompatActivity() {

    private val imagesAndVideos: MutableList<Uri> = mutableListOf()
    private val existingImageUrls: MutableList<String> = mutableListOf()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var itmName: EditText
    private lateinit var itmCategory: Spinner
    private lateinit var itmPrice: EditText
    private lateinit var itmConditions: RadioGroup
    private lateinit var itmDesc: EditText
    private lateinit var listingLocation: Spinner
    private lateinit var postListingButton: Button
    private lateinit var mediaPreviewLabel: TextView
    private lateinit var mediaPreviewScroll: HorizontalScrollView
    private lateinit var mediaPreviewRow: LinearLayout
    private lateinit var formScrollView: ScrollView
    private lateinit var postErrorMedia: TextView
    private lateinit var postErrorTitle: TextView
    private lateinit var postErrorPrice: TextView
    private lateinit var postErrorDescription: TextView

    private var validationAttempted = false

    private var cameraTempUri: Uri? = null
    private var editingListing: Listing? = null
    private var pickedLat: Double? = null
    private var pickedLng: Double? = null
    private var pickedLabel: String? = null
    private var suppressLocationClear = false

    private val locationOptions = mutableListOf<String>()
    private var customLocationIndex = 0
    private var previousBuildingIndex = 0
    private var pendingCustomPickRevertIndex: Int? = null

    private val maxMedia = 5

    private val pickMultipleMedia = //get max of 5 items from gallery
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
            if (uris.isNotEmpty()) {
                val availableSlots = maxMedia - existingImageUrls.size - imagesAndVideos.size
                if (availableSlots <= 0) {
                    Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                val toAdd = uris.take(availableSlots)
                imagesAndVideos.addAll(toAdd) //add images and videos to list

                refreshMediaUi()
                Toast.makeText(
                    this,
                    getString(R.string.post_media_added, toAdd.size, mediaCount()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success -> //open camera
            if (success && cameraTempUri != null) {
                if (mediaCount() < maxMedia) { //keep media under max number
                    imagesAndVideos.add(cameraTempUri!!) //add photo uri to ist
                    refreshMediaUi()
                    Toast.makeText(
                        this,
                        getString(R.string.post_photo_added, mediaCount()),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null ||
                !data.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false)
            ) {
                // Backing out of the picker must not leave the custom entry half-applied.
                revertPendingCustomPick()
                return@registerForActivityResult
            }

			//get selected location coordinates and label
            pickedLat = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0)
            pickedLng = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LNG, 0.0)
            pickedLabel = data.getStringExtra(MapActivity.EXTRA_LOCATION_LABEL)
                ?: getString(R.string.post_location_pinned)
            pendingCustomPickRevertIndex = null
            selectCustomLocationEntry()
            renderPickedLocation() //update ui with location
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_post_listing)
        setupInsets()

		//get ids of views
        itmName = findViewById(R.id.etItmName)
        itmCategory = findViewById(R.id.spiCategory)
        itmPrice = findViewById(R.id.etPrice)
        itmConditions = findViewById(R.id.radgroupCondition)
        itmDesc = findViewById(R.id.etDescription)
        listingLocation = findViewById(R.id.spiLocation)
        postListingButton = findViewById(R.id.btnPostList)
        mediaPreviewLabel = findViewById(R.id.mediaPreviewLabel)
        mediaPreviewScroll = findViewById(R.id.mediaPreviewScroll)
        mediaPreviewRow = findViewById(R.id.mediaPreviewRow)
        formScrollView = findViewById(R.id.formScrollView)
        postErrorMedia = findViewById(R.id.postErrorMedia)
        postErrorTitle = findViewById(R.id.postErrorTitle)
        postErrorPrice = findViewById(R.id.postErrorPrice)
        postErrorDescription = findViewById(R.id.postErrorDescription)
        refreshMediaUi()

		//set up ui
        setupSpinners()
        setupMediaPicker()
        setupMapPicker()
        setupInsetScrolling()
        setupBottomNavigation()

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        postListingButton.setOnClickListener { submitListing() }
        watchForFixes(itmName)
        watchForFixes(itmPrice)
        watchForFixes(itmDesc)

        intent.getStringExtra(EXTRA_LISTING_ID)?.takeIf { it.isNotBlank() }?.let(::loadListingForEdit)
    }

    private fun setupSpinners() {
		//populate category spinner
        val categoryAdapter = ArrayAdapter(this, R.layout.item_spinner, CategoryCatalog.NAMES)
        categoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        itmCategory.adapter = categoryAdapter

        locationOptions.clear()
        locationOptions.addAll(resources.getStringArray(R.array.locations))
        customLocationIndex = locationOptions.size
        locationOptions.add(getString(R.string.post_location_custom_option))

		//populate location spinner
        val locationAdapter = ArrayAdapter(this, R.layout.item_spinner, locationOptions)
        locationAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        listingLocation.adapter = locationAdapter
        listingLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressLocationClear) return
                if (PickupLocationSelection.isCustom(position, customLocationIndex)) {
                    onCustomLocationSelected()
                } else {
                    previousBuildingIndex = position
                    // A predefined building replaces a manual map pin.
                    clearPickedLocation()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupMediaPicker() {
        findViewById<View>(R.id.btnAddImage).setOnClickListener {
            if (mediaCount() >= maxMedia) { //check if max amount of media is reached
                Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
            } else {
                showMediaSourceDialog()
            }
        }
    }

    private fun setupMapPicker() {
        findViewById<MaterialButton>(R.id.btnPickOnMap).setOnClickListener { launchMapPicker() }
    }

    /** Opens the map in pick mode, starting from the current pin or the campus centre. */
    private fun launchMapPicker() {
        val lat = pickedLat ?: CampusLocations.ONTARIO_TECH.first
        val lng = pickedLng ?: CampusLocations.ONTARIO_TECH.second
        mapPickerLauncher.launch(
            Intent(this, MapActivity::class.java)
                .putExtra(MapActivity.EXTRA_PICK_LOCATION, true)
                .putExtra(MapActivity.EXTRA_CENTER_LAT, lat)
                .putExtra(MapActivity.EXTRA_CENTER_LNG, lng)
        )
    }

    private fun setupInsetScrolling() {
        // Android 11+ reports the IME as an inset; leaving adjustResize on would shrink the
        // form and then add the same keyboard height again as padding.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        val scroll = findViewById<ScrollView>(R.id.formScrollView)
        val tabBar = findViewById<View>(R.id.bottomNavBar)
        val base = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            // The form stops at the tab bar, so the keyboard only covers the slice of it that
            // reaches past the tab bar. Padding the whole keyboard height would lift the form by
            // the tab bar's height and leave an empty strip between the last field and the keys.
            val covered =
                (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - tabBar.height)
                    .coerceAtLeast(0)
            view.updatePadding(bottom = base + covered)
            if (covered > 0) view.post { scroll.scrollFocusedChildIntoView(covered) }
            insets
        }
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        val nav = findViewById<View>(R.id.bottomNavBar)
        val navBase = nav.paddingBottom

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            nav.updatePadding(bottom = navBase + bars.bottom)
            insets
        }
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
                        Toast.makeText(this@PostListing, R.string.post_chat_toast, Toast.LENGTH_SHORT).show()
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
                    findViewById<ScrollView>(R.id.formScrollView)?.smoothScrollTo(0, 0)
                }
            }
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
        val directory = externalCacheDir ?: cacheDir
		
		//create a temporary file to store the picture
        val tempFile = File.createTempFile("captured_media_", ".jpg", directory)
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            tempFile
        )
		
        cameraTempUri = uri //save image uri so it can be accessed later
        runCatching { takePhotoLauncher.launch(uri) } //open camera app
            .onFailure {
                Log.e("PostListing", "No camera app available", it)
                Toast.makeText(this, R.string.post_camera_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    private fun loadListingForEdit(listingId: String) {
        ListingRepo.getListing(listingId) { listing ->
            if (isDestroyed) return@getListing
            if (listing == null) {
                Toast.makeText(this, R.string.post_edit_missing, Toast.LENGTH_LONG).show()
                finish()
                return@getListing
            }
			
			//store the listing and restore its existing image urls
            editingListing = listing
            existingImageUrls.clear()
            existingImageUrls.addAll(listing.imageUrls)

			//update ui to indicate the user is editing an existing listing
            findViewById<TextView>(R.id.headerTitle).setText(R.string.post_edit_title)
            postListingButton.setText(R.string.post_save_changes)
			
			//populate fields with the listing's existing details
            itmName.setText(listing.title)
            itmPrice.setText(formatPrice(listing.price))
            itmDesc.setText(listing.description)
            itmCategory.setSelection(
                CategoryCatalog.NAMES.indexOfFirst { it.equals(listing.category, ignoreCase = true) }
                    .coerceAtLeast(0)
            )

            val buildings = resources.getStringArray(R.array.locations).toList()
            val locationIndex = PickupLocationSelection.selectionFor(listing.pickupLocation, buildings)
            val isCustomLocation = PickupLocationSelection.isCustom(locationIndex, customLocationIndex)
            if (isCustomLocation) {
                // Keep a custom location/map pin from the original post.
                pickedLabel = listing.pickupLocation.ifBlank { getString(R.string.post_location_pinned) }
                if (listing.latitude != 0.0 || listing.longitude != 0.0) {
                    pickedLat = listing.latitude
                    pickedLng = listing.longitude
                }
            } else {
                previousBuildingIndex = locationIndex
            }
            suppressLocationClear = true
            listingLocation.setSelection(locationIndex)
            suppressLocationClear = false

            itmConditions.check(
                when (listing.condition.lowercase(Locale.getDefault())) {
                    "new" -> R.id.radBtnNew
                    "like new" -> R.id.radBtnLikeNew
                    else -> R.id.radBtnUsed
                }
            )

            refreshMediaUi()
            renderPickedLocation()
        }
    }

    private fun submitListing() {
        val input = currentInput()
        val errors = PostListingValidator.validate(input)
        if (errors.isNotEmpty()) {
            validationAttempted = true
            renderInlineErrors(errors)
            focusFirstInvalid(errors)
            return
        }

        val customLocationSelected = PickupLocationSelection.isCustom(
            listingLocation.selectedItemPosition,
            customLocationIndex
        )
        if (customLocationSelected && !hasPickedLocation()) {
            Toast.makeText(this, R.string.post_location_pick_required, Toast.LENGTH_LONG).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, R.string.post_sign_in_required, Toast.LENGTH_LONG).show()
            return
        }

        val name = input.title.trim()
        val category = input.category
        val price = PostListingValidator.parsePrice(input.priceText) ?: 0.0
        val desc = input.description.trim()
        val predefinedLocation = listingLocation.selectedItem?.toString().orEmpty()
        val location = pickedLabel?.takeIf { it.isNotBlank() }
            ?: if (customLocationSelected) getString(R.string.post_location_pinned) else predefinedLocation
        val condition = input.condition
        val latitude = pickedLat ?: 0.0
        val longitude = pickedLng ?: 0.0

        postListingButton.isEnabled = false
        postListingButton.setText(R.string.post_uploading)

        ListingRepo.getUserProfile(uid) { profile ->
            val displayName = profile?.displayName ?: "Student"

            lifecycleScope.launch { //coroutine to handle media and listing upload
                try {
                    val uploadedUrls = imagesAndVideos.map { uri ->
                        async { uploadMediaFile(uri) }
                    }.awaitAll()
                    val allImages = existingImageUrls + uploadedUrls

                    val original = editingListing
                    if (original != null) {
                        ListingRepo.updateListing(
                            original.copy(
                                title = name,
                                category = category,
                                price = price,
                                description = desc,
                                pickupLocation = location,
                                condition = condition,
                                imageUrls = allImages,
                                latitude = latitude,
                                longitude = longitude,
                                // A price edit carries the price it dropped from, so cards and
                                // the detail sheet can show the saving. A raised price keeps
                                // the old number too, which produces no badge.
                                previousPrice = if (price != original.price) {
                                    original.price
                                } else {
                                    original.previousPrice
                                }
                            )
                        )
                        Toast.makeText(this@PostListing, R.string.post_updated, Toast.LENGTH_SHORT).show()
                    } else {
                        val newListingId = ListingRepo.createListing(
                            Listing(
                                sellerId = uid,
                                sellerName = displayName,
                                title = name,
                                category = category,
                                price = price,
                                description = desc,
                                pickupLocation = location,
                                condition = condition,
                                imageUrls = allImages,
                                latitude = latitude,
                                longitude = longitude
                            )
                        )
                        Log.d("PostListing", "Listing written to Firestore: $newListingId")
                        Toast.makeText(this@PostListing, R.string.post_success, Toast.LENGTH_SHORT).show()
                    }

                    setResult(RESULT_OK)
                    finish()
                } catch (e: Exception) {
                    Log.e("PostListing", "Failed to complete listing submission", e)
                    Toast.makeText(
                        this@PostListing,
                        getString(R.string.post_upload_error, e.message.orEmpty()),
                        Toast.LENGTH_LONG
                    ).show()
                    postListingButton.isEnabled = true
                    postListingButton.setText(
                        if (editingListing != null) R.string.post_save_changes else R.string.post_submit
                    )
                }
            }
        }
    }

    private suspend fun uploadMediaFile(uri: Uri): String {
        val fileId = UUID.randomUUID().toString() //generate unique id to avoid filename conflicts
        val path = "listings/$fileId.jpg" //path where media will be uploaded
        val ref = storage.reference.child(path)

        ref.putFile(uri).await() //upload file to database
        return ref.downloadUrl.await().toString() //return download url of file
    }

    /** Snapshot of the form for the pure validator. */
    private fun currentInput() = PostListingValidator.Input(
        hasMedia = mediaCount() > 0,
        title = itmName.text.toString(),
        priceText = itmPrice.text.toString(),
        description = itmDesc.text.toString(),
        category = itmCategory.selectedItem?.toString().orEmpty(),
        condition = selectedCondition()
    )

    private fun selectedCondition(): String {
        val checkedId = itmConditions.checkedRadioButtonId
        return if (checkedId == -1) "" else findViewById<RadioButton>(checkedId).text.toString()
    }

    /** Shows the inline error under every invalid field, or hides it once the field is fixed. */
    private fun renderInlineErrors(
        errors: Map<PostListingValidator.Field, PostListingValidator.Error>
    ) {
        renderInlineError(
            postErrorMedia,
            errors[PostListingValidator.Field.MEDIA],
            R.string.post_error_media
        )
        renderInlineError(
            postErrorTitle,
            errors[PostListingValidator.Field.TITLE],
            R.string.post_error_title
        )
        renderInlineError(
            postErrorPrice,
            errors[PostListingValidator.Field.PRICE],
            R.string.post_error_price
        )
        renderInlineError(
            postErrorDescription,
            errors[PostListingValidator.Field.DESCRIPTION],
            R.string.post_error_description
        )
    }

    private fun renderInlineError(
        view: TextView,
        error: PostListingValidator.Error?,
        messageRes: Int
    ) {
        if (error == null) {
            view.isVisible = false
        } else {
            view.setText(messageRes)
            view.isVisible = true
        }
    }

    /** Focuses the topmost invalid field and scrolls it into view. */
    private fun focusFirstInvalid(
        errors: Map<PostListingValidator.Field, PostListingValidator.Error>
    ) {
        val field = PostListingValidator.Field.entries.firstOrNull { it in errors } ?: return
        val target: View = when (field) {
            PostListingValidator.Field.MEDIA -> findViewById<View>(R.id.btnAddImage)
            PostListingValidator.Field.TITLE -> itmName
            PostListingValidator.Field.CATEGORY -> itmCategory
            PostListingValidator.Field.PRICE -> itmPrice
            PostListingValidator.Field.CONDITION -> itmConditions
            PostListingValidator.Field.DESCRIPTION -> itmDesc
        }
        if (target is EditText) target.requestFocus()
        scrollIntoView(target)
    }

    private fun scrollIntoView(view: View) {
        val rect = Rect()
        view.getDrawingRect(rect)
        formScrollView.offsetDescendantRectToMyCoords(view, rect)
        val top = formScrollView.scrollY + rect.top - dp(16)
        formScrollView.smoothScrollTo(0, top.coerceAtLeast(0))
    }

    /** Re-runs validation after a keystroke so a fixed field loses its inline error. */
    private fun watchForFixes(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (validationAttempted) {
                    renderInlineErrors(PostListingValidator.validate(currentInput()))
                }
            }
        })
    }

    private fun mediaCount() = existingImageUrls.size + imagesAndVideos.size

    private fun updateMediaSubtitle() {
        findViewById<TextView>(R.id.btnText2)?.text =
            getString(R.string.post_media_count, mediaCount())
    }

    private fun refreshMediaUi() {
        updateMediaSubtitle()
        renderMediaPreviews()
        refreshMediaError()
    }

    /** Keeps the media error in sync when media is picked or removed after a failed submit. */
    private fun refreshMediaError() {
        if (!validationAttempted) return
        val error = if (mediaCount() == 0) PostListingValidator.Error.MEDIA_REQUIRED else null
        renderInlineError(postErrorMedia, error, R.string.post_error_media)
    }

    /** Thumbnail strip so the seller can review everything that will be uploaded. */
    private fun renderMediaPreviews() {
		//get number of media files
        val count = mediaCount()
        val hasMedia = count > 0
		
		//show label and scroll if there is media to show
        mediaPreviewLabel.isVisible = hasMedia
        mediaPreviewScroll.isVisible = hasMedia
        mediaPreviewRow.removeAllViews()
        if (!hasMedia) return //return if no media to show

        mediaPreviewLabel.text = getString(R.string.post_selected_media, count)
        existingImageUrls.toList().forEach { url ->
            mediaPreviewRow.addView(mediaPreview(url, looksLikeVideo(url)) {
                existingImageUrls.remove(url)
                refreshMediaUi()
            })
        }
        imagesAndVideos.toList().forEach { uri -> //display newly selected media files that havent been uploaded
            mediaPreviewRow.addView(mediaPreview(uri, isVideoUri(uri)) {
                imagesAndVideos.remove(uri)
                refreshMediaUi()
            })
        }
    }

    private fun mediaPreview(model: Any, isVideo: Boolean, onRemove: () -> Unit): View {
        val size = dp(84) //size of media cards
        val card = CardView(this).apply { //create a rounded cardview
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@PostListing, R.color.nox_field_bg))
        }
        val frame = FrameLayout(this) //frame for media preview
        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.post_selected_media_item)
        }
        image.load(model) { //load image into imageview
            crossfade(true)
            placeholder(R.color.nox_field_bg)
            error(R.color.nox_field_bg)
        }
        frame.addView(image)

        if (isVideo) {
            frame.addView(ImageView(this).apply { //add a play button if a video
                setImageResource(android.R.drawable.ic_media_play)
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                setColorFilter(ContextCompat.getColor(this@PostListing, R.color.white))
            })
        }

        frame.addView(ImageButton(this).apply { //add a close button
            setImageResource(R.drawable.ic_close)
            background = ContextCompat.getDrawable(this@PostListing, R.drawable.media_remove_bg)
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END)
            minimumWidth = 0
            minimumHeight = 0
            contentDescription = getString(R.string.post_remove_media)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setColorFilter(ContextCompat.getColor(this@PostListing, R.color.white))
            setOnClickListener { onRemove() }
        })
        card.addView(frame)
        return card
    }

    private fun isVideoUri(uri: Uri): Boolean =
        contentResolver.getType(uri)?.startsWith("video/") == true

    private fun looksLikeVideo(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.getDefault())
        return listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".3gp").any { path.endsWith(it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun renderPickedLocation() {
        val label = findViewById<TextView>(R.id.tvPickedLocation)
        val lat = pickedLat
        val lng = pickedLng
        if (pickedLabel.isNullOrBlank() && lat == null) {
            label.isVisible = false
            label.text = ""
        } else {
            val text = pickedLabel?.takeIf { it.isNotBlank() }
                ?: getString(R.string.post_location_pinned)
            label.isVisible = true
            label.text = if (lat != null && lng != null) {
                getString(R.string.post_picked_location_coords, text, lat, lng)
            } else {
                getString(R.string.post_picked_location, text)
            }
        }
    }

    private fun hasPickedLocation(): Boolean =
        !pickedLabel.isNullOrBlank() || (pickedLat != null && pickedLng != null)

    /** Custom entry chosen from the dropdown: pin now, or open the picker when nothing is pinned. */
    private fun onCustomLocationSelected() {
        if (hasPickedLocation()) {
            renderPickedLocation()
            return
        }
        pendingCustomPickRevertIndex = previousBuildingIndex
        launchMapPicker()
    }

    private fun selectCustomLocationEntry() {
        suppressLocationClear = true
        listingLocation.setSelection(customLocationIndex)
        suppressLocationClear = false
    }

    /** Restores the last building when a custom pick started from the dropdown is cancelled. */
    private fun revertPendingCustomPick() {
        val revertTo = pendingCustomPickRevertIndex ?: return
        pendingCustomPickRevertIndex = null
        suppressLocationClear = true
        listingLocation.setSelection(revertTo)
        suppressLocationClear = false
    }

    private fun clearPickedLocation() {
        if (pickedLat == null && pickedLng == null && pickedLabel == null) return
        pickedLat = null
        pickedLng = null
        pickedLabel = null
        renderPickedLocation()
    }

    private fun formatPrice(price: Double): String =
        if (price % 1.0 == 0.0) price.toInt().toString() else price.toString()

    /** Scrolls the focused field above the part of the keyboard that covers [coveredHeight] px. */
    private fun ScrollView.scrollFocusedChildIntoView(coveredHeight: Int) {
        val focused = findFocus() ?: return
        if (focused === this) return
        val rect = Rect()
        focused.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(focused, rect)
        val margin = (120 * resources.displayMetrics.density).toInt()
        val overflow = rect.bottom - (height - coveredHeight - margin)
        if (overflow > 0) smoothScrollBy(0, overflow)
    }

    companion object {
        const val EXTRA_LISTING_ID = "listingId"
    }
}
