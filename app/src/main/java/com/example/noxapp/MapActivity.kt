package com.example.noxapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.data.CampusLocations
import com.example.noxapp.data.ListingFilters
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.data.LocationSearch
import com.example.noxapp.data.Prefs
import com.example.noxapp.models.BrowseFilters
import com.example.noxapp.models.Listing
import com.example.noxapp.models.PriceRange
import com.example.noxapp.ui.ItemDetailDialog
import com.example.noxapp.ui.applyEdgeToEdgeInsets
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private val markers = mutableListOf<Marker>()
    private var listings = emptyList<Listing>()
    private var baseFilters = BrowseFilters()
    private var listingsLoaded = false
    private var listingsError = false
    private var center = LatLng(CampusLocations.ONTARIO_TECH.first, CampusLocations.ONTARIO_TECH.second)
    private var radiusKm = 2.0
    private var hasSelection = false
    private var locationPending = false
    private var locationCancellation: CancellationTokenSource? = null
    private var restoredCamera = false
    private var navigation: String? = null
    private var searchJob: Job? = null
    private var searchResultsDialog: AlertDialog? = null

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
        arrangeLayout()
        applyEdgeToEdgeInsets(R.id.mapRoot, R.id.mapBottomNav)
        val state = savedInstanceState ?: intent.extras
        center = LatLng(
            state?.getDouble(EXTRA_CENTER_LAT, center.latitude) ?: center.latitude,
            state?.getDouble(EXTRA_CENTER_LNG, center.longitude) ?: center.longitude
        )
        radiusKm = state?.getDouble(EXTRA_RADIUS_KM, 2.0) ?: 2.0
        baseFilters = BrowseFilters(
            query = intent.getStringExtra(EXTRA_QUERY).orEmpty(),
            category = intent.getStringExtra(EXTRA_CATEGORY),
            priceRange = intent.getStringExtra(EXTRA_PRICE)?.let { name ->
                runCatching { PriceRange.valueOf(name) }.getOrNull()
            },
            condition = intent.getStringExtra(EXTRA_CONDITION)
        )
        hasSelection = savedInstanceState?.getBoolean(EXTRA_HAS_SELECTION) ?: false
        locationPending = savedInstanceState?.getBoolean("locationPending") ?: false
        restoredCamera = savedInstanceState != null

        setupControls()
        setupLocationSearch()
        setupNavigation()
        refreshArea()
        (supportFragmentManager.findFragmentById(R.id.campusMap) as SupportMapFragment).getMapAsync(this)
        loadListings()
    }

    private fun arrangeLayout() {
        val root = findViewById<ConstraintLayout>(R.id.mapRoot)
        val margin = (12 * resources.displayMetrics.density).toInt()
        ConstraintSet().apply {
            clone(root)
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                create(R.id.mapGuideline, ConstraintSet.VERTICAL)
                setGuidelinePercent(R.id.mapGuideline, 0.52f)
                connect(R.id.campusMap, ConstraintSet.END, R.id.mapGuideline, ConstraintSet.START)
                connect(R.id.campusMap, ConstraintSet.BOTTOM, R.id.mapBottomNav, ConstraintSet.TOP)
                connect(R.id.mapTopBar, ConstraintSet.END, R.id.mapGuideline, ConstraintSet.START, margin)
                connect(R.id.mapItemsScroll, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                connect(R.id.mapItemsScroll, ConstraintSet.START, R.id.mapGuideline, ConstraintSet.END)
            } else {
                clear(R.id.mapItemsScroll, ConstraintSet.TOP)
                constrainHeight(R.id.mapItemsScroll, ConstraintSet.WRAP_CONTENT)
                connect(R.id.campusMap, ConstraintSet.BOTTOM, R.id.mapItemsScroll, ConstraintSet.TOP)
            }
            applyTo(root)
        }
    }

    private fun setupControls() {
        findViewById<View>(R.id.myLocationButton).setOnClickListener {
            cancelSearch()
            locationPending = true
            if (hasLocationPermission()) locateDevice() else locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
        findViewById<Slider>(R.id.radiusSlider).apply {
            value = radiusKm.toFloat()
            setLabelFormatter {
                getString(R.string.radius_label, formatRadius(it.toDouble()), Prefs.distanceUnit(this@MapActivity))
            }
            addOnChangeListener { _, value, _ ->
                radiusKm = value.toDouble()
                refreshArea()
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit
                override fun onStopTrackingTouch(slider: Slider) { fitCircle() }
            })
        }
        findViewById<View>(R.id.searchAreaButton).apply {
            // The chosen centre and radius are already known, so the action can commit before
            // the map tiles finish loading. If the camera is ready, take its current target.
            setOnClickListener {
                map?.stopAnimation()
                center = map?.cameraPosition?.target ?: center
                hasSelection = true
                finish()
            }
        }
    }

    private fun setupLocationSearch() {
        findViewById<EditText>(R.id.locationSearchInput).apply {
            doAfterTextChanged {
                error = null
                cancelSearch()
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    searchLocation()
                    true
                } else false
            }
        }
        findViewById<View>(R.id.locationSearchButton).apply {
            isEnabled = false
            setOnClickListener { searchLocation() }
        }
    }

    private fun searchLocation() {
        val readyMap = map ?: return
        val input = findViewById<EditText>(R.id.locationSearchInput)
        val query = input.text.toString().trim()
        cancelSearch()
        if (query.isEmpty()) {
            input.error = getString(R.string.location_search_required)
            return
        }
        WindowCompat.getInsetsController(window, input).hide(WindowInsetsCompat.Type.ime())
        input.clearFocus()
        val status = findViewById<TextView>(R.id.locationSearchStatus)
        status.setText(R.string.location_search_loading)
        status.isVisible = true
        searchJob = lifecycleScope.launch {
            val addresses = try {
                withTimeoutOrNull(15_000) { LocationSearch.find(this@MapActivity, query) }
            } catch (_: IOException) {
                null
            }
            if (isFinishing || isDestroyed) return@launch
            if (addresses.isNullOrEmpty()) {
                status.setText(if (addresses == null) R.string.location_search_error else R.string.location_search_empty)
                return@launch
            }
            status.isVisible = false
            val labels = addresses.map { address ->
                address.getAddressLine(0) ?: listOfNotNull(
                    address.featureName, address.locality, address.adminArea, address.countryName
                ).distinct().joinToString(", ").ifEmpty { "${address.latitude}, ${address.longitude}" }
            }
            searchResultsDialog = MaterialAlertDialogBuilder(this@MapActivity)
                .setTitle(R.string.location_search_results)
                .setItems(labels.toTypedArray()) { _, index ->
                    val address = addresses[index]
                    readyMap.stopAnimation()
                    readyMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(address.latitude, address.longitude)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { searchResultsDialog = null }
                .show()
        }
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        searchResultsDialog?.dismiss()
        searchResultsDialog = null
        findViewById<View>(R.id.locationSearchStatus).isVisible = false
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (isDestroyed || isFinishing) return
        map = googleMap
        val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
            this,
            if (nightMode) R.raw.map_style_night else R.raw.map_style
        ))
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
        findViewById<View>(R.id.locationSearchButton).isEnabled = true
        googleMap.setOnCameraMoveListener {
            center = googleMap.cameraPosition.target
            refreshArea()
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
                    listingsLoaded = true
                    listingsError = false
                    rebuildMarkers()
                    refreshArea()
                }
            },
            onError = {
                if (!isDestroyed) {
                    listings = emptyList()
                    listingsLoaded = true
                    listingsError = true
                    rebuildMarkers()
                    refreshArea()
                    Snackbar.make(findViewById(R.id.mapRoot), R.string.browse_load_error, Snackbar.LENGTH_INDEFINITE)
                        .setAnchorView(R.id.mapBottomNav)
                        .setAction(R.string.browse_retry) { loadListings() }
                        .show()
                }
            }
        )
    }

    private fun rebuildMarkers() {
        val readyMap = map ?: return
        markers.forEach { it.remove() }
        markers.clear()
        val drawable = requireNotNull(ContextCompat.getDrawable(this, R.drawable.ic_map_marker))
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        // Pins follow the Browse filters too, so the map does not contradict the grid.
        listings.filter { ListingFilters.matches(it, baseFilters) }.forEach { listing ->
            val (lat, lng) = CampusLocations.resolve(listing)
            readyMap.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(listing.title)
                .icon(icon).anchor(0.5f, 0.5f))?.let { marker ->
                marker.tag = listing
                markers.add(marker)
            }
        }
    }

    private fun refreshArea() {
        val unit = Prefs.distanceUnit(this)
        val label = getString(R.string.radius_label, formatRadius(radiusKm), unit)
        findViewById<TextView>(R.id.radiusLabel).text = label
        findViewById<Slider>(R.id.radiusSlider).contentDescription = label
        findViewById<TextView>(R.id.radiusMinLabel).text = getString(R.string.map_radius_min, unit)
        findViewById<TextView>(R.id.radiusMaxLabel).text = getString(R.string.map_radius_max, unit)
        circle?.center = center
        circle?.radius = radiusKm * 1000
        val areaFilters = baseFilters.copy(
            centerLat = center.latitude,
            centerLng = center.longitude,
            radiusKm = radiusKm
        )
        markers.forEach { marker ->
            val listing = marker.tag as Listing
            marker.alpha = if (ListingFilters.matches(listing, areaFilters)) 1f else 0.4f
        }
        updateAreaSummary(unit)
        updateFilterSummary()
    }

    private fun updateAreaSummary(unit: String) {
        val summary = findViewById<TextView>(R.id.mapAreaSummary)
        summary.text = when {
            listingsError -> getString(R.string.map_area_error)
            !listingsLoaded -> getString(R.string.map_area_loading)
            else -> {
                val areaFilters = baseFilters.copy(
                    centerLat = center.latitude,
                    centerLng = center.longitude,
                    radiusKm = radiusKm
                )
                val count = listings.count { ListingFilters.matches(it, areaFilters) }
                resources.getQuantityString(
                    R.plurals.map_area_count, count, count, formatRadius(radiusKm), unit
                )
            }
        }
    }

    private fun updateFilterSummary() {
        val parts = mutableListOf<String>()
        baseFilters.category?.let { parts.add(it) }
        baseFilters.priceRange?.let { parts.add(it.label) }
        baseFilters.condition?.let { parts.add(it) }
        if (baseFilters.query.isNotBlank()) parts.add("\"${baseFilters.query}\"")
        val summary = findViewById<TextView>(R.id.mapFilterSummary)
        summary.isVisible = parts.isNotEmpty()
        if (parts.isNotEmpty()) {
            summary.text = getString(R.string.map_showing_filters, parts.joinToString(" · "))
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

    private fun setupNavigation() {
        findViewById<View>(R.id.mapBackButton).setOnClickListener { finish() }
        findViewById<BottomNavigationView>(R.id.mapBottomNav).apply {
            selectedItemId = R.id.navBrowse
            setOnItemSelectedListener { item ->
                post {
                    when (item.itemId) {
                        R.id.navBrowse -> finish()
                        R.id.navHome -> {
                            navigation = NAV_HOME
                            finish()
                        }
                        R.id.navProfile -> {
                            navigation = NAV_PROFILE
                            finish()
                        }
                        R.id.navPost -> startActivity(
                            Intent(
                                this@MapActivity,
                                com.example.noxapp.postlisting.PostListing::class.java
                            )
                        )
                        R.id.navChat -> Toast.makeText(
                            this@MapActivity, R.string.map_chat_toast, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                item.itemId == R.id.navBrowse
            }
            setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navBrowse) finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (locationPending && hasLocationPermission()) locateDevice()
    }

    override fun onStop() {
        cancelLocation()
        cancelSearch()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble(EXTRA_CENTER_LAT, center.latitude)
        outState.putDouble(EXTRA_CENTER_LNG, center.longitude)
        outState.putDouble(EXTRA_RADIUS_KM, radiusKm)
        outState.putBoolean(EXTRA_HAS_SELECTION, hasSelection)
        outState.putBoolean("locationPending", locationPending)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        setResult(RESULT_OK, Intent()
            .putExtra(EXTRA_HAS_SELECTION, hasSelection)
            .putExtra(EXTRA_CENTER_LAT, center.latitude)
            .putExtra(EXTRA_CENTER_LNG, center.longitude)
            .putExtra(EXTRA_RADIUS_KM, radiusKm)
            .putExtra(EXTRA_NAVIGATION, navigation))
        super.finish()
    }

    private fun formatRadius(value: Double) = Prefs.formatDistance(value)

    companion object {
        const val EXTRA_HAS_SELECTION = "hasSelection"
        const val EXTRA_CENTER_LAT = "centerLat"
        const val EXTRA_CENTER_LNG = "centerLng"
        const val EXTRA_RADIUS_KM = "radiusKm"
        const val EXTRA_NAVIGATION = "navigation"
        const val EXTRA_QUERY = "query"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_PRICE = "priceRange"
        const val EXTRA_CONDITION = "condition"
        const val NAV_HOME = "home"
        const val NAV_PROFILE = "profile"
    }
}
