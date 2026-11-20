package com.example.noxapp.postlisting

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.R
import com.example.noxapp.core.NavTab
import com.example.noxapp.core.applyEdgeToEdgeInsets
import com.example.noxapp.core.dp
import com.example.noxapp.core.wireTabBar
import com.example.noxapp.listing.CampusLocations
import com.example.noxapp.listing.CategoryCatalog
import com.example.noxapp.listing.Listing
import com.example.noxapp.listing.ListingRepo
import com.example.noxapp.listing.MediaHelper
import com.example.noxapp.listing.formatPrice
import com.example.noxapp.map.MapActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale
import java.util.UUID

class PostListing : AppCompatActivity() {

    private val pickedMedia: MutableList<Uri> = mutableListOf()
    private val existingMediaUrls: MutableList<String> = mutableListOf()
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
    private val maxVideoSeconds = 5

    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult

            val availableSlots = maxMedia - mediaCount()
            if (availableSlots <= 0) {
                Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val validUris = uris.filter { uri ->
                if (isVideoUri(uri) && !isVideoWithinLimit(uri)) {
                    Toast.makeText(this, "Videos must be $maxVideoSeconds seconds or less", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            }.take(availableSlots)

            if (validUris.isNotEmpty()) {
                pickedMedia.addAll(validUris)
                refreshMediaUi()
                Toast.makeText(
                    this,
                    getString(R.string.post_media_added, validUris.size, mediaCount()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            onCameraMediaCaptured(success)
        }

    private val takeVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onCameraMediaCaptured(result.resultCode == RESULT_OK)
        }

    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null ||
                !data.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false)
            ) {
                revertPendingCustomPick()
                return@registerForActivityResult
            }

            pickedLat = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0)
            pickedLng = data.getDoubleExtra(MapActivity.EXTRA_CENTER_LNG, 0.0)
            pickedLabel = data.getStringExtra(MapActivity.EXTRA_LOCATION_LABEL)
                ?: getString(R.string.post_location_pinned)
            pendingCustomPickRevertIndex = null
            selectCustomLocationEntry()
            renderPickedLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_post_listing)
        applyEdgeToEdgeInsets(R.id.rootContainer, R.id.bottomNavBar)

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

        restoreDraft(savedInstanceState)

