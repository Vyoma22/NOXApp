package com.example.noxapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.noxapp.adapters.ListingAdapter
import com.example.noxapp.data.CampusLocations
import com.example.noxapp.data.ListingFilters
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.ui.ItemDetailDialog
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import java.text.NumberFormat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private val markers = mutableListOf<Marker>()
    private var listings = emptyList<Listing>()
    private var savedIds = mutableSetOf<String>()
    private lateinit var adapter: ListingAdapter
    private lateinit var segments: MaterialButtonToggleGroup
    private var center = LatLng(CampusLocations.ONTARIO_TECH.first, CampusLocations.ONTARIO_TECH.second)
    private var radiusKm = 2.0
    private var hasSelection = false
    private var preferenceMode = false
    private var usesMyLocation = false
    private var locationPending = false
    private var locationCancellation: CancellationTokenSource? = null
    private var restoredCamera = false
    private var saveInProgress = false
    private var navigation: String? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (locationPending) {
            if (hasLocationPermission()) locateDevice() else campusFallback(R.string.location_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)
        preferenceMode = intent.getStringExtra(EXTRA_MODE) == MODE_PREFERENCE
        arrangeLayout()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val state = savedInstanceState ?: intent.extras
        center = LatLng(
            state?.getDouble(EXTRA_CENTER_LAT, center.latitude) ?: center.latitude,
            state?.getDouble(EXTRA_CENTER_LNG, center.longitude) ?: center.longitude
        )
        radiusKm = state?.getDouble(EXTRA_RADIUS_KM, 2.0) ?: 2.0
        hasSelection = savedInstanceState?.getBoolean(EXTRA_HAS_SELECTION) ?: false
        usesMyLocation = savedInstanceState?.getBoolean("usesMyLocation") ?: false
        locationPending = savedInstanceState?.getBoolean("locationPending") ?: false
        restoredCamera = savedInstanceState != null

        if (!preferenceMode) setupItems()
        setupControls()
        setupNavigation()
        refreshArea()
        (supportFragmentManager.findFragmentById(R.id.campusMap) as SupportMapFragment).getMapAsync(this)
        if (!preferenceMode) loadListings()
        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    private fun arrangeLayout() {
        findViewById<View>(R.id.mapItemsPanel).isVisible = !preferenceMode
        findViewById<View>(R.id.searchAreaButton).isVisible = !preferenceMode
        findViewById<View>(R.id.savePreferenceButton).isVisible = preferenceMode
        val root = findViewById<ConstraintLayout>(R.id.mapRoot)
        val margin = (12 * resources.displayMetrics.density).toInt()
        ConstraintSet().apply {
            clone(root)
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                create(R.id.mapGuideline, ConstraintSet.VERTICAL)
                setGuidelinePercent(R.id.mapGuideline, 0.52f)
                connect(R.id.campusMap, ConstraintSet.END, R.id.mapGuideline, ConstraintSet.START)
                connect(R.id.campusMap, ConstraintSet.BOTTOM, R.id.mapBottomNav, ConstraintSet.TOP)
                connect(R.id.mapSegments, ConstraintSet.END, R.id.mapGuideline, ConstraintSet.START, margin)
                connect(R.id.mapItemsScroll, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                connect(R.id.mapItemsScroll, ConstraintSet.START, R.id.mapGuideline, ConstraintSet.END)
            } else if (preferenceMode) {
                clear(R.id.mapItemsScroll, ConstraintSet.TOP)
                constrainHeight(R.id.mapItemsScroll, ConstraintSet.WRAP_CONTENT)
                connect(R.id.campusMap, ConstraintSet.BOTTOM, R.id.mapItemsScroll, ConstraintSet.TOP)
            }
            applyTo(root)
        }
    }

    private fun setupItems() {
        adapter = ListingAdapter(emptyList(), savedIds, { listing, shouldSave ->
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                ListingRepo.toggleSaveItem(uid, listing.id, shouldSave) {
                    if (!isDestroyed) {
                        if (shouldSave) savedIds.add(listing.id) else savedIds.remove(listing.id)
                        adapter.updateSavedIds(savedIds)
                    }
                }
            }
        }) { ItemDetailDialog.show(this, it) }
        findViewById<RecyclerView>(R.id.mapItemsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MapActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@MapActivity.adapter
        }
    }

    private fun setupControls() {
        segments = findViewById(R.id.mapSegments)
        segments.check(if (usesMyLocation) R.id.myLocationButton else R.id.mapModeButton)
        segments.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                usesMyLocation = checkedId == R.id.myLocationButton
                if (usesMyLocation) {
                    locationPending = true
                    if (hasLocationPermission()) locateDevice() else locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                } else {
                    cancelLocation()
                    locationPending = false
                }
            }
        }
        findViewById<Slider>(R.id.radiusSlider).apply {
            value = radiusKm.toFloat()
            setLabelFormatter { getString(R.string.radius_label, formatRadius(it.toDouble())) }
            addOnChangeListener { _, value, fromUser ->
                radiusKm = value.toDouble()
                if (fromUser) hasSelection = true
                refreshArea()
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit
                override fun onStopTrackingTouch(slider: Slider) { fitCircle() }
            })
        }
        findViewById<View>(R.id.searchAreaButton).apply {
            isEnabled = false
            setOnClickListener {
                map?.let { readyMap ->
                    segments.check(R.id.mapModeButton)
                    center = readyMap.cameraPosition.target
                    hasSelection = true
                    refreshArea()
                    fitCircle()
                }
            }
        }
        findViewById<View>(R.id.mapAreaAction).setOnClickListener {
            hasSelection = true
            finish()
        }
        findViewById<View>(R.id.savePreferenceButton).apply {
            isEnabled = false
            setOnClickListener { savePreference() }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (isDestroyed || isFinishing) return
        map = googleMap
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        val green = ContextCompat.getColor(this, R.color.nox_primary_green)
        circle = googleMap.addCircle(CircleOptions().center(center).radius(radiusKm * 1000)
            .fillColor(ColorUtils.setAlphaComponent(green, 46)).strokeColor(green)
            .strokeWidth(resources.displayMetrics.density))
        googleMap.setOnMarkerClickListener { marker ->
            (marker.tag as? Listing)?.let { ItemDetailDialog.show(this, it) }
            true
        }
        findViewById<View>(R.id.searchAreaButton).isEnabled = true
        findViewById<View>(R.id.savePreferenceButton).isEnabled = true
        if (preferenceMode) {
            googleMap.setOnCameraMoveListener {
                center = googleMap.cameraPosition.target
                circle?.center = center
            }
        }
        rebuildMarkers()
        refreshArea()
        if (!restoredCamera) findViewById<View>(R.id.campusMap).post { fitCircle(false) }
    }

    private fun loadListings() {
        ListingRepo.loadAllListings(
            onSuccess = {
                if (!isDestroyed) {
                    listings = it
                    rebuildMarkers()
                    refreshArea()
                }
            },
            onError = {
                if (!isDestroyed) {
                    listings = emptyList()
                    rebuildMarkers()
                    refreshArea()
                    Snackbar.make(findViewById(R.id.mapRoot), R.string.map_listings_error, Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.mapBottomNav)
                        .setAction(R.string.map_retry) { loadListings() }.show()
                }
            }
        )
    }

    private fun rebuildMarkers() {
        if (preferenceMode) return
        val readyMap = map ?: return
        markers.forEach { it.remove() }
        markers.clear()
        val drawable = requireNotNull(ContextCompat.getDrawable(this, R.drawable.ic_map_marker))
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        listings.forEach { listing ->
            val (lat, lng) = CampusLocations.resolve(listing)
            readyMap.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(listing.title)
                .icon(icon).anchor(0.5f, 0.5f))?.let { marker ->
                marker.tag = listing
                markers.add(marker)
            }
        }
    }

    private fun refreshArea() {
        val label = getString(R.string.radius_label, formatRadius(radiusKm))
        findViewById<TextView>(R.id.radiusLabel).text = label
        findViewById<Slider>(R.id.radiusSlider).contentDescription = label
        circle?.center = center
        circle?.radius = radiusKm * 1000
        if (preferenceMode) return
        val filters = BrowseFilters(centerLat = center.latitude, centerLng = center.longitude, radiusKm = radiusKm)
        val items = ListingFilters.apply(listings, filters)
        adapter.updateList(items)
        findViewById<RecyclerView>(R.id.mapItemsRecycler).isVisible = items.isNotEmpty()
        findViewById<TextView>(R.id.mapEmptyNotice).apply {
            text = getString(R.string.no_items_in_radius, formatRadius(radiusKm))
            isVisible = items.isEmpty()
        }
        markers.forEach { marker ->
            val listing = marker.tag as Listing
            marker.alpha = if (ListingFilters.matches(listing, filters)) 1f else 0.4f
        }
    }

    private fun fitCircle(animate: Boolean = true) {
        val readyMap = map ?: return
        val view = findViewById<View>(R.id.campusMap)
        if (view.width == 0 || view.height == 0 || isDestroyed) return
        val angularRadius = radiusKm / 6371.0
        val latitudeDelta = Math.toDegrees(angularRadius)
        val longitudeDelta = Math.toDegrees(asin(
            (sin(angularRadius) / cos(Math.toRadians(center.latitude))).coerceIn(-1.0, 1.0)
        ))
        val bounds = LatLngBounds.builder()
            .include(LatLng(center.latitude - latitudeDelta, center.longitude - longitudeDelta))
            .include(LatLng(center.latitude + latitudeDelta, center.longitude + longitudeDelta))
            .build()
        val padding = (48 * resources.displayMetrics.density).toInt()
            .coerceAtMost(minOf(view.width, view.height) / 4)
        val update = CameraUpdateFactory.newLatLngBounds(bounds, view.width, view.height, padding)
        if (animate) readyMap.animateCamera(update) else readyMap.moveCamera(update)
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun locateDevice() {
        if (!hasLocationPermission() || locationCancellation != null) return
        val cancellation = CancellationTokenSource()
        locationCancellation = cancellation
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (locationCancellation !== cancellation || isDestroyed || isFinishing) return@addOnSuccessListener
                locationCancellation = null
                if (location == null) campusFallback(R.string.location_unavailable) else {
                    locationPending = false
                    center = LatLng(location.latitude, location.longitude)
                    hasSelection = true
                    refreshArea()
                    fitCircle()
                }
            }
            .addOnFailureListener {
                if (locationCancellation === cancellation && !isDestroyed && !isFinishing) {
                    locationCancellation = null
                    campusFallback(R.string.location_unavailable)
                }
            }
    }

    private fun campusFallback(message: Int) {
        cancelLocation()
        locationPending = false
        usesMyLocation = false
        segments.check(R.id.mapModeButton)
        center = LatLng(CampusLocations.ONTARIO_TECH.first, CampusLocations.ONTARIO_TECH.second)
        refreshArea()
        fitCircle()
        Snackbar.make(findViewById(R.id.mapRoot), message, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.mapBottomNav).show()
    }

    private fun cancelLocation() {
        locationCancellation?.cancel()
        locationCancellation = null
    }

    private fun savePreference() {
        if (saveInProgress) return
        val readyMap = map ?: return
        readyMap.stopAnimation()
        center = readyMap.cameraPosition.target
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, R.string.map_preference_error, Toast.LENGTH_SHORT).show()
            return
        }
        saveInProgress = true
        findViewById<View>(R.id.savePreferenceButton).isEnabled = false
        ListingRepo.saveGeoPreference(uid, center.latitude, center.longitude, radiusKm) { success ->
            if (!isDestroyed && !isFinishing) {
                saveInProgress = false
                findViewById<View>(R.id.savePreferenceButton).isEnabled = true
                Toast.makeText(this, if (success) R.string.preference_saved else R.string.map_preference_error,
                    Toast.LENGTH_SHORT).show()
                if (success) finish()
            }
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.mapBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.mNavBrowse).setOnClickListener {
            if (preferenceMode) {
                startActivity(Intent(this, BrowseActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            finish()
        }
        findViewById<View>(R.id.mNavHome).setOnClickListener {
            if (preferenceMode) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } else {
                navigation = NAV_HOME
            }
            finish()
        }
        findViewById<View>(R.id.mNavProfile).setOnClickListener {
            if (!preferenceMode) navigation = NAV_PROFILE
            finish()
        }
        findViewById<View>(R.id.mNavPost).setOnClickListener {
            Toast.makeText(this, R.string.map_post_toast, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.mNavChat).setOnClickListener {
            Toast.makeText(this, R.string.map_chat_toast, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!preferenceMode) {
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                ListingRepo.getSavedItemIds(uid) {
                    if (!isDestroyed) {
                        savedIds = it.toMutableSet()
                        adapter.updateSavedIds(savedIds)
                    }
                }
            }
        }
        if (locationPending && hasLocationPermission()) locateDevice()
    }

    override fun onStop() {
        cancelLocation()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble(EXTRA_CENTER_LAT, center.latitude)
        outState.putDouble(EXTRA_CENTER_LNG, center.longitude)
        outState.putDouble(EXTRA_RADIUS_KM, radiusKm)
        outState.putBoolean(EXTRA_HAS_SELECTION, hasSelection)
        outState.putBoolean("usesMyLocation", usesMyLocation)
        outState.putBoolean("locationPending", locationPending)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (!preferenceMode) setResult(RESULT_OK, Intent()
            .putExtra(EXTRA_HAS_SELECTION, hasSelection)
            .putExtra(EXTRA_CENTER_LAT, center.latitude)
            .putExtra(EXTRA_CENTER_LNG, center.longitude)
            .putExtra(EXTRA_RADIUS_KM, radiusKm)
            .putExtra(EXTRA_NAVIGATION, navigation))
        super.finish()
    }

    private fun formatRadius(value: Double) = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 1
    }.format(value)

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_BROWSE = "browse"
        const val MODE_PREFERENCE = "preference"
        const val EXTRA_HAS_SELECTION = "hasSelection"
        const val EXTRA_CENTER_LAT = "centerLat"
        const val EXTRA_CENTER_LNG = "centerLng"
        const val EXTRA_RADIUS_KM = "radiusKm"
        const val EXTRA_NAVIGATION = "navigation"
        const val NAV_HOME = "home"
        const val NAV_PROFILE = "profile"
    }
}