        intent.getStringExtra(EXTRA_LISTING_ID)?.takeIf { it.isNotBlank() }
            ?.let { loadListingForEdit(it, restoreForm = savedInstanceState == null) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_PICKED_IMAGES, ArrayList(pickedMedia.map { it.toString() }))
        outState.putStringArrayList(STATE_EXISTING_IMAGES, ArrayList(existingMediaUrls))
        cameraTempUri?.let { outState.putString(STATE_CAMERA_URI, it.toString()) }
        pickedLat?.let { outState.putDouble(STATE_PICK_LAT, it) }
        pickedLng?.let { outState.putDouble(STATE_PICK_LNG, it) }
        outState.putString(STATE_PICK_LABEL, pickedLabel)
        outState.putInt(STATE_PREVIOUS_BUILDING, previousBuildingIndex)
        pendingCustomPickRevertIndex?.let { outState.putInt(STATE_PENDING_REVERT, it) }
        outState.putBoolean(STATE_VALIDATION_ATTEMPTED, validationAttempted)
        super.onSaveInstanceState(outState)
    }

    private fun restoreDraft(state: Bundle?) {
        if (state == null) return
        state.getStringArrayList(STATE_PICKED_IMAGES)?.forEach { pickedMedia.add(Uri.parse(it)) }
        state.getStringArrayList(STATE_EXISTING_IMAGES)?.let { existingMediaUrls.addAll(it) }
        cameraTempUri = state.getString(STATE_CAMERA_URI)?.let { Uri.parse(it) }
        if (state.containsKey(STATE_PICK_LAT)) pickedLat = state.getDouble(STATE_PICK_LAT)
        if (state.containsKey(STATE_PICK_LNG)) pickedLng = state.getDouble(STATE_PICK_LNG)
        pickedLabel = state.getString(STATE_PICK_LABEL)
        previousBuildingIndex = state.getInt(STATE_PREVIOUS_BUILDING, 0)
        if (state.containsKey(STATE_PENDING_REVERT)) {
            pendingCustomPickRevertIndex = state.getInt(STATE_PENDING_REVERT)
        }
        validationAttempted = state.getBoolean(STATE_VALIDATION_ATTEMPTED, false)
        refreshMediaUi()
        renderPickedLocation()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (validationAttempted) {
            renderInlineErrors(PostListingValidator.validate(currentInput()))
        }
    }

    private fun setupSpinners() {
        val categoryAdapter = ArrayAdapter(this, R.layout.item_spinner, CategoryCatalog.NAMES)
        categoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        itmCategory.adapter = categoryAdapter

        locationOptions.clear()
        locationOptions.addAll(resources.getStringArray(R.array.locations))
        customLocationIndex = locationOptions.size
        locationOptions.add(getString(R.string.post_location_custom_option))

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
                    clearPickedLocation()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupMediaPicker() {
        findViewById<View>(R.id.btnAddImage).setOnClickListener {
            if (mediaCount() >= maxMedia) {
                Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
            } else {
                showMediaSourceDialog()
            }
        }
    }

    private fun setupMapPicker() {
        findViewById<MaterialButton>(R.id.btnPickOnMap).setOnClickListener { launchMapPicker() }
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        val scroll = findViewById<ScrollView>(R.id.formScrollView)
        val tabBar = findViewById<View>(R.id.bottomNavBar)
        val base = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val covered =
                (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - tabBar.height)
                    .coerceAtLeast(0)
            view.updatePadding(bottom = base + covered)
            insets
        }
    }

    private fun setupBottomNavigation() {
        wireTabBar(R.id.bottomNavBar, NavTab.POST, onReselect = {
            findViewById<ScrollView>(R.id.formScrollView)?.smoothScrollTo(0, 0)
        })
    }

    private fun showMediaSourceDialog() {
        val options = arrayOf("Take Photo", "Record Video (Max ${maxVideoSeconds}s)", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Add Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera(isVideo = false)
                    1 -> launchCamera(isVideo = true)
                    2 -> pickMultipleMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            }
            .show()
    }

    private fun launchCamera(isVideo: Boolean) {
        val directory = externalCacheDir ?: cacheDir
        val ext = if (isVideo) ".mp4" else ".jpg"
        val tempFile = File.createTempFile("captured_media_", ext, directory)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)

        cameraTempUri = uri
        runCatching {
            if (isVideo) {
                val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxVideoSeconds)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                takeVideoLauncher.launch(videoIntent)
            } else {
                takePhotoLauncher.launch(uri)
            }
        }.onFailure {
            Log.e("PostListing", "No camera app available", it)
            Toast.makeText(this, R.string.post_camera_unavailable, Toast.LENGTH_LONG).show()
        }
        val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxVideoSeconds)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0) // Low/standard quality = tiny file size & fast buffering
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun onCameraMediaCaptured(success: Boolean) {
        val uri = cameraTempUri ?: return
        if (!success) return

        if (mediaCount() >= maxMedia) {
            Toast.makeText(this, R.string.post_max_media, Toast.LENGTH_SHORT).show()
            return
        }

        if (isVideoUri(uri) && !isVideoWithinLimit(uri)) {
            Toast.makeText(this, "Video must be $maxVideoSeconds seconds or less", Toast.LENGTH_SHORT).show()
            return
        }

        pickedMedia.add(uri)
        refreshMediaUi()
        Toast.makeText(this, getString(R.string.post_photo_added, mediaCount()), Toast.LENGTH_SHORT).show()
    }

    private fun loadListingForEdit(listingId: String, restoreForm: Boolean) {
        ListingRepo.getListing(listingId) { listing ->
            if (isDestroyed) return@getListing
            if (listing == null) {
                Toast.makeText(this, R.string.post_edit_missing, Toast.LENGTH_LONG).show()
                finish()
                return@getListing
            }

            editingListing = listing
            findViewById<TextView>(R.id.headerTitle).setText(R.string.post_edit_title)
            postListingButton.setText(R.string.post_save_changes)

            if (!restoreForm) return@getListing

            existingMediaUrls.clear()
            existingMediaUrls.addAll(listing.imageUrls)

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

            lifecycleScope.launch {
                try {
                    val uploadedUrls = coroutineScope {
                        pickedMedia.map { uri ->
                            async { uploadMediaFile(uri) }
                        }.awaitAll()
                    }
                    val allMedia = existingMediaUrls + uploadedUrls

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
                                imageUrls = allMedia,
                                latitude = latitude,
                                longitude = longitude,
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
                                imageUrls = allMedia,
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
        val fileId = UUID.randomUUID().toString()
        val isVideo = isVideoUri(uri)

        if (isVideo) {
            val thumbBitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame
            }.getOrNull()

            if (thumbBitmap != null) {
                val baos = java.io.ByteArrayOutputStream()
                thumbBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                storage.reference.child("listings/${fileId}_thumb.jpg")
                    .putBytes(baos.toByteArray())
                    .await()
            }
        }

        val ext = if (isVideo) "mp4" else "jpg"
        val ref = storage.reference.child("listings/$fileId.$ext")
        val stream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open selected file")
        stream.use { input ->
            ref.putStream(input).await()
        }
        return ref.downloadUrl.await().toString()
    }

    private fun isVideoUri(uri: Uri): Boolean =
        contentResolver.getType(uri)?.startsWith("video/") == true ||
                uri.toString().endsWith(".mp4", ignoreCase = true)

    private fun isVideoSizeAcceptable(uri: Uri, maxMb: Int = 8): Boolean {
        val sizeBytes = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        return sizeBytes in 1..(maxMb * 1024L * 1024L)
    }

    private fun isVideoWithinLimit(uri: Uri): Boolean = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        // Allow a 500ms buffer for camera encoder keyframe rounding
        durationMs <= (maxVideoSeconds * 1000L) + 500L
    }.getOrDefault(false)

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

    private fun renderInlineErrors(
        errors: Map<PostListingValidator.Field, PostListingValidator.Error>
    ) {
        renderInlineError(postErrorMedia, errors[PostListingValidator.Field.MEDIA], R.string.post_error_media)
        renderInlineError(postErrorTitle, errors[PostListingValidator.Field.TITLE], R.string.post_error_title)
        renderInlineError(postErrorPrice, errors[PostListingValidator.Field.PRICE], R.string.post_error_price)
        renderInlineError(postErrorDescription, errors[PostListingValidator.Field.DESCRIPTION], R.string.post_error_description)
    }

    private fun renderInlineError(view: TextView, error: PostListingValidator.Error?, messageRes: Int) {
        if (error == null) {
            view.isVisible = false
        } else {
            view.setText(messageRes)
            view.isVisible = true
        }
    }

    private fun focusFirstInvalid(errors: Map<PostListingValidator.Field, PostListingValidator.Error>) {
        val field = PostListingValidator.Field.entries.firstOrNull { it in errors } ?: return
        val target: View = when (field) {
            PostListingValidator.Field.MEDIA -> findViewById(R.id.btnAddImage)
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

    private fun mediaCount() = existingMediaUrls.size + pickedMedia.size

    private fun updateMediaSubtitle() {
        findViewById<TextView>(R.id.btnText2)?.text =
            getString(R.string.post_media_count, mediaCount())
    }

    private fun refreshMediaUi() {
        updateMediaSubtitle()
        renderMediaPreviews()
        refreshMediaError()
    }

    private fun refreshMediaError() {
        if (!validationAttempted) return
        val error = if (mediaCount() == 0) PostListingValidator.Error.MEDIA_REQUIRED else null
        renderInlineError(postErrorMedia, error, R.string.post_error_media)
    }

    private fun renderMediaPreviews() {
        val count = mediaCount()
        val hasMedia = count > 0

        mediaPreviewLabel.isVisible = hasMedia
        mediaPreviewScroll.isVisible = hasMedia
        mediaPreviewRow.removeAllViews()
        if (!hasMedia) return

        mediaPreviewLabel.text = getString(R.string.post_selected_media, count)
        existingMediaUrls.toList().forEach { url ->
            mediaPreviewRow.addView(mediaPreview(url, looksLikeVideo(url)) {
                existingMediaUrls.remove(url)
                refreshMediaUi()
            })
        }
        pickedMedia.toList().forEach { uri ->
            mediaPreviewRow.addView(mediaPreview(uri, isVideoUri(uri)) {
                pickedMedia.remove(uri)
                refreshMediaUi()
            })
        }
    }

    private fun mediaPreview(model: Any, isVideo: Boolean, onRemove: () -> Unit): View {
        val size = dp(84)
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
            radius = dp(12).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            stateListAnimator = null
            setCardBackgroundColor(ContextCompat.getColor(this@PostListing, R.color.nox_field_bg))
        }
        val frame = FrameLayout(this)
        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.post_selected_media_item)
        }

        MediaHelper.loadThumbnail(image, model)
        if (isVideo) {
            card.setOnClickListener { MediaHelper.playVideo(this, model) }
        }
        frame.addView(image)

        if (isVideo) {
            frame.addView(ImageView(this).apply {
                setImageResource(android.R.drawable.ic_media_play)
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                setColorFilter(ContextCompat.getColor(this@PostListing, R.color.white))
            })
        }

        frame.addView(ImageButton(this).apply {
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

    private fun looksLikeVideo(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.getDefault())
        return listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".3gp").any { path.endsWith(it) }
    }

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

    companion object {
        const val EXTRA_LISTING_ID = "listingId"

        private const val STATE_PICKED_IMAGES = "post.pickedImages"
        private const val STATE_EXISTING_IMAGES = "post.existingImages"
        private const val STATE_CAMERA_URI = "post.cameraUri"
        private const val STATE_PICK_LAT = "post.pickLat"
        private const val STATE_PICK_LNG = "post.pickLng"
        private const val STATE_PICK_LABEL = "post.pickLabel"
        private const val STATE_PREVIOUS_BUILDING = "post.previousBuilding"
        private const val STATE_PENDING_REVERT = "post.pendingRevert"
        private const val STATE_VALIDATION_ATTEMPTED = "post.validationAttempted"
    }
}